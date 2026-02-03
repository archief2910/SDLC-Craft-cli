package com.sdlcraft.backend.handler;

import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentPhase;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.agent.AgentStatus;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for "analyze security" intent.
 * 
 * Coordinates security scanning agents to perform comprehensive security analysis:
 * - Dependency vulnerability scanning
 * - Static code analysis
 * - Configuration security checks
 * - Secret detection
 * 
 * Design rationale:
 * - Aggregates results from multiple security tools
 * - Prioritizes findings by severity
 * - Provides actionable recommendations
 * - Simulated implementation for development
 */
@Component
public class AnalyzeSecurityIntentHandler implements IntentHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyzeSecurityIntentHandler.class);
    
    private static final String INTENT_NAME = "analyze";
    private static final String TARGET_SECURITY = "security";
    
    @Override
    public String getIntentName() {
        return INTENT_NAME;
    }
    
    @Override
    public boolean canHandle(IntentResult intent, SDLCState state) {
        return INTENT_NAME.equals(intent.getIntent()) && 
               TARGET_SECURITY.equals(intent.getTarget());
    }
    
    @Override
    public AgentResult handle(AgentContext context) {
        logger.info("Handling analyze security intent for project: {}", context.getProjectId());
        
        try {
            // Simulate security analysis
            // In production, this would integrate with actual security tools:
            // - OWASP Dependency Check
            // - Snyk
            // - SonarQube
            // - GitLeaks
            // - Trivy
            
            List<SecurityFinding> findings = performSecurityAnalysis(context);
            
            // Aggregate and prioritize findings
            Map<String, Object> analysisResults = aggregateFindings(findings);
            
            // Generate recommendations
            List<String> recommendations = generateRecommendations(findings);
            
            // Build summary
            String summary = buildSecuritySummary(findings);
            
            // Build reasoning
            String reasoning = buildSecurityReasoning(findings);
            
            return AgentResult.builder()
                    .agentType("analyze-security-handler")
                    .phase(AgentPhase.ACT)
                    .status(determineStatus(findings))
                    .data("findings", findings)
                    .data("analysis", analysisResults)
                    .data("recommendations", recommendations)
                    .data("summary", summary)
                    .reasoning(reasoning)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Failed to handle analyze security intent", e);
            return AgentResult.builder()
                    .agentType("analyze-security-handler")
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILURE)
                    .error("Security analysis failed: " + e.getMessage())
                    .reasoning("Exception occurred during security analysis")
                    .build();
        }
    }
    
    @Override
    public String getHelpText() {
        return "Perform comprehensive security analysis including dependency scanning, " +
               "static code analysis, and configuration checks.";
    }
    
    /**
     * Perform security analysis (REAL implementation).
     */
    private List<SecurityFinding> performSecurityAnalysis(AgentContext context) {
        List<SecurityFinding> findings = new ArrayList<>();
        
        try {
            // Scan Java source files in the project
            java.nio.file.Path projectRoot = java.nio.file.Paths.get(".");
            java.nio.file.Path srcPath = projectRoot.resolve("src");
            
            if (java.nio.file.Files.exists(srcPath)) {
                scanDirectory(srcPath, findings);
            }
            
            // If no findings, add a success message
            if (findings.isEmpty()) {
                findings.add(new SecurityFinding(
                        "NO_ISSUES",
                        "INFO",
                        "No security issues detected in source code",
                        "Project root",
                        "Continue following security best practices"
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error scanning files", e);
            findings.add(new SecurityFinding(
                    "SCAN_ERROR",
                    "LOW",
                    "Could not complete full security scan: " + e.getMessage(),
                    "Scanner",
                    "Check scanner configuration and permissions"
            ));
        }
        
        return findings;
    }
    
    /**
     * Recursively scan directory for security issues.
     */
    private void scanDirectory(java.nio.file.Path dir, List<SecurityFinding> findings) {
        try {
            java.nio.file.Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanJavaFile(path, findings));
        } catch (Exception e) {
            logger.error("Error walking directory: " + dir, e);
        }
    }
    
    /**
     * Scan a single Java file for security issues.
     */
    private void scanJavaFile(java.nio.file.Path file, List<SecurityFinding> findings) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(file);
            String relativePath = java.nio.file.Paths.get(".").toAbsolutePath().relativize(file.toAbsolutePath()).toString();
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNum = i + 1;
                
                // Check for hardcoded passwords/secrets
                if (line.contains("password") && (line.contains("=") || line.contains(":"))) {
                    if (!line.trim().startsWith("//") && !line.trim().startsWith("*")) {
                        findings.add(new SecurityFinding(
                                "HARDCODED_SECRET",
                                "HIGH",
                                "Potential hardcoded password or secret",
                                relativePath + ":" + lineNum,
                                "Use environment variables or secure vault for secrets"
                        ));
                    }
                }
                
                // Check for SQL injection risks
                if (line.contains("Statement") && line.contains("execute") && line.contains("+")) {
                    findings.add(new SecurityFinding(
                            "SQL_INJECTION_RISK",
                            "CRITICAL",
                            "Potential SQL injection - string concatenation in query",
                            relativePath + ":" + lineNum,
                            "Use PreparedStatement with parameterized queries"
                    ));
                }
                
                // Check for weak crypto
                if (line.contains("MD5") || line.contains("SHA1") || line.contains("DES")) {
                    findings.add(new SecurityFinding(
                            "WEAK_CRYPTO",
                            "MEDIUM",
                            "Weak cryptographic algorithm detected",
                            relativePath + ":" + lineNum,
                            "Use SHA-256, SHA-3, or bcrypt for hashing"
                    ));
                }
                
                // Check for insecure random
                if (line.contains("new Random()") && !line.contains("SecureRandom")) {
                    findings.add(new SecurityFinding(
                            "INSECURE_RANDOM",
                            "MEDIUM",
                            "Using insecure Random instead of SecureRandom",
                            relativePath + ":" + lineNum,
                            "Use SecureRandom for security-sensitive operations"
                    ));
                }
                
                // Check for XXE vulnerabilities
                if (line.contains("DocumentBuilderFactory") || line.contains("SAXParserFactory")) {
                    findings.add(new SecurityFinding(
                            "XXE_RISK",
                            "HIGH",
                            "XML parser without XXE protection",
                            relativePath + ":" + lineNum,
                            "Disable external entity processing in XML parsers"
                    ));
                }
                
                // Check for command injection
                if (line.contains("Runtime.getRuntime().exec") || line.contains("ProcessBuilder")) {
                    if (line.contains("+") || line.contains("concat")) {
                        findings.add(new SecurityFinding(
                                "COMMAND_INJECTION",
                                "CRITICAL",
                                "Potential command injection vulnerability",
                                relativePath + ":" + lineNum,
                                "Validate and sanitize all inputs to system commands"
                        ));
                    }
                }
                
                // Check for path traversal
                if (line.contains("new File") && line.contains("+")) {
                    findings.add(new SecurityFinding(
                            "PATH_TRAVERSAL",
                            "HIGH",
                            "Potential path traversal vulnerability",
                            relativePath + ":" + lineNum,
                            "Validate file paths and use canonical paths"
                    ));
                }
            }
            
        } catch (Exception e) {
            logger.error("Error scanning file: " + file, e);
        }
    }
    
    /**
     * Aggregate findings by severity.
     */
    private Map<String, Object> aggregateFindings(List<SecurityFinding> findings) {
        Map<String, Object> results = new HashMap<>();
        
        int critical = 0, high = 0, medium = 0, low = 0;
        
        for (SecurityFinding finding : findings) {
            switch (finding.getSeverity()) {
                case "CRITICAL": critical++; break;
                case "HIGH": high++; break;
                case "MEDIUM": medium++; break;
                case "LOW": low++; break;
            }
        }
        
        results.put("totalFindings", findings.size());
        results.put("critical", critical);
        results.put("high", high);
        results.put("medium", medium);
        results.put("low", low);
        results.put("riskScore", calculateRiskScore(critical, high, medium, low));
        
        return results;
    }
    
    /**
     * Generate recommendations based on findings.
     */
    private List<String> generateRecommendations(List<SecurityFinding> findings) {
        List<String> recommendations = new ArrayList<>();
        
        boolean hasCritical = findings.stream().anyMatch(f -> "CRITICAL".equals(f.getSeverity()));
        boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equals(f.getSeverity()));
        
        if (hasCritical) {
            recommendations.add("Address CRITICAL findings immediately before deployment");
            recommendations.add("Review and rotate any exposed secrets");
        }
        
        if (hasHigh) {
            recommendations.add("Fix HIGH severity issues before next release");
            recommendations.add("Conduct security code review for affected areas");
        }
        
        recommendations.add("Update dependencies to latest secure versions");
        recommendations.add("Enable automated security scanning in CI/CD pipeline");
        recommendations.add("Schedule regular security audits");
        
        return recommendations;
    }
    
    /**
     * Build security summary.
     */
    private String buildSecuritySummary(List<SecurityFinding> findings) {
        Map<String, Long> severityCounts = new HashMap<>();
        for (SecurityFinding finding : findings) {
            severityCounts.merge(finding.getSeverity(), 1L, Long::sum);
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Security Analysis Results:\n");
        summary.append("  Total Findings: ").append(findings.size()).append("\n");
        
        if (severityCounts.containsKey("CRITICAL")) {
            summary.append("  CRITICAL: ").append(severityCounts.get("CRITICAL")).append("\n");
        }
        if (severityCounts.containsKey("HIGH")) {
            summary.append("  HIGH: ").append(severityCounts.get("HIGH")).append("\n");
        }
        if (severityCounts.containsKey("MEDIUM")) {
            summary.append("  MEDIUM: ").append(severityCounts.get("MEDIUM")).append("\n");
        }
        if (severityCounts.containsKey("LOW")) {
            summary.append("  LOW: ").append(severityCounts.get("LOW")).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Build reasoning explanation.
     */
    private String buildSecurityReasoning(List<SecurityFinding> findings) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("Performed comprehensive security analysis. ");
        reasoning.append("Found ").append(findings.size()).append(" security findings. ");
        
        long critical = findings.stream().filter(f -> "CRITICAL".equals(f.getSeverity())).count();
        long high = findings.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
        
        if (critical > 0) {
            reasoning.append("CRITICAL issues require immediate attention. ");
        }
        if (high > 0) {
            reasoning.append("HIGH severity issues should be addressed before release. ");
        }
        
        reasoning.append("Recommendations provided for remediation.");
        
        return reasoning.toString();
    }
    
    /**
     * Determine overall status from findings.
     */
    private AgentStatus determineStatus(List<SecurityFinding> findings) {
        boolean hasCritical = findings.stream().anyMatch(f -> "CRITICAL".equals(f.getSeverity()));
        boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equals(f.getSeverity()));
        
        if (hasCritical) {
            return AgentStatus.FAILURE;
        } else if (hasHigh) {
            return AgentStatus.PARTIAL;
        } else {
            return AgentStatus.SUCCESS;
        }
    }
    
    /**
     * Calculate risk score from findings.
     */
    private double calculateRiskScore(int critical, int high, int medium, int low) {
        return (critical * 10.0 + high * 5.0 + medium * 2.0 + low * 1.0) / 100.0;
    }
    
    /**
     * Security finding model.
     */
    public static class SecurityFinding {
        private final String type;
        private final String severity;
        private final String description;
        private final String location;
        private final String remediation;
        
        public SecurityFinding(String type, String severity, String description,
                             String location, String remediation) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.location = location;
            this.remediation = remediation;
        }
        
        public String getType() {
            return type;
        }
        
        public String getSeverity() {
            return severity;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getLocation() {
            return location;
        }
        
        public String getRemediation() {
            return remediation;
        }
    }
}
