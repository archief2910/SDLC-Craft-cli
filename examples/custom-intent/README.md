# Custom Intent Example: Deploy

This example demonstrates how to create a custom intent for deploying applications to different environments.

## Overview

The `deploy` intent allows users to deploy applications with commands like:
- `sdlc deploy staging`
- `sdlc deploy production --version=1.2.3`
- `deploy my app to staging`

## Files

- `DeployIntentDefinition.java` - Intent definition
- `DeployIntentHandler.java` - Intent handler implementation
- `CustomIntentRegistrar.java` - Registration logic
- `DeployIntentHandlerTest.java` - Unit tests

## Installation

1. Copy the files to your backend project:
   ```bash
   cp -r examples/custom-intent/src/* backend/src/
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

4. Test the new intent:
   ```bash
   sdlc deploy staging
   ```

## Customization

To adapt this example for your needs:

1. Change the intent name in `DeployIntentDefinition.java`
2. Modify the handler logic in `DeployIntentHandler.java`
3. Update the risk level if needed
4. Add custom validation logic

## See Also

- [Developer Guide](../../docs/developer-guide.md)
- [API Reference](../../docs/api-reference.md)
