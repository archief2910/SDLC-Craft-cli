-- SDLCraft Backend Database Schema - H2 Compatible

-- SDLC State table
CREATE TABLE sdlc_state (
    project_id VARCHAR(255) PRIMARY KEY,
    current_phase VARCHAR(50) NOT NULL CHECK (current_phase IN ('PLANNING', 'DEVELOPMENT', 'TESTING', 'STAGING', 'PRODUCTION')),
    risk_level VARCHAR(50) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    test_coverage DOUBLE CHECK (test_coverage >= 0 AND test_coverage <= 1),
    open_issues INTEGER DEFAULT 0,
    last_deployment TIMESTAMP,
    custom_metrics VARCHAR(4000) DEFAULT '{}',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sdlc_state_phase_risk ON sdlc_state(current_phase, risk_level);

-- Command Executions table
CREATE TABLE command_executions (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    raw_command TEXT NOT NULL,
    intent VARCHAR(100),
    target VARCHAR(255),
    modifiers VARCHAR(4000) DEFAULT '{}',
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    outcome TEXT,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    CONSTRAINT fk_project FOREIGN KEY (project_id) REFERENCES sdlc_state(project_id) ON DELETE CASCADE
);

CREATE INDEX idx_command_executions_project ON command_executions(project_id);
CREATE INDEX idx_command_executions_user ON command_executions(user_id);
CREATE INDEX idx_command_executions_intent ON command_executions(intent);
CREATE INDEX idx_command_executions_started_at ON command_executions(started_at DESC);
CREATE INDEX idx_command_executions_status ON command_executions(status);

-- Audit Logs table
CREATE TABLE audit_logs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255),
    old_value VARCHAR(4000),
    new_value VARCHAR(4000),
    risk_level VARCHAR(50) CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    confirmation_required BOOLEAN DEFAULT FALSE,
    confirmation_received BOOLEAN DEFAULT FALSE,
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_project FOREIGN KEY (project_id) REFERENCES sdlc_state(project_id) ON DELETE CASCADE
);

CREATE INDEX idx_audit_logs_project ON audit_logs(project_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_risk_level ON audit_logs(risk_level);

-- Agent Executions table
CREATE TABLE agent_executions (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL,
    agent_type VARCHAR(100) NOT NULL,
    phase VARCHAR(50) NOT NULL CHECK (phase IN ('PLAN', 'ACT', 'OBSERVE', 'REFLECT')),
    input_context VARCHAR(4000),
    output_result VARCHAR(4000),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT
);

CREATE INDEX idx_agent_executions_command ON agent_executions(execution_id);
CREATE INDEX idx_agent_executions_type ON agent_executions(agent_type);
CREATE INDEX idx_agent_executions_phase ON agent_executions(phase);
CREATE INDEX idx_agent_executions_status ON agent_executions(status);

-- Project Context table
CREATE TABLE project_context (
    project_id VARCHAR(255) PRIMARY KEY,
    project_name VARCHAR(255) NOT NULL,
    project_path TEXT NOT NULL,
    repository_url TEXT,
    context_data VARCHAR(4000) DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_context_project FOREIGN KEY (project_id) REFERENCES sdlc_state(project_id) ON DELETE CASCADE
);

-- Intent Definitions table
CREATE TABLE intent_definitions (
    name VARCHAR(100) PRIMARY KEY,
    description TEXT NOT NULL,
    required_parameters VARCHAR(4000) DEFAULT '[]',
    optional_parameters VARCHAR(4000) DEFAULT '[]',
    examples VARCHAR(4000) DEFAULT '[]',
    default_risk_level VARCHAR(50) NOT NULL CHECK (default_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert core intents
INSERT INTO intent_definitions (name, description, required_parameters, optional_parameters, examples, default_risk_level) VALUES
('status', 'Display current SDLC state and metrics', '[]', '["verbose"]', '["sdlc status"]', 'LOW'),
('analyze', 'Perform analysis on specified target', '["target"]', '["depth"]', '["sdlc analyze security"]', 'LOW'),
('improve', 'Identify and suggest improvements', '["target"]', '["auto-apply"]', '["sdlc improve performance"]', 'MEDIUM'),
('test', 'Execute tests for specified target', '["target"]', '["coverage"]', '["sdlc test unit"]', 'LOW'),
('debug', 'Debug issues in specified target', '["target"]', '["verbose"]', '["sdlc debug api"]', 'LOW'),
('prepare', 'Prepare for deployment or release', '["target"]', '["environment"]', '["sdlc prepare release"]', 'MEDIUM'),
('release', 'Execute release to environment', '["environment"]', '["version"]', '["sdlc release production"]', 'HIGH');
