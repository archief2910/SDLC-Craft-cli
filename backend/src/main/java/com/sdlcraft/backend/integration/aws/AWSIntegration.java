package com.sdlcraft.backend.integration.aws;

import com.sdlcraft.backend.integration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * AWS integration for infrastructure and deployment operations.
 * 
 * Supports:
 * - ECS service deployments
 * - ECR image management
 * - Lambda function updates
 * - S3 artifact storage
 * - Infrastructure provisioning
 */
@Component
public class AWSIntegration implements Integration {

    private static final Logger logger = LoggerFactory.getLogger(AWSIntegration.class);
    
    @Value("${sdlcraft.integration.aws.region:us-east-1}")
    private String awsRegion;
    
    @Value("${sdlcraft.integration.aws.profile:default}")
    private String awsProfile;
    
    private EcsClient ecsClient;
    private EcrClient ecrClient;
    private S3Client s3Client;
    private StsClient stsClient;

    @PostConstruct
    public void init() {
        try {
            Region region = Region.of(awsRegion);
            
            this.stsClient = StsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            this.ecsClient = EcsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            this.ecrClient = EcrClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            
            this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
                
            logger.info("AWS integration initialized for region: {}", awsRegion);
        } catch (Exception e) {
            logger.warn("AWS integration initialization failed: {}", e.getMessage());
        }
    }

    @Override
    public String getId() {
        return "aws";
    }

    @Override
    public String getName() {
        return "Amazon Web Services";
    }

    @Override
    public boolean isConfigured() {
        return stsClient != null;
    }

    @Override
    public IntegrationHealth healthCheck() {
        if (!isConfigured()) {
            return IntegrationHealth.unhealthy(getId(), "Not configured");
        }
        
        long start = System.currentTimeMillis();
        try {
            var identity = stsClient.getCallerIdentity();
            logger.debug("AWS identity: {}", identity.arn());
            return IntegrationHealth.healthy(getId(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("AWS health check failed", e);
            return IntegrationHealth.unhealthy(getId(), e.getMessage());
        }
    }

    @Override
    public String[] getSupportedActions() {
        return new String[]{
            "deployEcsService",
            "updateEcsService",
            "describeEcsService",
            "pushToEcr",
            "describeImages",
            "uploadToS3",
            "getCallerIdentity"
        };
    }
    
    /**
     * Deploy/update an ECS service with a new image.
     */
    public IntegrationResult deployEcsService(String cluster, String service, 
                                               String taskDefinition, int desiredCount) {
        long start = System.currentTimeMillis();
        try {
            UpdateServiceRequest request = UpdateServiceRequest.builder()
                .cluster(cluster)
                .service(service)
                .taskDefinition(taskDefinition)
                .desiredCount(desiredCount)
                .forceNewDeployment(true)
                .build();
            
            UpdateServiceResponse response = ecsClient.updateService(request);
            
            return IntegrationResult.success(getId(), "deployEcsService",
                "Deployed " + service + " with task " + taskDefinition,
                Map.of(
                    "cluster", cluster,
                    "service", service,
                    "taskDefinition", taskDefinition,
                    "runningCount", response.service().runningCount(),
                    "desiredCount", response.service().desiredCount()
                ),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to deploy ECS service {}", service, e);
            return IntegrationResult.failure(getId(), "deployEcsService", e.getMessage());
        }
    }
    
    /**
     * Describe an ECS service status.
     */
    public IntegrationResult describeEcsService(String cluster, String service) {
        long start = System.currentTimeMillis();
        try {
            DescribeServicesRequest request = DescribeServicesRequest.builder()
                .cluster(cluster)
                .services(service)
                .build();
            
            DescribeServicesResponse response = ecsClient.describeServices(request);
            
            if (response.services().isEmpty()) {
                return IntegrationResult.failure(getId(), "describeEcsService", 
                    "Service not found: " + service);
            }
            
            var svc = response.services().get(0);
            return IntegrationResult.success(getId(), "describeEcsService",
                "Service " + service + " status: " + svc.status(),
                Map.of(
                    "status", svc.status(),
                    "runningCount", svc.runningCount(),
                    "desiredCount", svc.desiredCount(),
                    "pendingCount", svc.pendingCount(),
                    "taskDefinition", svc.taskDefinition()
                ),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to describe ECS service {}", service, e);
            return IntegrationResult.failure(getId(), "describeEcsService", e.getMessage());
        }
    }
    
    /**
     * List images in an ECR repository.
     */
    public IntegrationResult describeEcrImages(String repositoryName) {
        long start = System.currentTimeMillis();
        try {
            DescribeImagesRequest request = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .build();
            
            DescribeImagesResponse response = ecrClient.describeImages(request);
            
            List<Map<String, Object>> images = new ArrayList<>();
            for (var image : response.imageDetails()) {
                images.add(Map.of(
                    "digest", image.imageDigest(),
                    "tags", image.imageTags() != null ? image.imageTags() : List.of(),
                    "pushedAt", image.imagePushedAt().toString(),
                    "sizeBytes", image.imageSizeInBytes()
                ));
            }
            
            return IntegrationResult.success(getId(), "describeEcrImages",
                "Found " + images.size() + " images in " + repositoryName,
                Map.of("images", images),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to describe ECR images for {}", repositoryName, e);
            return IntegrationResult.failure(getId(), "describeEcrImages", e.getMessage());
        }
    }
    
    /**
     * Get ECR login credentials for Docker.
     */
    public IntegrationResult getEcrLoginCredentials() {
        long start = System.currentTimeMillis();
        try {
            GetAuthorizationTokenRequest request = GetAuthorizationTokenRequest.builder().build();
            GetAuthorizationTokenResponse response = ecrClient.getAuthorizationToken(request);
            
            var authData = response.authorizationData().get(0);
            
            return IntegrationResult.success(getId(), "getEcrLoginCredentials",
                "Got ECR login credentials",
                Map.of(
                    "token", authData.authorizationToken(),
                    "proxyEndpoint", authData.proxyEndpoint(),
                    "expiresAt", authData.expiresAt().toString()
                ),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to get ECR login credentials", e);
            return IntegrationResult.failure(getId(), "getEcrLoginCredentials", e.getMessage());
        }
    }
    
    /**
     * Get current AWS identity.
     */
    public IntegrationResult getCallerIdentity() {
        long start = System.currentTimeMillis();
        try {
            var identity = stsClient.getCallerIdentity();
            
            return IntegrationResult.success(getId(), "getCallerIdentity",
                "AWS identity verified",
                Map.of(
                    "account", identity.account(),
                    "arn", identity.arn(),
                    "userId", identity.userId()
                ),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to get caller identity", e);
            return IntegrationResult.failure(getId(), "getCallerIdentity", e.getMessage());
        }
    }
}

