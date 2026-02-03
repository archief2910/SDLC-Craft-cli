# SDLCraft RAG System

## Overview

SDLCraft includes a powerful RAG (Retrieval-Augmented Generation) system that enables intelligent, context-aware code understanding and modification through natural language prompts.

## Features

### 1. Codebase Indexing
- Automatically chunks source files into manageable pieces
- Generates vector embeddings using sentence transformers
- Stores embeddings in Pinecone vector database
- Supports Java, Go, and other languages

### 2. Semantic Code Search
- Find relevant code based on meaning, not just keywords
- Multi-file awareness for complex queries
- Context-aware retrieval for better LLM responses

### 3. AI-Powered Code Modifications
- Natural language descriptions of changes
- Automatic diff generation
- Multiple change application modes (auto, interactive, dry-run)
- Rollback capability

### 4. Command Correction
- Intelligent typo correction
- Synonym expansion
- Natural language fallback

## Setup

### 1. Environment Variables

Create a `.env` file in the project root (or set environment variables):

```bash
# OpenRouter API Key (for LLM)
OPENROUTER_API_KEY=your-openrouter-api-key

# Pinecone API Key (for Vector Store)
PINECONE_API_KEY=your-pinecone-api-key

# Pinecone Configuration
PINECONE_ENVIRONMENT=us-east-1
PINECONE_INDEX=sdlcraft-index
```

### 2. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

### 3. Index Your Codebase

```bash
sdlc index
```

## Usage

### Natural Language AI Commands

```bash
# Ask questions about the code
sdlc ai "explain how the authentication system works"

# Request code changes
sdlc ai "add error handling to all API endpoints"

# Refactor with context
sdlc ai "refactor the database queries to use connection pooling"

# Focus on specific file
sdlc ai --file src/main/java/App.java "optimize this file for performance"

# Apply changes automatically
sdlc ai --apply "add input validation to user registration"

# Dry run to preview changes
sdlc ai --dry-run "rename the UserService to AccountService"

# Interactive mode to review each change
sdlc ai --interactive "update all deprecated API calls"
```

### Command Correction

```bash
# Typo correction
sdlc suggest "analize security"
# Suggests: "analyze security"

# Natural language to command
sdlc suggest "how do I check test coverage"
# Suggests: "test coverage"

# Unknown commands auto-suggest
sdlc staus
# Shows: "Did you mean 'status'?"
```

## Architecture

### Components

1. **PineconeVectorStore** (`backend/memory/`)
   - Connects to Pinecone serverless vector database
   - Handles embedding storage and retrieval
   - Supports metadata filtering

2. **RAGService** (`backend/rag/`)
   - Orchestrates retrieval and generation
   - Builds context-aware prompts
   - Parses LLM responses into structured changes

3. **CodeChangeService** (`backend/rag/`)
   - Applies code modifications
   - Supports diff-based and full-file changes
   - Handles rollback

4. **AgenticRAGHandler** (`backend/handler/`)
   - Full agent orchestration (PLAN → ACT → OBSERVE → REFLECT)
   - Combines RAG with existing agent system

5. **CLI Commands** (`cli/cmd/`)
   - `ai.go` - Natural language command processing
   - `suggest.go` - Command correction and suggestions
   - `index.go` - Codebase indexing

### Flow

```
User Prompt
    ↓
CLI (ai command)
    ↓
Backend (RAGController)
    ↓
RAGService
    ├── CodebaseIndexer → VectorStore (Pinecone)
    │        ↓
    │   Retrieve relevant code chunks
    │        ↓
    └── LLMProvider (OpenRouter)
             ↓
         Generate suggestions
             ↓
    CodeChangeService
         ↓
    Apply changes (if --apply)
         ↓
    Return results to CLI
```

## API Endpoints

### POST /api/rag/query
Query the codebase with natural language.

```json
{
  "query": "add logging to all controllers",
  "projectPath": ".",
  "focusFile": null
}
```

### POST /api/rag/apply
Apply suggested code changes.

```json
{
  "changes": [...],
  "projectPath": ".",
  "dryRun": false
}
```

### POST /api/rag/rollback
Rollback the last set of changes.

### POST /api/rag/index
Re-index the codebase.

### GET /api/rag/status
Check RAG service availability.

### POST /api/intent/suggest
Get command suggestions.

```json
{
  "input": "analize security",
  "projectPath": "."
}
```

## Configuration

### application.yml

```yaml
sdlcraft:
  llm:
    openrouter:
      api-key: ${OPENROUTER_API_KEY:}
      model: mistralai/devstral-2512:free
  
  vector-store:
    pinecone:
      api-key: ${PINECONE_API_KEY:}
      environment: ${PINECONE_ENVIRONMENT:us-east-1}
      index: ${PINECONE_INDEX:sdlcraft-index}
```

## Best Practices

1. **Index First**: Always run `sdlc index` after major code changes
2. **Use Focus Files**: For targeted changes, use `--file` flag
3. **Preview First**: Use `--dry-run` before applying changes
4. **Review in Interactive**: Use `--interactive` for complex changes
5. **Clear Prompts**: Be specific in your natural language requests

## Troubleshooting

### "Backend not available"
- Ensure the backend is running: `cd backend && mvn spring-boot:run`
- Check BACKEND_URL environment variable

### "Vector store not available"
- Check PINECONE_API_KEY is set correctly
- Verify Pinecone index exists
- Check network connectivity to Pinecone

### "LLM not available"
- Check OPENROUTER_API_KEY is set correctly
- Verify API key has sufficient credits

### Poor search results
- Re-index the codebase: `sdlc index`
- Try more specific queries
- Use `--file` flag for focused searches




