# Renderer Package

The `renderer` package provides a clean interface for displaying CLI output with color-coded formatting, progress indicators, and user interaction prompts.

## Overview

The renderer abstracts all terminal output, making it easy to:
- Display results with appropriate formatting
- Show real-time progress during long operations
- Prompt users for confirmation on high-risk commands
- Present multiple options for user selection
- Support both interactive and non-interactive modes
- Enable verbose mode for detailed debugging

## Interface

```go
type Renderer interface {
    DisplayResult(result interface{})
    DisplayProgress(event ExecutionEvent)
    PromptConfirmation(message string, riskLevel string) (bool, error)
    DisplayError(err error)
    DisplayAutoCorrection(original, corrected, explanation string)
    DisplayOptions(options []string, prompt string) (int, error)
    SetVerbose(verbose bool)
    SetOutput(w io.Writer)
}
```

## Usage Examples

### Basic Result Display

```go
renderer := renderer.NewDefaultRenderer()

// Display string result
renderer.DisplayResult("Command executed successfully")

// Display map result
result := map[string]interface{}{
    "status": "ok",
    "count": 42,
}
renderer.DisplayResult(result)

// Display error
err := errors.New("connection failed")
renderer.DisplayError(err)
```

### Auto-Correction Display

```go
renderer.DisplayAutoCorrection(
    "sdlc stauts security",
    "sdlc status security",
    "Corrected typo 'stauts' to 'status'",
)
```

Output:
```
Command auto-corrected:
  Original:  sdlc stauts security
  Corrected: sdlc status security
  Reason: Corrected typo 'stauts' to 'status'
```

### Multiple Options Selection

```go
options := []string{
    "sdlc status security",
    "sdlc analyze security",
    "sdlc improve security",
}

selected, err := renderer.DisplayOptions(options, "Did you mean:")
if err != nil {
    log.Fatal(err)
}

fmt.Printf("User selected option %d\n", selected)
```

Output:
```
Did you mean:

  1. sdlc status security
  2. sdlc analyze security
  3. sdlc improve security

Select option (1-3): 
```

### Confirmation Prompts

```go
confirmed, err := renderer.PromptConfirmation(
    "This will delete all production data. Are you sure?",
    "HIGH",
)

if err != nil {
    log.Fatal(err)
}

if confirmed {
    // Execute high-risk operation
} else {
    fmt.Println("Operation cancelled")
}
```

Output:
```
⚠️  HIGH RISK OPERATION
This will delete all production data. Are you sure?

Proceed? (yes/no): 
```

### Progress Events

```go
// Progress event with percentage
event := ExecutionEvent{
    Type:      "progress",
    Message:   "Processing files",
    Progress:  50,
    Timestamp: time.Now(),
}
renderer.DisplayProgress(event)

// Status update
event = ExecutionEvent{
    Type:      "status",
    Message:   "Analyzing code quality",
    Timestamp: time.Now(),
}
renderer.DisplayProgress(event)

// Completion
event = ExecutionEvent{
    Type:      "completion",
    Message:   "Analysis complete",
    Timestamp: time.Now(),
}
renderer.DisplayProgress(event)
```

Output:
```
Processing files [████████████████████░░░░░░░░░░░░░░░░░░░░] 50%
[15:04:05] Analyzing code quality
[15:04:10] ✓ Analysis complete
```

### Verbose Mode

```go
renderer.SetVerbose(true)

// Agent reasoning events are only shown in verbose mode
event := ExecutionEvent{
    Type:      "agent_reasoning",
    Message:   "Planning execution steps based on project state",
    Timestamp: time.Now(),
}
renderer.DisplayProgress(event)
```

Output (verbose mode):
```
Verbose mode enabled
[15:04:05] [AGENT] Planning execution steps based on project state
```

### Spinner for Long Operations

```go
spinner := renderer.NewSpinner("Loading project data")
spinner.Start()

// Perform long operation
time.Sleep(5 * time.Second)

spinner.Stop()
renderer.DisplayResult("Project data loaded")
```

Output (animated):
```
⠋ Loading project data
⠙ Loading project data
⠹ Loading project data
...
Project data loaded
```

## Color Coding

The renderer uses consistent color coding:

- **Green**: Success messages, completed operations
- **Yellow**: Warnings, auto-corrections, confirmations
- **Red**: Errors, high-risk operations
- **Cyan**: Information, status updates, progress

## Interactive vs Non-Interactive Mode

The renderer automatically detects if the terminal is interactive:

**Interactive Mode** (terminal):
- Prompts for user input
- Shows confirmation dialogs
- Displays option selection menus
- Supports colored output

**Non-Interactive Mode** (CI/CD, pipes):
- Returns errors for prompts instead of blocking
- Skips confirmation dialogs (fails safely)
- Plain text output without colors
- Suitable for logging and automation

## Testing

The renderer is designed to be testable by allowing custom output writers:

```go
func TestMyCommand(t *testing.T) {
    buf := &bytes.Buffer{}
    renderer := renderer.NewDefaultRenderer()
    renderer.SetOutput(buf)
    
    // Execute command
    renderer.DisplayResult("test output")
    
    // Verify output
    output := buf.String()
    if !strings.Contains(output, "test output") {
        t.Error("Expected output not found")
    }
}
```

## Integration with Repair Engine

The renderer integrates seamlessly with the repair engine:

```go
// Parse and repair command
cmd, _ := parser.Parse("sdlc stauts security")
result, action, _ := engine.RepairWithDecision(cmd)

// Display based on action
switch action {
case "auto-correct":
    renderer.DisplayAutoCorrection(
        cmd.Raw,
        fmt.Sprintf("sdlc %s %s", result.Repaired.Intent, result.Repaired.Target),
        result.Explanation,
    )
    executeCommand(result.Repaired)

case "present-options":
    options := make([]string, len(result.Candidates))
    for i, candidate := range result.Candidates {
        options[i] = fmt.Sprintf("sdlc %s %s", candidate.Intent, candidate.Target)
    }
    selected, _ := renderer.DisplayOptions(options, "Did you mean:")
    executeCommand(result.Candidates[selected])

case "fail-to-backend":
    spinner := renderer.NewSpinner("Inferring intent")
    spinner.Start()
    response, _ := backendClient.InferIntent(cmd)
    spinner.Stop()
    renderer.DisplayResult(response)
}
```

## Event Types

The renderer supports these event types:

- `progress`: Progress updates with percentage (0-100)
- `status`: General status messages
- `error`: Error messages during execution
- `completion`: Successful completion messages
- `agent_reasoning`: Agent planning and reasoning (verbose only)
- Custom types: Displayed with default formatting

## Risk Levels

Confirmation prompts support these risk levels:

- `HIGH` / `CRITICAL`: Red warning, strong emphasis
- `MEDIUM`: Yellow warning, moderate emphasis
- `LOW`: Blue info, minimal emphasis

## Dependencies

- `github.com/fatih/color`: Cross-platform colored terminal output
- Standard library: `io`, `fmt`, `strings`, `time`

## Design Principles

1. **Separation of Concerns**: Renderer only handles display, not business logic
2. **Testability**: All output can be redirected to custom writers
3. **Flexibility**: Supports both interactive and non-interactive modes
4. **Consistency**: Uniform color coding and formatting across all output
5. **User Experience**: Clear, informative messages with appropriate visual cues

## Performance

- Minimal overhead: < 1ms per display operation
- Non-blocking: Progress events don't block execution
- Efficient: Reuses color objects, minimal allocations

## Future Enhancements

Potential improvements for future versions:

- Terminal width detection for responsive layouts
- Table formatting for structured data
- Markdown rendering for rich text
- Custom themes and color schemes
- Accessibility options (high contrast, no colors)
- Localization support for multiple languages
