package com.sdlcraft.backend.integration.docker;

import com.sdlcraft.backend.integration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Docker integration for container operations.
 * 
 * Supports:
 * - Building images
 * - Pushing to registries
 * - Running containers
 * - Docker Compose operations
 */
@Component
public class DockerIntegration implements Integration {

    private static final Logger logger = LoggerFactory.getLogger(DockerIntegration.class);
    
    @Value("${sdlcraft.integration.docker.registry:}")
    private String defaultRegistry;
    
    @Value("${sdlcraft.integration.docker.timeout-seconds:300}")
    private int timeoutSeconds;

    @Override
    public String getId() {
        return "docker";
    }

    @Override
    public String getName() {
        return "Docker";
    }

    @Override
    public boolean isConfigured() {
        // Docker is configured if docker command is available
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version");
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public IntegrationHealth healthCheck() {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}");
            Process p = pb.start();
            
            String version = new String(p.getInputStream().readAllBytes()).trim();
            boolean success = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                return IntegrationHealth.healthy(getId(), System.currentTimeMillis() - start);
            }
            return IntegrationHealth.unhealthy(getId(), "Docker daemon not responding");
        } catch (Exception e) {
            logger.error("Docker health check failed", e);
            return IntegrationHealth.unhealthy(getId(), e.getMessage());
        }
    }

    @Override
    public String[] getSupportedActions() {
        return new String[]{
            "build",
            "push",
            "pull",
            "run",
            "stop",
            "logs",
            "composeUp",
            "composeDown",
            "listContainers",
            "listImages"
        };
    }
    
    /**
     * Build a Docker image.
     */
    public IntegrationResult build(String contextPath, String imageName, String tag, 
                                    String dockerfile, Map<String, String> buildArgs) {
        long start = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("build");
            command.add("-t");
            command.add(imageName + ":" + (tag != null ? tag : "latest"));
            
            if (dockerfile != null) {
                command.add("-f");
                command.add(dockerfile);
            }
            
            if (buildArgs != null) {
                for (var entry : buildArgs.entrySet()) {
                    command.add("--build-arg");
                    command.add(entry.getKey() + "=" + entry.getValue());
                }
            }
            
            command.add(contextPath != null ? contextPath : ".");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = new String(p.getInputStream().readAllBytes());
            boolean success = p.waitFor(timeoutSeconds, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                return IntegrationResult.success(getId(), "build",
                    "Built image " + imageName + ":" + tag,
                    Map.of("imageName", imageName, "tag", tag, "output", truncateOutput(output)),
                    System.currentTimeMillis() - start);
            } else {
                return IntegrationResult.failure(getId(), "build", 
                    "Build failed: " + truncateOutput(output));
            }
        } catch (Exception e) {
            logger.error("Docker build failed", e);
            return IntegrationResult.failure(getId(), "build", e.getMessage());
        }
    }
    
    /**
     * Push an image to a registry.
     */
    public IntegrationResult push(String imageName, String tag) {
        long start = System.currentTimeMillis();
        try {
            String fullImage = imageName + ":" + (tag != null ? tag : "latest");
            
            ProcessBuilder pb = new ProcessBuilder("docker", "push", fullImage);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = new String(p.getInputStream().readAllBytes());
            boolean success = p.waitFor(timeoutSeconds, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                return IntegrationResult.success(getId(), "push",
                    "Pushed image " + fullImage,
                    Map.of("imageName", imageName, "tag", tag),
                    System.currentTimeMillis() - start);
            } else {
                return IntegrationResult.failure(getId(), "push",
                    "Push failed: " + truncateOutput(output));
            }
        } catch (Exception e) {
            logger.error("Docker push failed", e);
            return IntegrationResult.failure(getId(), "push", e.getMessage());
        }
    }
    
    /**
     * Run a container.
     */
    public IntegrationResult run(String imageName, String containerName, 
                                  Map<String, String> envVars, Map<Integer, Integer> portMappings,
                                  boolean detached) {
        long start = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("run");
            
            if (detached) {
                command.add("-d");
            }
            
            if (containerName != null) {
                command.add("--name");
                command.add(containerName);
            }
            
            if (envVars != null) {
                for (var entry : envVars.entrySet()) {
                    command.add("-e");
                    command.add(entry.getKey() + "=" + entry.getValue());
                }
            }
            
            if (portMappings != null) {
                for (var entry : portMappings.entrySet()) {
                    command.add("-p");
                    command.add(entry.getKey() + ":" + entry.getValue());
                }
            }
            
            command.add(imageName);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = new String(p.getInputStream().readAllBytes()).trim();
            boolean success = p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                return IntegrationResult.success(getId(), "run",
                    "Started container " + (containerName != null ? containerName : output.substring(0, 12)),
                    Map.of("containerId", output, "imageName", imageName),
                    System.currentTimeMillis() - start);
            } else {
                return IntegrationResult.failure(getId(), "run",
                    "Run failed: " + truncateOutput(output));
            }
        } catch (Exception e) {
            logger.error("Docker run failed", e);
            return IntegrationResult.failure(getId(), "run", e.getMessage());
        }
    }
    
    /**
     * Start docker-compose services.
     */
    public IntegrationResult composeUp(String composePath, boolean detached) {
        long start = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("compose");
            
            if (composePath != null) {
                command.add("-f");
                command.add(composePath);
            }
            
            command.add("up");
            
            if (detached) {
                command.add("-d");
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = new String(p.getInputStream().readAllBytes());
            boolean success = p.waitFor(timeoutSeconds, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                return IntegrationResult.success(getId(), "composeUp",
                    "Started compose services",
                    Map.of("output", truncateOutput(output)),
                    System.currentTimeMillis() - start);
            } else {
                return IntegrationResult.failure(getId(), "composeUp",
                    "Compose up failed: " + truncateOutput(output));
            }
        } catch (Exception e) {
            logger.error("Docker compose up failed", e);
            return IntegrationResult.failure(getId(), "composeUp", e.getMessage());
        }
    }
    
    /**
     * Stop docker-compose services.
     */
    public IntegrationResult composeDown(String composePath) {
        long start = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("compose");
            
            if (composePath != null) {
                command.add("-f");
                command.add(composePath);
            }
            
            command.add("down");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = new String(p.getInputStream().readAllBytes());
            boolean success = p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                return IntegrationResult.success(getId(), "composeDown",
                    "Stopped compose services",
                    Map.of("output", truncateOutput(output)),
                    System.currentTimeMillis() - start);
            } else {
                return IntegrationResult.failure(getId(), "composeDown",
                    "Compose down failed: " + truncateOutput(output));
            }
        } catch (Exception e) {
            logger.error("Docker compose down failed", e);
            return IntegrationResult.failure(getId(), "composeDown", e.getMessage());
        }
    }
    
    /**
     * List running containers.
     */
    public IntegrationResult listContainers(boolean all) {
        long start = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("ps");
            if (all) command.add("-a");
            command.add("--format");
            command.add("{{.ID}}|{{.Image}}|{{.Status}}|{{.Names}}");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            
            String output = new String(p.getInputStream().readAllBytes());
            boolean success = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (success) {
                List<Map<String, String>> containers = new ArrayList<>();
                for (String line : output.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 4) {
                            containers.add(Map.of(
                                "id", parts[0],
                                "image", parts[1],
                                "status", parts[2],
                                "name", parts[3]
                            ));
                        }
                    }
                }
                
                return IntegrationResult.success(getId(), "listContainers",
                    "Found " + containers.size() + " containers",
                    Map.of("containers", containers),
                    System.currentTimeMillis() - start);
            } else {
                return IntegrationResult.failure(getId(), "listContainers", "Failed to list containers");
            }
        } catch (Exception e) {
            logger.error("Docker list containers failed", e);
            return IntegrationResult.failure(getId(), "listContainers", e.getMessage());
        }
    }
    
    private String truncateOutput(String output) {
        if (output.length() > 2000) {
            return output.substring(0, 2000) + "... (truncated)";
        }
        return output;
    }
}

