package com.example.sdlcraft.custom;

import com.sdlcraft.backend.agent.Agent;
import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.agent.AgentPhase;
import com.sdlcraft.backend.agent.AgentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DatabaseMigrationAgent handles database schema migrations.
 * 
 * This agent follows the PLAN → ACT → OBSERVE → REFLECT pattern:
 * - PLAN: Detects pending migrations and creates execution plan
 * - ACT: Applies migrations sequentially with backup
 * - OBSERVE: Verifies migrations were applied correctly
 * - REFLECT: Analyzes outcome and suggests next steps or rollback
 * 
 * @author SDLCraft Team
 * @since 1.0.0
 */
@Component
public class DatabaseMigrationAgent implements Agent {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationAgent.class);
    
    @Override
    public String getType() {
        return "DatabaseMigrationAgent";
    }
    
    @Override
    public AgentResult plan(AgentContext context) {
        logger.info("Planning database migrations for project: {}", 
            context.getParameters().get("projectId"));
        
        try {
            // Detect pending migrations
            List<String> pendingMigrations = detectPendingMigrations(context);
            
            if (pendingMigrations.isEmpty()) {
                return AgentResult.builder()
                    .agentType(getType())
                    .phase(AgentPhase.PLAN)
                    .status(AgentStatus.SUCCESS)
                    .result(Map.of("migrations", List.of()))
                    .reasoning("No pending migrations found. Database schema is up to date.")
                    .build();
            }
            
            // Estimate duration and assess risk
            long estimatedDuration = estimateDuration(pendingMigrations);
            boolean backupRequired = shouldCreateBackup(context);
            String riskLevel = assessRisk(pendingMigrations, context);
            
            // Create execution plan
            Map<String, Object> plan = new HashMap<>();
            plan.put("migrations", pendingMigrations);
            plan.put("estimatedDuration", estimatedDuration);
            plan.put("backupRequired", backupRequired);
            plan.put("riskLevel", riskLevel);
            plan.put("plannedAt", LocalDateTime.now());
            
            String reasoning = String.format(
                "Identified %d pending migration(s). " +
                "Estimated duration: %d seconds. " +
                "Backup %s. Risk level: %s",
                pendingMigrations.size(),
                estimatedDuration / 1000,
                backupRequired ? "required" : "not required",
                riskLevel
            );
            
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.PLAN)
                .status(AgentStatus.SUCCESS)
                .result(plan)
                .reasoning(reasoning)
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to plan migrations", e);
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.PLAN)
                .status(AgentStatus.FAILED)
                .error(e.getMessage())
                .reasoning("Failed to detect pending migrations: " + e.getClass().getSimpleName())
                .build();
        }
    }
    
    @Override
    public AgentResult act(AgentContext context, Map<String, Object> plan) {
        logger.info("Executing database migrations");
        
        try {
            @SuppressWarnings("unchecked")
            List<String> migrations = (List<String>) plan.get("migrations");
            boolean backupRequired = (Boolean) plan.get("backupRequired");
            
            // Create backup if required
            String backupId = null;
            if (backupRequired) {
                backupId = createBackup(context);
                logger.info("Created backup: {}", backupId);
            }
            
            // Apply migrations sequentially
            List<String> applied = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            
            for (String migration : migrations) {
                try {
                    logger.info("Applying migration: {}", migration);
                    applyMigration(migration, context);
                    applied.add(migration);
                    logger.info("✓ Applied migration: {}", migration);
                } catch (Exception e) {
                    logger.error("✗ Failed to apply migration: {}", migration, e);
                    failed.add(migration);
                    
                    // Stop on first failure
                    Map<String, Object> result = new HashMap<>();
                    result.put("appliedMigrations", applied);
                    result.put("failedMigration", migration);
                    result.put("backupId", backupId);
                    result.put("error", e.getMessage());
                    
                    return AgentResult.builder()
                        .agentType(getType())
                        .phase(AgentPhase.ACT)
                        .status(AgentStatus.FAILED)
                        .error("Migration failed: " + migration)
                        .result(result)
                        .reasoning(String.format(
                            "Applied %d of %d migrations before failure. " +
                            "Backup available: %s",
                            applied.size(), migrations.size(), backupId
                        ))
                        .build();
                }
            }
            
            // All migrations applied successfully
            Map<String, Object> result = new HashMap<>();
            result.put("appliedMigrations", applied);
            result.put("backupId", backupId);
            result.put("completedAt", LocalDateTime.now());
            
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.ACT)
                .status(AgentStatus.SUCCESS)
                .result(result)
                .reasoning(String.format(
                    "Successfully applied all %d migration(s). " +
                    "Database schema updated.",
                    applied.size()
                ))
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to execute migrations", e);
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.ACT)
                .status(AgentStatus.FAILED)
                .error(e.getMessage())
                .reasoning("Unexpected error during migration execution")
                .build();
        }
    }
    
    @Override
    public AgentResult observe(AgentContext context, Map<String, Object> actionResult) {
        logger.info("Verifying database migrations");
        
        try {
            @SuppressWarnings("unchecked")
            List<String> applied = (List<String>) actionResult.get("appliedMigrations");
            
            if (applied == null || applied.isEmpty()) {
                return AgentResult.builder()
                    .agentType(getType())
                    .phase(AgentPhase.OBSERVE)
                    .status(AgentStatus.SUCCESS)
                    .result(Map.of("verified", true))
                    .reasoning("No migrations to verify")
                    .build();
            }
            
            // Verify each migration was applied correctly
            List<String> verified = new ArrayList<>();
            List<String> verificationFailed = new ArrayList<>();
            
            for (String migration : applied) {
                if (verifyMigration(migration, context)) {
                    verified.add(migration);
                } else {
                    verificationFailed.add(migration);
                }
            }
            
            boolean allVerified = verificationFailed.isEmpty();
            
            Map<String, Object> result = new HashMap<>();
            result.put("verified", allVerified);
            result.put("verifiedMigrations", verified);
            result.put("failedVerifications", verificationFailed);
            
            String reasoning = allVerified ?
                String.format("All %d migration(s) verified successfully", verified.size()) :
                String.format("Verification failed for %d migration(s): %s", 
                    verificationFailed.size(), verificationFailed);
            
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.OBSERVE)
                .status(allVerified ? AgentStatus.SUCCESS : AgentStatus.FAILED)
                .result(result)
                .reasoning(reasoning)
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to verify migrations", e);
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.OBSERVE)
                .status(AgentStatus.FAILED)
                .error(e.getMessage())
                .reasoning("Verification process failed")
                .build();
        }
    }
    
    @Override
    public AgentResult reflect(AgentContext context, Map<String, Object> observation) {
        logger.info("Reflecting on migration outcome");
        
        try {
            boolean verified = (Boolean) observation.get("verified");
            
            if (!verified) {
                // Verification failed - recommend rollback
                @SuppressWarnings("unchecked")
                List<String> failedVerifications = 
                    (List<String>) observation.get("failedVerifications");
                
                Map<String, Object> result = new HashMap<>();
                result.put("recommendation", "ROLLBACK");
                result.put("reason", "Migrations failed verification");
                result.put("failedMigrations", failedVerifications);
                result.put("nextSteps", List.of(
                    "Restore from backup",
                    "Investigate migration failures",
                    "Fix migration scripts",
                    "Retry after fixes"
                ));
                
                return AgentResult.builder()
                    .agentType(getType())
                    .phase(AgentPhase.REFLECT)
                    .status(AgentStatus.SUCCESS)
                    .result(result)
                    .reasoning("Recommend rolling back due to verification failures. " +
                              "Database may be in inconsistent state.")
                    .build();
            }
            
            // All verified - recommend proceeding
            Map<String, Object> result = new HashMap<>();
            result.put("recommendation", "PROCEED");
            result.put("nextSteps", List.of(
                "Update schema documentation",
                "Notify team of schema changes",
                "Monitor application for issues",
                "Clean up old backups if needed"
            ));
            result.put("outcome", "SUCCESS");
            
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.REFLECT)
                .status(AgentStatus.SUCCESS)
                .result(result)
                .reasoning("Migrations completed successfully and verified. " +
                          "Database schema is up to date and consistent.")
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to reflect on outcome", e);
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.REFLECT)
                .status(AgentStatus.FAILED)
                .error(e.getMessage())
                .reasoning("Reflection process failed")
                .build();
        }
    }
    
    // Helper methods
    
    private List<String> detectPendingMigrations(AgentContext context) {
        // In a real implementation, this would:
        // 1. Query the database for applied migrations
        // 2. Scan the filesystem for migration files
        // 3. Return the difference
        
        // Example implementation
        return List.of(
            "V1__create_users_table.sql",
            "V2__add_email_index.sql",
            "V3__create_orders_table.sql"
        );
    }
    
    private long estimateDuration(List<String> migrations) {
        // Estimate 5 seconds per migration
        return migrations.size() * 5000L;
    }
    
    private boolean shouldCreateBackup(AgentContext context) {
        // Always create backup for production
        String environment = (String) context.getParameters().get("environment");
        return "production".equals(environment) || "staging".equals(environment);
    }
    
    private String assessRisk(List<String> migrations, AgentContext context) {
        // Assess risk based on number of migrations and environment
        String environment = (String) context.getParameters().get("environment");
        int count = migrations.size();
        
        if ("production".equals(environment)) {
            return count > 5 ? "HIGH" : "MEDIUM";
        }
        
        return count > 10 ? "MEDIUM" : "LOW";
    }
    
    private String createBackup(AgentContext context) {
        // In a real implementation, this would create a database backup
        String backupId = "backup-" + System.currentTimeMillis();
        logger.info("Creating backup: {}", backupId);
        return backupId;
    }
    
    private void applyMigration(String migration, AgentContext context) {
        // In a real implementation, this would execute the migration SQL
        logger.info("Applying migration: {}", migration);
        
        // Simulate migration execution
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private boolean verifyMigration(String migration, AgentContext context) {
        // In a real implementation, this would verify the migration was applied
        // by checking the schema or migration tracking table
        logger.info("Verifying migration: {}", migration);
        return true;
    }
}
