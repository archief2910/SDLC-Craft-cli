package com.sdlcraft.backend.policy;

/**
 * PolicySeverity represents the severity of a policy violation.
 */
public enum PolicySeverity {
    /**
     * INFO: Informational, does not block execution.
     */
    INFO,
    
    /**
     * WARNING: Warning, execution can proceed but user should be aware.
     */
    WARNING,
    
    /**
     * ERROR: Error, execution should be blocked unless user confirms.
     */
    ERROR
}
