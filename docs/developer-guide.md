# SDLCraft Developer Guide

## Table of Contents

1. [Extension Points Overview](#extension-points-overview)
2. [Creating Custom Intents](#creating-custom-intents)
3. [Implementing Custom Agents](#implementing-custom-agents)
4. [Creating LLM Provider Implementations](#creating-llm-provider-implementations)
5. [Implementing Vector Store Backends](#implementing-vector-store-backends)
6. [Plugin Development](#plugin-development)
7. [Testing Extensions](#testing-extensions)
8. [Best Practices](#best-practices)

---

## Extension Points Overview

SDLCraft is designed to be extensible at multiple levels:

```
┌─────────────────────────────────────────────────────────┐
│                    Extension Points                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. Custom Intents                                       │
│     └─ Add new high-level commands                      │
│                                                          │
│  2. Custom Agents                                        │
│     └─ Implement specialized execution logic            │
│                                                          │
│  3. LLM Providers                                        │
│     └─ Integrate different AI models                    │
│                                                          │
│  4. Vector Stores                                        │
│     └─ Use different embedding databases                │
│                                                          │
│  5. Policy Rules                                         │
│     └─ Define custom safety policies                    │
│                                                          │
│  6. CLI Plugins                                          │
│     └─ Extend CLI functionality                         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

All extension points use interface-based design, allowing you to plug in custom implementations without modifying core code.

---

## Creating Custom Intents

### Overview

Custom intents allow you to add new high-level commands to SDLCraft. An intent represents a developer goal (like "deploy", "backup", "migrate").

### Step 1: Define the Intent

Create an intent definition that describes your new command:

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.intent.IntentDefinition;
import com.sdlcraft.backend.sdlc.RiskLevel;
import java.util.List;

public class DeployIntentDefinition {
    
    public static IntentDefinition create() {
        IntentDefinition definition = new IntentDefinition();
        definition.setName("deploy");
        definition.setDescription("Deploy application to specified environment");
        definition.setRequiredParameters(List.of("environment"));
        definition.setOptionalParameters(List.of("version", "rollback"));
        definition.setExamples(List.of(
            "sdlc deploy staging",
            "sdlc deploy production --version=1.2.3",
            "deploy to staging with rollback enabled"
        ));
        definition.setDefaultRiskLevel(RiskLevel.HIGH);
        return definition;
    }
}
```

### Step 2: Create the Intent Handler

Implement the `IntentHandler` interface to define what happens when your intent is executed:

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.handler.IntentHandler;
import com.sdlcraft.backend.intent.IntentRequest;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.agent.AgentOrchestrator;
import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.ExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class DeployIntentHandler implements IntentHandler {
    
    private final AgentOrchestrator orchestrator;
    
    public DeployIntentHandler(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
    
    @Override
    public String getIntentName() {
        return "deploy";
    }
    
    @Override
    public ExecutionResult handle(IntentRequest request, IntentResult intent) {
        // Extract parameters
        String environment = intent.getTarget();
        String version = intent.getModifiers().getOrDefault("version", "latest");
        boolean rollback = Boolean.parseBoolean(
            intent.getModifiers().getOrDefault("rollback", "false")
        );
        
        // Create agent context
        AgentContext context = AgentContext.builder()
            .executionId(generateExecutionId())
            .intent(intent.getIntent())
            .parameters(Map.of(
                "environment", environment,
                "version", version,
                "rollback", rollback
            ))
            .build();
        
        // Execute through agent orchestrator
        return orchestrator.execute(context);
    }
    
    @Override
    public boolean supports(String intent) {
        return "deploy".equals(intent);
    }
    
    private String generateExecutionId() {
        return "exec-" + UUID.randomUUID().toString();
    }
}
```

### Step 3: Register the Intent

Register your intent with the system on startup:

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.intent.IntentInferenceService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CustomIntentRegistrar implements CommandLineRunner {
    
    private final IntentInferenceService intentService;
    
    public CustomIntentRegistrar(IntentInferenceService intentService) {
        this.intentService = intentService;
    }
    
    @Override
    public void run(String... args) {
        // Register the deploy intent
        intentService.registerIntent(DeployIntentDefinition.create());
        
        System.out.println("✓ Registered custom intent: deploy");
    }
}
```

### Step 4: Test Your Intent

Create tests for your custom intent:

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.intent.IntentRequest;
import com.sdlcraft.backend.intent.IntentResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeployIntentHandlerTest {
    
    @Autowired
    private DeployIntentHandler handler;
    
    @Test
    void shouldHandleDeployIntent() {
        IntentRequest request = new IntentRequest();
        request.setRawCommand("sdlc deploy staging");
        
        IntentResult intent = new IntentResult();
        intent.setIntent("deploy");
        intent.setTarget("staging");
        
        ExecutionResult result = handler.handle(request, intent);
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    }
}
```

### Complete Example

See `examples/custom-intent/` for a complete working example.

---

## Implementing Custom Agents

### Overview

Custom agents implement specialized execution logic following the PLAN → ACT → OBSERVE → REFLECT pattern.

### Step 1: Implement the Agent Interface

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.agent.Agent;
import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.agent.AgentPhase;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationAgent implements Agent {
    
    @Override
    public String getType() {
        return "DatabaseMigrationAgent";
    }
    
    @Override
    public AgentResult plan(AgentContext context) {
        // Analyze what migrations need to run
        List<String> pendingMigrations = detectPendingMigrations(context);
        
        Map<String, Object> plan = Map.of(
            "migrations", pendingMigrations,
            "estimatedDuration", estimateDuration(pendingMigrations),
            "backupRequired", true
        );
        
        return AgentResult.builder()
            .agentType(getType())
            .phase(AgentPhase.PLAN)
            .status(AgentStatus.SUCCESS)
            .result(plan)
            .reasoning("Identified " + pendingMigrations.size() + 
                      " pending migrations that need to be applied")
            .build();
    }
    
    @Override
    public AgentResult act(AgentContext context, Map<String, Object> plan) {
        // Execute the migrations
        List<String> migrations = (List<String>) plan.get("migrations");
        boolean backupRequired = (Boolean) plan.get("backupRequired");
        
        if (backupRequired) {
            createBackup(context);
        }
        
        List<String> applied = new ArrayList<>();
        for (String migration : migrations) {
            try {
                applyMigration(migration);
                applied.add(migration);
            } catch (Exception e) {
                return AgentResult.builder()
                    .agentType(getType())
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILED)
                    .error(e.getMessage())
                    .result(Map.of("appliedMigrations", applied))
                    .build();
            }
        }
        
        return AgentResult.builder()
            .agentType(getType())
            .phase(AgentPhase.ACT)
            .status(AgentStatus.SUCCESS)
            .result(Map.of("appliedMigrations", applied))
            .reasoning("Successfully applied " + applied.size() + " migrations")
            .build();
    }
    
    @Override
    public AgentResult observe(AgentContext context, Map<String, Object> actionResult) {
        // Verify migrations were applied correctly
        List<String> applied = (List<String>) actionResult.get("appliedMigrations");
        
        boolean allVerified = verifyMigrations(applied);
        
        return AgentResult.builder()
            .agentType(getType())
            .phase(AgentPhase.OBSERVE)
            .status(allVerified ? AgentStatus.SUCCESS : AgentStatus.FAILED)
            .result(Map.of("verified", allVerified))
            .reasoning(allVerified ? 
                "All migrations verified successfully" : 
                "Some migrations failed verification")
            .build();
    }
    
    @Override
    public AgentResult reflect(AgentContext context, Map<String, Object> observation) {
        // Analyze the outcome and suggest improvements
        boolean verified = (Boolean) observation.get("verified");
        
        if (!verified) {
            return AgentResult.builder()
                .agentType(getType())
                .phase(AgentPhase.REFLECT)
                .status(AgentStatus.SUCCESS)
                .result(Map.of(
                    "recommendation", "ROLLBACK",
                    "reason", "Migrations failed verification"
                ))
                .reasoning("Recommend rolling back due to verification failure")
                .build();
        }
        
        return AgentResult.builder()
            .agentType(getType())
            .phase(AgentPhase.REFLECT)
            .status(AgentStatus.SUCCESS)
            .result(Map.of(
                "recommendation", "PROCEED",
                "nextSteps", List.of("Update schema documentation", "Notify team")
            ))
            .reasoning("Migrations successful, ready to proceed")
            .build();
    }
    
    // Helper methods
    private List<String> detectPendingMigrations(AgentContext context) {
        // Implementation
        return List.of("V1__add_users.sql", "V2__add_orders.sql");
    }
    
    private long estimateDuration(List<String> migrations) {
        return migrations.size() * 5000L; // 5 seconds per migration
    }
    
    private void createBackup(AgentContext context) {
        // Implementation
    }
    
    private void applyMigration(String migration) {
        // Implementation
    }
    
    private boolean verifyMigrations(List<String> migrations) {
        // Implementation
        return true;
    }
}
```

### Step 2: Register the Agent

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.agent.AgentOrchestrator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CustomAgentRegistrar implements CommandLineRunner {
    
    private final AgentOrchestrator orchestrator;
    private final DatabaseMigrationAgent migrationAgent;
    
    public CustomAgentRegistrar(
            AgentOrchestrator orchestrator,
            DatabaseMigrationAgent migrationAgent) {
        this.orchestrator = orchestrator;
        this.migrationAgent = migrationAgent;
    }
    
    @Override
    public void run(String... args) {
        orchestrator.registerAgent("DatabaseMigrationAgent", migrationAgent);
        System.out.println("✓ Registered custom agent: DatabaseMigrationAgent");
    }
}
```

### Step 3: Use the Agent in an Intent Handler

```java
@Override
public ExecutionResult handle(IntentRequest request, IntentResult intent) {
    AgentContext context = AgentContext.builder()
        .executionId(generateExecutionId())
        .intent("migrate")
        .parameters(Map.of("database", intent.getTarget()))
        .build();
    
    // The orchestrator will use your custom agent
    return orchestrator.execute(context);
}
```

### Complete Example

See `examples/custom-agent/` for a complete working example.

---

## Creating LLM Provider Implementations

### Overview

LLM providers integrate different AI models for intent inference and natural language processing.

### Step 1: Implement the LLMProvider Interface

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.llm.LLMException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class OpenAIProvider implements LLMProvider {
    
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    
    public OpenAIProvider(
            @Value("${sdlcraft.llm.openai.api-key}") String apiKey,
            @Value("${sdlcraft.llm.openai.model:gpt-4}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public String complete(String prompt, Map<String, Object> parameters) {
        try {
            // Build request
            Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                ),
                "temperature", parameters.getOrDefault("temperature", 0.7),
                "max_tokens", parameters.getOrDefault("max_tokens", 500)
            );
            
            // Call OpenAI API
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = 
                new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.openai.com/v1/chat/completions",
                entity,
                Map.class
            );
            
            // Extract completion
            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> choices = 
                (List<Map<String, Object>>) body.get("choices");
            Map<String, Object> message = 
                (Map<String, Object>) choices.get(0).get("message");
            
            return (String) message.get("content");
            
        } catch (Exception e) {
            throw new LLMException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Double> embed(List<String> texts) {
        try {
            // Build request
            Map<String, Object> request = Map.of(
                "model", "text-embedding-ada-002",
                "input", texts
            );
            
            // Call OpenAI API
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = 
                new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.openai.com/v1/embeddings",
                entity,
                Map.class
            );
            
            // Extract embeddings
            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> data = 
                (List<Map<String, Object>>) body.get("data");
            
            return data.stream()
                .map(item -> (List<Double>) item.get("embedding"))
                .flatMap(List::stream)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            throw new LLMException("OpenAI embedding call failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
}
```

### Step 2: Configure the Provider

Add configuration to `application.yml`:

```yaml
sdlcraft:
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
      timeout: 30s
```

### Step 3: Make it Selectable

Create a configuration class that selects the provider:

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.llm.LLMProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMProviderConfig {
    
    @Bean
    @ConditionalOnProperty(name = "sdlcraft.llm.provider", havingValue = "openai")
    public LLMProvider openAIProvider(
            @Value("${sdlcraft.llm.openai.api-key}") String apiKey,
            @Value("${sdlcraft.llm.openai.model}") String model) {
        return new OpenAIProvider(apiKey, model);
    }
    
    @Bean
    @ConditionalOnProperty(name = "sdlcraft.llm.provider", havingValue = "anthropic")
    public LLMProvider anthropicProvider(
            @Value("${sdlcraft.llm.anthropic.api-key}") String apiKey) {
        return new AnthropicProvider(apiKey);
    }
}
```

### Complete Example

See `examples/llm-provider/` for complete implementations of OpenAI, Anthropic, and local model providers.

---

## Implementing Vector Store Backends

### Overview

Vector stores provide semantic search capabilities for long-term memory.

### Step 1: Implement the VectorStore Interface

```java
package com.example.sdlcraft.custom;

import com.sdlcraft.backend.memory.VectorStore;
import com.sdlcraft.backend.memory.VectorSearchResult;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class PineconeVectorStore implements VectorStore {
    
    private final String apiKey;
    private final String environment;
    private final String indexName;
    private final RestTemplate restTemplate;
    
    public PineconeVectorStore(
            @Value("${sdlcraft.vector-store.pinecone.api-key}") String apiKey,
            @Value("${sdlcraft.vector-store.pinecone.environment}") String environment,
            @Value("${sdlcraft.vector-store.pinecone.index}") String indexName) {
        this.apiKey = apiKey;
        this.environment = environment;
        this.indexName = indexName;
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public void store(String id, List<Double> embedding, Map<String, Object> metadata) {
        try {
            String url = String.format(
                "https://%s-%s.svc.%s.pinecone.io/vectors/upsert",
                indexName, environment, environment
            );
            
            Map<String, Object> vector = Map.of(
                "id", id,
                "values", embedding,
                "metadata", metadata
            );
            
            Map<String, Object> request = Map.of(
                "vectors", List.of(vector)
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Api-Key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = 
                new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to store vector in Pinecone", e);
        }
    }
    
    @Override
    public List<VectorSearchResult> search(
            List<Double> queryEmbedding, 
            int limit, 
            Map<String, Object> filter) {
        try {
            String url = String.format(
                "https://%s-%s.svc.%s.pinecone.io/query",
                indexName, environment, environment
            );
            
            Map<String, Object> request = Map.of(
                "vector", queryEmbedding,
                "topK", limit,
                "includeMetadata", true,
                "filter", filter != null ? filter : Map.of()
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Api-Key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = 
                new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = 
                restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> matches = 
                (List<Map<String, Object>>) body.get("matches");
            
            return matches.stream()
                .map(match -> new VectorSearchResult(
                    (String) match.get("id"),
                    ((Number) match.get("score")).doubleValue(),
                    (Map<String, Object>) match.get("metadata")
                ))
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to search vectors in Pinecone", e);
        }
    }
    
    @Override
    public void delete(String id) {
        try {
            String url = String.format(
                "https://%s-%s.svc.%s.pinecone.io/vectors/delete",
                indexName, environment, environment
            );
            
            Map<String, Object> request = Map.of(
                "ids", List.of(id)
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Api-Key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = 
                new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete vector from Pinecone", e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "pinecone";
    }
}
```

### Step 2: Configure the Vector Store

```yaml
sdlcraft:
  vector-store:
    provider: pinecone
    pinecone:
      api-key: ${PINECONE_API_KEY}
      environment: us-west1-gcp
      index: sdlcraft-memory
```

### Complete Example

See `examples/vector-store/` for complete implementations of Pinecone, Weaviate, and Qdrant.

---

## Plugin Development

### Overview

Plugins extend CLI functionality with new commands or behaviors.

### Step 1: Create Plugin Structure

```
my-plugin/
├── plugin.yaml
├── commands/
│   └── my-command.go
└── README.md
```

### Step 2: Define Plugin Metadata

`plugin.yaml`:
```yaml
name: my-plugin
version: 1.0.0
description: My custom SDLCraft plugin
author: Your Name

commands:
  - name: custom
    description: My custom command
    handler: commands/my-command.go
```

### Step 3: Implement Command Handler

`commands/my-command.go`:
```go
package commands

import (
    "github.com/spf13/cobra"
    "github.com/your-org/sdlcraft/cli/client"
)

func NewCustomCommand(backendClient client.BackendClient) *cobra.Command {
    cmd := &cobra.Command{
        Use:   "custom",
        Short: "My custom command",
        Long:  "Detailed description of what this command does",
        RunE: func(cmd *cobra.Command, args []string) error {
            // Your command logic here
            return executeCustomCommand(backendClient, args)
        },
    }
    
    // Add flags
    cmd.Flags().StringP("option", "o", "", "Custom option")
    
    return cmd
}

func executeCustomCommand(client client.BackendClient, args []string) error {
    // Implementation
    return nil
}
```

### Step 4: Install Plugin

```bash
sdlc plugin install ./my-plugin
```

### Complete Example

See `examples/plugin/` for a complete plugin example.

---

## Testing Extensions

### Unit Testing Custom Intents

```java
@SpringBootTest
class CustomIntentTest {
    
    @Autowired
    private IntentInferenceService intentService;
    
    @Test
    void shouldRecognizeCustomIntent() {
        IntentRequest request = new IntentRequest();
        request.setRawCommand("sdlc deploy staging");
        
        IntentResult result = intentService.inferIntent(request);
        
        assertThat(result.getIntent()).isEqualTo("deploy");
        assertThat(result.getTarget()).isEqualTo("staging");
    }
}
```

### Integration Testing Custom Agents

```java
@SpringBootTest
class CustomAgentIntegrationTest {
    
    @Autowired
    private AgentOrchestrator orchestrator;
    
    @Test
    void shouldExecuteCustomAgent() {
        AgentContext context = AgentContext.builder()
            .executionId("test-exec")
            .intent("migrate")
            .parameters(Map.of("database", "test-db"))
            .build();
        
        ExecutionResult result = orchestrator.execute(context);
        
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    }
}
```

### Testing LLM Providers

```java
@SpringBootTest
class LLMProviderTest {
    
    @Autowired
    private LLMProvider llmProvider;
    
    @Test
    void shouldCompletePrompt() {
        String prompt = "What is the capital of France?";
        String response = llmProvider.complete(prompt, Map.of());
        
        assertThat(response).containsIgnoringCase("Paris");
    }
}
```

---

## Best Practices

### 1. Follow Interface Contracts

Always implement all methods defined in interfaces, even if some are no-ops for your use case.

### 2. Handle Errors Gracefully

```java
@Override
public AgentResult act(AgentContext context, Map<String, Object> plan) {
    try {
        // Your logic
    } catch (Exception e) {
        return AgentResult.builder()
            .status(AgentStatus.FAILED)
            .error(e.getMessage())
            .reasoning("Failed due to: " + e.getClass().getSimpleName())
            .build();
    }
}
```

### 3. Provide Clear Reasoning

Always include reasoning in agent results:

```java
return AgentResult.builder()
    .reasoning("Applied 3 migrations successfully. " +
              "Database schema is now at version 1.2.3")
    .build();
```

### 4. Use Dependency Injection

Let Spring manage your dependencies:

```java
@Component
public class MyAgent implements Agent {
    private final SomeService service;
    
    public MyAgent(SomeService service) {
        this.service = service;
    }
}
```

### 5. Document Your Extensions

Include comprehensive documentation:

```java
/**
 * DatabaseMigrationAgent handles database schema migrations.
 * 
 * This agent:
 * - Detects pending migrations
 * - Creates backups before applying changes
 * - Applies migrations in order
 * - Verifies successful application
 * - Recommends rollback if verification fails
 * 
 * @author Your Name
 * @since 1.0.0
 */
@Component
public class DatabaseMigrationAgent implements Agent {
    // ...
}
```

### 6. Test Thoroughly

Write tests for all extension points:
- Unit tests for individual methods
- Integration tests for full workflows
- Property-based tests for invariants

### 7. Version Your Extensions

Use semantic versioning for your extensions:
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes

### 8. Provide Examples

Include example usage in your documentation:

```java
/**
 * Example usage:
 * 
 * <pre>
 * sdlc migrate database --environment=staging
 * sdlc migrate database --environment=production --backup=true
 * </pre>
 */
```

---

## Additional Resources

- **Example Extensions**: See `examples/` directory
- **API Reference**: `docs/api-reference.md`
- **Architecture**: `docs/architecture.md`
- **Contributing**: `CONTRIBUTING.md`

---

## Getting Help

If you need help developing extensions:

1. **Check Examples**: Look at `examples/` for working code
2. **Read Tests**: Core tests show how to use interfaces
3. **Ask Questions**: Open a GitHub Discussion
4. **Report Issues**: File a GitHub Issue if you find bugs

Happy extending! 🚀
