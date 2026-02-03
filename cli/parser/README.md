# Parser Package

The parser package provides command parsing and validation for the SDLCraft CLI. It extracts structured information from raw user input and validates it against the expected grammar rules.

## Overview

The parser supports two types of input:

1. **Structured Commands**: Follow the pattern `sdlc <intent> <target> [modifiers]`
2. **Natural Language**: Free-form text that will be sent to the backend for intent inference

## Core Types

### Command

The `Command` struct represents a parsed CLI command with structured fields:

```go
type Command struct {
    ID          string            // Unique identifier
    Raw         string            // Original user input
    Intent      string            // High-level goal (status, analyze, improve, etc.)
    Target      string            // Specific area or component
    Modifiers   map[string]string // Additional parameters
    IsValid     bool              // Whether the command passed validation
    Timestamp   time.Time         // When the command was created
    UserID      string            // User who issued the command (optional)
    ProjectPath string            // Project path (optional)
}
```

### Parser Interface

The `Parser` interface defines the contract for parsing and validating commands:

```go
type Parser interface {
    Parse(input string) (*Command, error)
    ValidateGrammar(cmd *Command) error
}
```

## Usage

### Basic Parsing

```go
import "github.com/sdlcraft/cli/parser"

// Create a parser
p := parser.NewDefaultParser()

// Parse a command
cmd, err := p.Parse("sdlc status project")
if err != nil {
    // Handle error
}

// Check if command is valid
if cmd.IsValid {
    fmt.Printf("Intent: %s, Target: %s\n", cmd.Intent, cmd.Target)
}
```

### Supported Intents

The parser recognizes these core intents:
- `status` - Display current SDLC state
- `analyze` - Perform analysis (requires target)
- `improve` - Suggest improvements (requires target)
- `test` - Run tests
- `debug` - Debug issues
- `prepare` - Prepare for deployment
- `release` - Release to production

### Modifier Formats

The parser supports various modifier formats:

```bash
# Double dash with equals
sdlc status --verbose=true

# Double dash with space
sdlc status --format json

# Single dash with space
sdlc status -f json

# Boolean flags
sdlc status --verbose

# Multiple modifiers
sdlc analyze security --verbose=true --format json -e production
```

### Grammar Validation

Commands are validated according to these rules:

1. **Intent is required**: Every command must have an intent
2. **Intent must be valid**: Only recognized intents are accepted
3. **Some intents require targets**: `analyze` and `improve` require a target
4. **Case insensitive**: Intents and targets are converted to lowercase

### Error Handling

The parser returns specific errors for different failure cases:

```go
// Empty input
cmd, err := p.Parse("")
// err == parser.ErrEmptyInput

// Missing intent
cmd := &parser.Command{Intent: ""}
err := p.ValidateGrammar(cmd)
// err == parser.ErrMissingIntent

// Invalid intent
cmd := &parser.Command{Intent: "invalid"}
err := p.ValidateGrammar(cmd)
// err wraps parser.ErrInvalidGrammar
```

### Natural Language Detection

If the input doesn't match the structured grammar, it's treated as natural language:

```go
cmd, err := p.Parse("what is the status of my project?")
// err == nil
// cmd.IsValid == false (will be sent to backend for inference)
// cmd.Raw == "what is the status of my project?"
```

## Examples

### Example 1: Simple Status Command

```go
cmd, _ := p.Parse("sdlc status")
// cmd.Intent = "status"
// cmd.Target = ""
// cmd.IsValid = true
```

### Example 2: Analyze with Target

```go
cmd, _ := p.Parse("sdlc analyze security")
// cmd.Intent = "analyze"
// cmd.Target = "security"
// cmd.IsValid = true
```

### Example 3: Command with Modifiers

```go
cmd, _ := p.Parse("sdlc status project --verbose --format=json")
// cmd.Intent = "status"
// cmd.Target = "project"
// cmd.Modifiers = {"verbose": "true", "format": "json"}
// cmd.IsValid = true
```

### Example 4: Natural Language

```go
cmd, _ := p.Parse("check the security of the application")
// cmd.IsValid = false (needs backend inference)
// cmd.Raw = "check the security of the application"
```

## Design Decisions

### Why Two Input Types?

Supporting both structured and natural language input provides flexibility:
- **Structured**: Fast, deterministic, works offline
- **Natural Language**: User-friendly, requires backend

### Why Case Insensitive?

Users shouldn't have to remember exact casing. Converting to lowercase makes the CLI more forgiving.

### Why Preserve Raw Input?

The raw input is preserved for:
1. Error messages and suggestions
2. Sending to backend for intent inference
3. Audit logging

### Why Separate Parse and Validate?

Separating parsing from validation allows:
1. Parsing natural language without validation errors
2. Testing parsing logic independently
3. Custom validation rules for different contexts

## Testing

The parser package includes comprehensive tests:

```bash
# Run all tests
go test ./parser -v

# Run specific test
go test ./parser -run TestParseStructuredCommand -v

# Run with coverage
go test ./parser -cover
```

Test coverage includes:
- Structured command parsing
- Modifier parsing (all formats)
- Grammar validation
- Empty input handling
- Natural language detection
- Edge cases (long input, special characters, unicode)
- Case insensitivity

## Future Enhancements

Potential improvements for future versions:

1. **Custom Intent Registration**: Allow plugins to register new intents
2. **Intent Aliases**: Support synonyms (e.g., "check" → "status")
3. **Validation Rules**: Configurable validation rules per intent
4. **Autocomplete**: Generate shell completion from valid intents
5. **Help Generation**: Auto-generate help text from intent definitions

## Related Components

- **Repair Engine** (`cli/repair`): Fixes typos and malformed commands
- **Backend Client** (`cli/client`): Sends natural language to backend for inference
- **Output Renderer** (`cli/renderer`): Displays parsed commands and results
