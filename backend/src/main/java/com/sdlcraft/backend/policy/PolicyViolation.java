package com.sdlcraft.backend.policy;

/**
 * PolicyViolation represents a violation of a policy.
 * 
 * This includes the policy name, violation message, severity, and suggested action.
 */
public class PolicyViolation {
    
    private String policyName;
    private String message;
    private PolicySeverity severity;
    private String suggestedAction;
    
    public PolicyViolation() {
    }
    
    public PolicyViolation(String policyName, String message, PolicySeverity severity) {
        this.policyName = policyName;
        this.message = message;
        this.severity = severity;
    }
    
    // Getters and setters
    
    public String getPolicyName() {
        return policyName;
    }
    
    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public PolicySeverity getSeverity() {
        return severity;
    }
    
    public void setSeverity(PolicySeverity severity) {
        this.severity = severity;
    }
    
    public String getSuggestedAction() {
        return suggestedAction;
    }
    
    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }
    
    @Override
    public String toString() {
        return "PolicyViolation{" +
                "policyName='" + policyName + '\'' +
                ", message='" + message + '\'' +
                ", severity=" + severity +
                ", suggestedAction='" + suggestedAction + '\'' +
                '}';
    }
}
