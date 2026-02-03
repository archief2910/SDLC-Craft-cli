# LLM Provider Example: OpenAI Integration

This example demonstrates how to integrate OpenAI's GPT models as an LLM provider for SDLCraft.

## Overview

The `OpenAIProvider` implements the `LLMProvider` interface to:
- Generate completions for intent inference
- Create embeddings for semantic search
- Handle API errors gracefully
- Support configurable models and parameters

## Files

- `OpenAIProvider.java` - OpenAI provider implementation
- `LLMProviderConfig.java` - Configuration class
- `OpenAIProviderTest.java` - Unit tests
- `application.yml` - Configuration example

## Prerequisites

- OpenAI API key (get one at https://platform.openai.com/)
- Set environment variable: `export OPENAI_API_KEY=your-key-here`

## Installation

1. Copy the files to your backend project:
   ```bash
   cp -r examples/llm-provider/src/* backend/src/
   ```

2. Update `application.yml`:
   ```yaml
   sdlcraft:
     llm:
       provider: openai
       openai:
         api-key: ${OPENAI_API_KEY}
         model: gpt-4
         timeout: 30s
   ```

3. Rebuild and restart:
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```

## Usage

The provider is automatically used for intent inference:

```bash
sdlc what's the security status of my project?
# Uses OpenAI to interpret natural language
```

## Customization

- Change model: Set `sdlcraft.llm.openai.model` to `gpt-3.5-turbo`, `gpt-4`, etc.
- Adjust temperature: Modify the `temperature` parameter in `complete()`
- Add retry logic: Implement exponential backoff for API failures
- Cache responses: Add caching layer to reduce API calls

## Other Providers

See also:
- `AnthropicProvider.java` - Claude integration
- `LocalLLMProvider.java` - Local model integration (Ollama, LM Studio)

## See Also

- [Developer Guide](../../docs/developer-guide.md)
- [OpenAI API Documentation](https://platform.openai.com/docs/api-reference)
