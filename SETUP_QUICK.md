# Quick Setup Guide for SDLCraft RAG System

## Prerequisites

1. **Java 21+** - Required for the backend
2. **Go 1.21+** - Required for the CLI
3. **API Keys**:
   - OpenRouter API Key (for LLM): https://openrouter.ai/
   - Pinecone API Key (for vector store): https://www.pinecone.io/

## Step 1: Configure Environment Variables

### Windows (PowerShell)
```powershell
# Set JAVA_HOME to your Java 21+ installation
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"  # Adjust path

# Set API Keys
$env:OPENROUTER_API_KEY = "your-openrouter-api-key"
$env:PINECONE_API_KEY = "your-pinecone-api-key"
$env:PINECONE_ENVIRONMENT = "us-east-1"
$env:PINECONE_INDEX = "sdlcraft-index"
```

### Linux/macOS
```bash
export JAVA_HOME=/path/to/jdk-21
export OPENROUTER_API_KEY="your-openrouter-api-key"
export PINECONE_API_KEY="your-pinecone-api-key"
export PINECONE_ENVIRONMENT="us-east-1"
export PINECONE_INDEX="sdlcraft-index"
```

## Step 2: Start the Backend

```bash
cd backend
mvn spring-boot:run
```

Wait until you see "Started BackendApplication".

## Step 3: Build and Use the CLI

```bash
cd cli
go build -o sdlc.exe .
```

## Step 4: Index Your Codebase

```bash
./sdlc index
```

## Step 5: Use AI Commands!

```bash
# Ask questions about code
./sdlc ai "explain how the authentication works"

# Request code changes
./sdlc ai "add error handling to the API endpoints"

# Apply changes automatically
./sdlc ai --apply "add logging to all controllers"

# Get command suggestions
./sdlc suggest "analize security"

# Execute standard SDLC commands
./sdlc exec "analyze security"
```

## Available Commands

| Command | Description |
|---------|-------------|
| `sdlc ai "prompt"` | Natural language AI command |
| `sdlc ai --apply "prompt"` | AI command with auto-apply |
| `sdlc ai --dry-run "prompt"` | Preview changes without applying |
| `sdlc ai --interactive "prompt"` | Review each change |
| `sdlc ai --file path "prompt"` | Focus on specific file |
| `sdlc index` | Index codebase for RAG |
| `sdlc suggest "text"` | Get command suggestions |
| `sdlc exec "command"` | Execute SDLC command |
| `sdlc status` | Show project status |

## Troubleshooting

### "Backend not available"
- Make sure backend is running: `cd backend && mvn spring-boot:run`
- Check if port 8080 is available

### "Java version not supported"
- Set JAVA_HOME to Java 21 or higher
- Verify with: `java -version`

### "LLM/Vector store not available"  
- Check API keys are set correctly
- Verify network connectivity

## Example Session

```bash
# Start backend (terminal 1)
cd backend
$env:OPENROUTER_API_KEY = "sk-..."
$env:PINECONE_API_KEY = "..."
mvn spring-boot:run

# Use CLI (terminal 2)
cd cli
./sdlc index
./sdlc ai "add input validation to user registration"
./sdlc ai --apply "refactor database queries for connection pooling"
```




