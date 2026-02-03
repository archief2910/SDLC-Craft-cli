# SDLCraft Backend API Reference

## Overview

The SDLCraft Backend provides RESTful APIs for intent inference, agent orchestration, SDLC state management, and audit logging. All endpoints accept and return JSON unless otherwise specified.

**Base URL**: `http://localhost:8080/api/v1`

**Authentication**: Currently not implemented (future enhancement)

## Table of Contents

- [Intent Inference API](#intent-inference-api)
- [SDLC State API](#sdlc-state-api)
- [Agent Orchestration API](#agent-orchestration-api)
- [Memory API](#memory-api)
- [Audit API](#audit-api)
- [Policy API](#policy-api)
- [Error Codes](#error-codes)

---

## Intent Inference API

### Infer Intent from Natural Language

Converts natural language or ambiguous commands into structured intents.

**Endpoint**: `POST /api/v1/intent/infer`

**Request Body**:
```json
{
  "rawCommand": "check the status of my project",
  "userId": "user123",
  "projectId": "project-abc",
  "context": {
    "currentPhase": "DEVELOPMENT",
    "lastCommand": "sdlc test run"
  }
}
```

**Response** (200 OK):
```json
{
  "intent": "status",
  "target": "project",
  "modifiers": {},
  "confidence": 0.95,
  "explanation": "Interpreted as status query based on keywords 'check' and 'status'",
  "clarificationQuestions": []
}
```

**Response** (200 OK - Low Confidence):
```json
{
  "intent": "analyze",
  "target": "unknown",
  "modifiers": {},
  "confidence": 0.45,
  "explanation": "Ambiguous intent detected",
  "clarificationQuestions": [
    "What would you like to analyze? (security, performance, code quality)"
  ]
}
```

**Error Response** (400 Bad Request):
```json
{
  "error": "INVALID_REQUEST",
  "message": "rawCommand is required",
  "details": {
    "field": "rawCommand",
    "reason": "missing"
  }
}
```

**Error Response** (503 Service Unavailable):
```json
{
  "error": "LLM_UNAVAILABLE",
  "message": "LLM provider is currently unavailable",
  "details": {
    "provider": "mock",
    "fallback": "template-based inference"
  }
}
```

---

### Get Supported Intents

Returns a list of all registered intents.

**Endpoint**: `GET /api/v1/intent/supported`

**Response** (200 OK):
```json
{
  "intents": [
    {
      "name": "status",
      "description": "Query current SDLC state and metrics",
      "requiredParameters": [],
      "optionalParameters": ["verbose", "format"],
      "examples": [
        "sdlc status",
        "show me the current status",
        "what's the project state?"
      ],
      "defaultRiskLevel": "LOW"
    },
    {
      "name": "analyze",
      "description": "Perform analysis on code or infrastructure",
      "requiredParameters": ["target"],
      "optionalParameters": ["depth", "format"],
      "examples": [
        "sdlc analyze security",
        "analyze performance bottlenecks"
      ],
      "defaultRiskLevel": "LOW"
    }
  ]
}
```

---

### Register Custom Intent

Registers a new intent definition (requires admin privileges in future).

**Endpoint**: `POST /api/v1/intent/register`

**Request Body**:
```json
{
  "name": "deploy",
  "description": "Deploy application to specified environment",
  "requiredParameters": ["environment"],
  "optionalParameters": ["version", "rollback"],
  "examples": [
    "sdlc deploy staging",
    "deploy to production with version 1.2.3"
  ],
  "defaultRiskLevel": "HIGH"
}
```

**Response** (201 Created):
```json
{
  "message": "Intent registered successfully",
  "intent": {
    "name": "deploy",
    "description": "Deploy application to specified environment",
    "requiredParameters": ["environment"],
    "optionalParameters": ["version", "rollback"],
    "examples": [
      "sdlc deploy staging",
      "deploy to production with version 1.2.3"
    ],
    "defaultRiskLevel": "HIGH"
  }
}
```

**Error Response** (409 Conflict):
```json
{
  "error": "INTENT_ALREADY_EXISTS",
  "message": "Intent 'deploy' is already registered",
  "details": {
    "existingIntent": "deploy"
  }
}
```

---

## SDLC State API

### Get Current State

Retrieves the current SDLC state for a project.

**Endpoint**: `GET /api/v1/state/{projectId}`

**Path Parameters**:
- `projectId` (string, required): The project identifier

**Query Parameters**:
- `verbose` (boolean, optional): Include detailed metrics (default: false)

**Response** (200 OK):
```json
{
  "projectId": "project-abc",
  "currentPhase": "DEVELOPMENT",
  "riskLevel": "MEDIUM",
  "testCoverage": 0.75,
  "openIssues": 12,
  "lastDeployment": "2026-01-20T10:30:00Z",
  "releaseReadiness": 0.68,
  "customMetrics": {
    "codeQualityScore": 8.5,
    "securityScore": 9.2
  },
  "updatedAt": "2026-01-24T14:22:00Z"
}
```

**Response** (404 Not Found):
```json
{
  "error": "PROJECT_NOT_FOUND",
  "message": "Project 'project-xyz' does not exist",
  "details": {
    "projectId": "project-xyz"
  }
}
```

---

### Transition Phase

Transitions a project to a new SDLC phase.

**Endpoint**: `POST /api/v1/state/{projectId}/transition`

**Path Parameters**:
- `projectId` (string, required): The project identifier

**Request Body**:
```json
{
  "newPhase": "TESTING",
  "reason": "Development complete, ready for QA"
}
```

**Response** (200 OK):
```json
{
  "projectId": "project-abc",
  "previousPhase": "DEVELOPMENT",
  "currentPhase": "TESTING",
  "transitionedAt": "2026-01-24T14:25:00Z",
  "message": "Phase transition successful"
}
```

**Error Response** (400 Bad Request):
```json
{
  "error": "INVALID_PHASE_TRANSITION",
  "message": "Cannot transition from PLANNING to PRODUCTION",
  "details": {
    "currentPhase": "PLANNING",
    "requestedPhase": "PRODUCTION",
    "allowedTransitions": ["DEVELOPMENT"]
  }
}
```

---

### Update Metrics

Updates metrics for a project.

**Endpoint**: `PUT /api/v1/state/{projectId}/metrics`

**Path Parameters**:
- `projectId` (string, required): The project identifier

**Request Body**:
```json
{
  "testCoverage": 0.82,
  "openIssues": 8,
  "customMetrics": {
    "codeQualityScore": 9.0,
    "securityScore": 9.5
  }
}
```

**Response** (200 OK):
```json
{
  "projectId": "project-abc",
  "metrics": {
    "testCoverage": 0.82,
    "openIssues": 8,
    "customMetrics": {
      "codeQualityScore": 9.0,
      "securityScore": 9.5
    }
  },
  "riskLevel": "LOW",
  "releaseReadiness": 0.85,
  "updatedAt": "2026-01-24T14:30:00Z"
}
```

---

### Calculate Release Readiness

Calculates the release readiness score for a project.

**Endpoint**: `GET /api/v1/state/{projectId}/readiness`

**Path Parameters**:
- `projectId` (string, required): The project identifier

**Response** (200 OK):
```json
{
  "projectId": "project-abc",
  "releaseReadiness": 0.85,
  "factors": {
    "testCoverage": 0.82,
    "openCriticalIssues": 0,
    "openHighIssues": 2,
    "daysSinceLastDeploy": 5,
    "riskLevel": "LOW"
  },
  "recommendation": "READY_FOR_RELEASE",
  "blockers": []
}
```

**Response** (200 OK - Not Ready):
```json
{
  "projectId": "project-abc",
  "releaseReadiness": 0.45,
  "factors": {
    "testCoverage": 0.55,
    "openCriticalIssues": 3,
    "openHighIssues": 8,
    "daysSinceLastDeploy": 45,
    "riskLevel": "HIGH"
  },
  "recommendation": "NOT_READY",
  "blockers": [
    "Test coverage below 70% threshold",
    "3 critical issues must be resolved",
    "Risk level is HIGH"
  ]
}
```

---

## Agent Orchestration API

### Execute Intent

Executes an intent using the agent orchestration framework.

**Endpoint**: `POST /api/v1/agent/execute`

**Request Body**:
```json
{
  "intent": "analyze",
  "target": "security",
  "modifiers": {
    "depth": "full",
    "format": "json"
  },
  "projectId": "project-abc",
  "userId": "user123"
}
```

**Response** (202 Accepted):
```json
{
  "executionId": "exec-789xyz",
  "status": "RUNNING",
  "message": "Execution started",
  "streamUrl": "/api/v1/agent/stream/exec-789xyz"
}
```

**Error Response** (400 Bad Request):
```json
{
  "error": "MISSING_REQUIRED_PARAMETER",
  "message": "Intent 'analyze' requires parameter 'target'",
  "details": {
    "intent": "analyze",
    "missingParameter": "target"
  }
}
```

---

### Stream Execution Events

Streams real-time execution events using Server-Sent Events (SSE).

**Endpoint**: `GET /api/v1/agent/stream/{executionId}`

**Path Parameters**:
- `executionId` (string, required): The execution identifier

**Response** (200 OK - SSE Stream):
```
event: agent_started
data: {"agentType":"PlannerAgent","phase":"PLAN","timestamp":"2026-01-24T14:35:00Z"}

event: agent_progress
data: {"agentType":"PlannerAgent","message":"Analyzing security requirements","progress":0.25}

event: agent_completed
data: {"agentType":"PlannerAgent","phase":"PLAN","result":{"steps":["scan_dependencies","check_vulnerabilities"]}}

event: agent_started
data: {"agentType":"ExecutorAgent","phase":"ACT","timestamp":"2026-01-24T14:35:05Z"}

event: execution_completed
data: {"executionId":"exec-789xyz","status":"SUCCESS","result":{"findings":[]}}
```

---

### Get Execution Status

Retrieves the current status of an execution.

**Endpoint**: `GET /api/v1/agent/execution/{executionId}`

**Path Parameters**:
- `executionId` (string, required): The execution identifier

**Response** (200 OK):
```json
{
  "executionId": "exec-789xyz",
  "status": "SUCCESS",
  "intent": "analyze",
  "target": "security",
  "startedAt": "2026-01-24T14:35:00Z",
  "completedAt": "2026-01-24T14:36:30Z",
  "durationMs": 90000,
  "agentResults": [
    {
      "agentType": "PlannerAgent",
      "phase": "PLAN",
      "status": "SUCCESS",
      "result": {
        "steps": ["scan_dependencies", "check_vulnerabilities"]
      }
    },
    {
      "agentType": "ExecutorAgent",
      "phase": "ACT",
      "status": "SUCCESS",
      "result": {
        "findings": []
      }
    }
  ],
  "finalResult": {
    "securityScore": 9.5,
    "vulnerabilities": [],
    "recommendations": []
  }
}
```

---

## Memory API

### Store Command Execution

Stores a command execution in long-term memory.

**Endpoint**: `POST /api/v1/memory/command`

**Request Body**:
```json
{
  "userId": "user123",
  "projectId": "project-abc",
  "rawCommand": "sdlc analyze security",
  "intent": "analyze",
  "target": "security",
  "modifiers": {},
  "status": "SUCCESS",
  "outcome": "No vulnerabilities found",
  "durationMs": 90000
}
```

**Response** (201 Created):
```json
{
  "id": "cmd-456def",
  "message": "Command execution stored successfully",
  "storedAt": "2026-01-24T14:40:00Z"
}
```

---

### Query Command History

Queries command execution history.

**Endpoint**: `GET /api/v1/memory/commands`

**Query Parameters**:
- `intent` (string, optional): Filter by intent
- `projectId` (string, optional): Filter by project
- `userId` (string, optional): Filter by user
- `startDate` (ISO 8601, optional): Start of time range
- `endDate` (ISO 8601, optional): End of time range
- `limit` (integer, optional): Maximum results (default: 50, max: 500)

**Response** (200 OK):
```json
{
  "commands": [
    {
      "id": "cmd-456def",
      "userId": "user123",
      "projectId": "project-abc",
      "rawCommand": "sdlc analyze security",
      "intent": "analyze",
      "target": "security",
      "status": "SUCCESS",
      "outcome": "No vulnerabilities found",
      "startedAt": "2026-01-24T14:35:00Z",
      "completedAt": "2026-01-24T14:36:30Z",
      "durationMs": 90000
    }
  ],
  "total": 1,
  "limit": 50
}
```

---

### Semantic Search

Performs semantic similarity search on stored context.

**Endpoint**: `POST /api/v1/memory/search`

**Request Body**:
```json
{
  "query": "security vulnerabilities in dependencies",
  "projectId": "project-abc",
  "limit": 10
}
```

**Response** (200 OK):
```json
{
  "results": [
    {
      "content": "Previous security scan found 2 vulnerabilities in lodash dependency",
      "similarity": 0.92,
      "timestamp": "2026-01-20T10:00:00Z",
      "metadata": {
        "commandId": "cmd-123abc",
        "intent": "analyze"
      }
    }
  ],
  "total": 1
}
```

---

## Audit API

### Query Audit Logs

Retrieves audit log entries.

**Endpoint**: `GET /api/v1/audit/logs`

**Query Parameters**:
- `action` (string, optional): Filter by action type
- `entityType` (string, optional): Filter by entity type
- `userId` (string, optional): Filter by user
- `startDate` (ISO 8601, optional): Start of time range
- `endDate` (ISO 8601, optional): End of time range
- `riskLevel` (string, optional): Filter by risk level (LOW, MEDIUM, HIGH, CRITICAL)
- `limit` (integer, optional): Maximum results (default: 100, max: 1000)

**Response** (200 OK):
```json
{
  "logs": [
    {
      "id": "audit-789ghi",
      "action": "PHASE_TRANSITION",
      "entityType": "SDLC_STATE",
      "entityId": "project-abc",
      "userId": "user123",
      "oldValue": {"phase": "DEVELOPMENT"},
      "newValue": {"phase": "TESTING"},
      "riskLevel": "MEDIUM",
      "confirmed": false,
      "timestamp": "2026-01-24T14:25:00Z"
    },
    {
      "id": "audit-790jkl",
      "action": "HIGH_RISK_COMMAND_CONFIRMED",
      "entityType": "COMMAND",
      "entityId": "cmd-999zzz",
      "userId": "user123",
      "oldValue": null,
      "newValue": {
        "command": "sdlc deploy production",
        "confirmation": "yes"
      },
      "riskLevel": "HIGH",
      "confirmed": true,
      "timestamp": "2026-01-24T15:00:00Z"
    }
  ],
  "total": 2,
  "limit": 100
}
```

---

## Policy API

### Assess Risk

Assesses the risk level of an intent.

**Endpoint**: `POST /api/v1/policy/assess`

**Request Body**:
```json
{
  "intent": "deploy",
  "target": "production",
  "modifiers": {},
  "projectId": "project-abc",
  "currentState": {
    "phase": "STAGING",
    "riskLevel": "LOW",
    "testCoverage": 0.85
  }
}
```

**Response** (200 OK):
```json
{
  "riskLevel": "HIGH",
  "requiresConfirmation": true,
  "concerns": [
    "Deployment to production environment",
    "Irreversible operation"
  ],
  "explanation": "Production deployments are classified as HIGH risk and require explicit confirmation",
  "estimatedImpact": "Affects all production users"
}
```

---

### Check Policies

Checks if an intent violates any registered policies.

**Endpoint**: `POST /api/v1/policy/check`

**Request Body**:
```json
{
  "intent": "delete",
  "target": "database",
  "modifiers": {
    "environment": "production"
  },
  "projectId": "project-abc"
}
```

**Response** (200 OK - No Violations):
```json
{
  "violations": [],
  "allowed": true
}
```

**Response** (200 OK - With Violations):
```json
{
  "violations": [
    {
      "policyId": "no-prod-delete",
      "severity": "CRITICAL",
      "message": "Direct database deletion in production is prohibited",
      "recommendation": "Use backup and restore procedures instead"
    }
  ],
  "allowed": false
}
```

---

## Error Codes

### Client Errors (4xx)

| Code | Error | Description |
|------|-------|-------------|
| 400 | `INVALID_REQUEST` | Request body is malformed or missing required fields |
| 400 | `MISSING_REQUIRED_PARAMETER` | Intent requires a parameter that was not provided |
| 400 | `INVALID_PHASE_TRANSITION` | Requested phase transition is not allowed |
| 400 | `INVALID_INTENT_TARGET_COMBINATION` | The intent-target combination is not valid |
| 401 | `UNAUTHORIZED` | Authentication required (future) |
| 403 | `FORBIDDEN` | User lacks permission for this operation (future) |
| 404 | `PROJECT_NOT_FOUND` | Specified project does not exist |
| 404 | `EXECUTION_NOT_FOUND` | Specified execution ID does not exist |
| 409 | `INTENT_ALREADY_EXISTS` | Intent with this name is already registered |
| 409 | `POLICY_VIOLATION` | Operation violates one or more policies |

### Server Errors (5xx)

| Code | Error | Description |
|------|-------|-------------|
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error occurred |
| 503 | `LLM_UNAVAILABLE` | LLM provider is temporarily unavailable |
| 503 | `VECTOR_STORE_UNAVAILABLE` | Vector store is temporarily unavailable |
| 503 | `DATABASE_UNAVAILABLE` | Database connection failed |

### Error Response Format

All error responses follow this structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "details": {
    "additionalField": "Additional context about the error"
  },
  "timestamp": "2026-01-24T15:30:00Z",
  "path": "/api/v1/intent/infer"
}
```

---

## Rate Limiting

Currently not implemented. Future versions will include rate limiting with the following headers:

- `X-RateLimit-Limit`: Maximum requests per time window
- `X-RateLimit-Remaining`: Remaining requests in current window
- `X-RateLimit-Reset`: Time when the rate limit resets (Unix timestamp)

---

## Versioning

The API uses URL-based versioning (`/api/v1/`). Breaking changes will result in a new version (`/api/v2/`). Non-breaking changes (new fields, new endpoints) will be added to existing versions.

---

## Support

For API support and questions:
- GitHub Issues: [SDLCraft Repository](https://github.com/your-org/sdlcraft)
- Documentation: See `docs/` directory in the repository
