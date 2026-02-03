# Custom Agent Example: Database Migration Agent

This example demonstrates how to create a custom agent that handles database migrations following the PLAN → ACT → OBSERVE → REFLECT pattern.

## Overview

The `DatabaseMigrationAgent` automates database schema migrations with:
- Detection of pending migrations
- Automatic backup before changes
- Sequential migration application
- Verification of successful application
- Rollback recommendations on failure

## Files

- `DatabaseMigrationAgent.java` - Agent implementation
- `CustomAgentRegistrar.java` - Registration logic
- `DatabaseMigrationAgentTest.java` - Unit tests

## Installation

1. Copy the files to your backend project:
   ```bash
   cp -r examples/custom-agent/src/* backend/src/
   ```

2. Rebuild the backend:
   ```bash
   cd backend
   mvn clean install
   ```

3. Restart the backend:
   ```bash
   mvn spring-boot:run
   ```

4. The agent will be automatically registered and available for use

## Usage

The agent is invoked automatically when the `migrate` intent is used:

```bash
sdlc migrate database
```

## Customization

To adapt this example for your needs:

1. Modify the migration detection logic in `detectPendingMigrations()`
2. Customize the backup strategy in `createBackup()`
3. Adjust the verification logic in `verifyMigrations()`
4. Add custom error handling and recovery strategies

## See Also

- [Developer Guide](../../docs/developer-guide.md)
- [Agent Architecture](../../docs/architecture.md#agent-framework)
