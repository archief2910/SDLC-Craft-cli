package renderer

import (
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	"github.com/fatih/color"
)

// ExecutionEvent represents a single event during command execution.
// Events are streamed from the backend to provide real-time feedback.
type ExecutionEvent struct {
	// Type indicates the event type (progress, status, error, completion)
	Type string

	// Message is the human-readable event message
	Message string

	// Timestamp when the event occurred
	Timestamp time.Time

	// Progress indicates completion percentage (0-100) for progress events
	Progress int

	// Metadata contains additional event-specific data
	Metadata map[string]interface{}
}

// Renderer defines the interface for displaying CLI output to users.
// It handles result display, progress indicators, confirmation prompts, and errors.
// The renderer abstracts output formatting to support different output modes
// (interactive terminal, non-interactive CI/CD, verbose debugging).
type Renderer interface {
	// DisplayResult displays the final result of a command execution.
	// The result can be any type (string, struct, map) and will be formatted appropriately.
	DisplayResult(result interface{})

	// DisplayProgress displays a progress event during long-running operations.
	// Shows real-time updates as the backend processes the command.
	DisplayProgress(event ExecutionEvent)

	// PromptConfirmation displays a confirmation prompt for high-risk commands.
	// Returns true if user confirms, false if user declines.
	// The riskLevel parameter determines the visual styling (HIGH, MEDIUM, LOW).
	PromptConfirmation(message string, riskLevel string) (bool, error)

	// DisplayError displays an error message with appropriate formatting.
	// Errors are shown in red with additional context when available.
	DisplayError(err error)

	// DisplayAutoCorrection displays an auto-corrected command with explanation.
	// Shows the original command and the corrected version.
	DisplayAutoCorrection(original, corrected, explanation string)

	// DisplayOptions displays multiple command options for user selection.
	// Returns the index of the selected option (0-based).
	DisplayOptions(options []string, prompt string) (int, error)

	// SetVerbose enables or disables verbose output mode.
	// In verbose mode, additional details like agent reasoning are displayed.
	SetVerbose(verbose bool)

	// SetOutput sets the output writer (useful for testing).
	// Defaults to os.Stdout if not set.
	SetOutput(w io.Writer)
}

// DefaultRenderer implements the Renderer interface with color-coded terminal output.
// It uses the fatih/color package for cross-platform color support.
type DefaultRenderer struct {
	// output is the writer for all output (defaults to os.Stdout)
	output io.Writer

	// verbose indicates whether to show detailed output
	verbose bool

	// interactive indicates whether the terminal is interactive (supports prompts)
	interactive bool

	// colors for different output types
	successColor *color.Color
	warningColor *color.Color
	errorColor   *color.Color
	infoColor    *color.Color
	promptColor  *color.Color
}

// NewDefaultRenderer creates a new renderer with default settings.
// Output goes to os.Stdout, interactive mode is auto-detected.
func NewDefaultRenderer() *DefaultRenderer {
	return &DefaultRenderer{
		output:       os.Stdout,
		verbose:      false,
		interactive:  isTerminal(os.Stdout),
		successColor: color.New(color.FgGreen),
		warningColor: color.New(color.FgYellow),
		errorColor:   color.New(color.FgRed, color.Bold),
		infoColor:    color.New(color.FgCyan),
		promptColor:  color.New(color.FgYellow, color.Bold),
	}
}

// DisplayResult implements the Renderer interface.
// Formats and displays the final result of a command execution.
func (r *DefaultRenderer) DisplayResult(result interface{}) {
	if result == nil {
		return
	}

	// Format based on result type
	switch v := result.(type) {
	case string:
		r.successColor.Fprintln(r.output, v)
	case error:
		r.DisplayError(v)
	case map[string]interface{}:
		r.displayMap(v, 0)
	default:
		// Use fmt.Sprintf for other types
		r.successColor.Fprintf(r.output, "%v\n", v)
	}
}

// DisplayProgress implements the Renderer interface.
// Shows real-time progress updates during command execution.
func (r *DefaultRenderer) DisplayProgress(event ExecutionEvent) {
	timestamp := event.Timestamp.Format("15:04:05")

	switch event.Type {
	case "progress":
		// Show progress bar for progress events
		if event.Progress >= 0 && event.Progress <= 100 {
			r.displayProgressBar(event.Message, event.Progress)
		} else {
			r.infoColor.Fprintf(r.output, "[%s] %s\n", timestamp, event.Message)
		}

	case "status":
		// Show status updates in cyan
		r.infoColor.Fprintf(r.output, "[%s] %s\n", timestamp, event.Message)

	case "error":
		// Show errors in red
		r.errorColor.Fprintf(r.output, "[%s] ERROR: %s\n", timestamp, event.Message)

	case "completion":
		// Show completion in green
		r.successColor.Fprintf(r.output, "[%s] ✓ %s\n", timestamp, event.Message)

	case "agent_reasoning":
		// Show agent reasoning only in verbose mode
		if r.verbose {
			r.infoColor.Fprintf(r.output, "[%s] [AGENT] %s\n", timestamp, event.Message)
		}

	default:
		// Default: show message without special formatting
		fmt.Fprintf(r.output, "[%s] %s\n", timestamp, event.Message)
	}
}

// PromptConfirmation implements the Renderer interface.
// Displays a confirmation prompt and waits for user input.
func (r *DefaultRenderer) PromptConfirmation(message string, riskLevel string) (bool, error) {
	// In non-interactive mode, always return false (don't execute)
	if !r.interactive {
		r.warningColor.Fprintln(r.output, "Non-interactive mode: confirmation required but not available")
		return false, fmt.Errorf("confirmation required but terminal is non-interactive")
	}

	// Display risk level with appropriate color
	switch strings.ToUpper(riskLevel) {
	case "HIGH", "CRITICAL":
		r.errorColor.Fprintln(r.output, "⚠️  HIGH RISK OPERATION")
	case "MEDIUM":
		r.warningColor.Fprintln(r.output, "⚠️  MEDIUM RISK OPERATION")
	default:
		r.infoColor.Fprintln(r.output, "ℹ️  CONFIRMATION REQUIRED")
	}

	// Display the confirmation message
	r.promptColor.Fprintln(r.output, message)
	r.promptColor.Fprint(r.output, "\nProceed? (yes/no): ")

	// Read user input
	var response string
	_, err := fmt.Fscanln(os.Stdin, &response)
	if err != nil {
		return false, fmt.Errorf("failed to read confirmation: %w", err)
	}

	// Parse response (accept yes, y, true, 1)
	response = strings.ToLower(strings.TrimSpace(response))
	confirmed := response == "yes" || response == "y" || response == "true" || response == "1"

	if confirmed {
		r.successColor.Fprintln(r.output, "✓ Confirmed")
	} else {
		r.warningColor.Fprintln(r.output, "✗ Cancelled")
	}

	return confirmed, nil
}

// DisplayError implements the Renderer interface.
// Shows error messages in red with additional context.
func (r *DefaultRenderer) DisplayError(err error) {
	if err == nil {
		return
	}

	r.errorColor.Fprintf(r.output, "✗ Error: %s\n", err.Error())

	// In verbose mode, show additional error details if available
	if r.verbose {
		r.errorColor.Fprintf(r.output, "  Details: %+v\n", err)
	}
}

// DisplayAutoCorrection implements the Renderer interface.
// Shows the original command and the auto-corrected version with explanation.
func (r *DefaultRenderer) DisplayAutoCorrection(original, corrected, explanation string) {
	r.warningColor.Fprintln(r.output, "Command auto-corrected:")
	r.errorColor.Fprintf(r.output, "  Original:  %s\n", original)
	r.successColor.Fprintf(r.output, "  Corrected: %s\n", corrected)
	if explanation != "" {
		r.infoColor.Fprintf(r.output, "  Reason: %s\n", explanation)
	}
	fmt.Fprintln(r.output)
}

// DisplayOptions implements the Renderer interface.
// Shows multiple options and prompts user to select one.
func (r *DefaultRenderer) DisplayOptions(options []string, prompt string) (int, error) {
	// In non-interactive mode, return error
	if !r.interactive {
		return -1, fmt.Errorf("option selection required but terminal is non-interactive")
	}

	// Display prompt
	r.promptColor.Fprintln(r.output, prompt)
	fmt.Fprintln(r.output)

	// Display options with numbers
	for i, option := range options {
		r.infoColor.Fprintf(r.output, "  %d. %s\n", i+1, option)
	}
	fmt.Fprintln(r.output)

	// Prompt for selection
	r.promptColor.Fprintf(r.output, "Select option (1-%d): ", len(options))

	// Read user input
	var selection int
	_, err := fmt.Fscanln(os.Stdin, &selection)
	if err != nil {
		return -1, fmt.Errorf("failed to read selection: %w", err)
	}

	// Validate selection
	if selection < 1 || selection > len(options) {
		return -1, fmt.Errorf("invalid selection: must be between 1 and %d", len(options))
	}

	// Return 0-based index
	return selection - 1, nil
}

// SetVerbose implements the Renderer interface.
// Enables or disables verbose output mode.
func (r *DefaultRenderer) SetVerbose(verbose bool) {
	r.verbose = verbose
	if verbose {
		r.infoColor.Fprintln(r.output, "Verbose mode enabled")
	}
}

// SetOutput implements the Renderer interface.
// Sets the output writer (useful for testing).
func (r *DefaultRenderer) SetOutput(w io.Writer) {
	r.output = w
	// Update interactive mode based on new output
	if f, ok := w.(*os.File); ok {
		r.interactive = isTerminal(f)
	} else {
		r.interactive = false
	}
}

// displayMap displays a map with indentation for nested structures.
func (r *DefaultRenderer) displayMap(m map[string]interface{}, indent int) {
	indentStr := strings.Repeat("  ", indent)

	for key, value := range m {
		switch v := value.(type) {
		case map[string]interface{}:
			r.infoColor.Fprintf(r.output, "%s%s:\n", indentStr, key)
			r.displayMap(v, indent+1)
		case []interface{}:
			r.infoColor.Fprintf(r.output, "%s%s:\n", indentStr, key)
			for i, item := range v {
				fmt.Fprintf(r.output, "%s  [%d] %v\n", indentStr, i, item)
			}
		default:
			r.infoColor.Fprintf(r.output, "%s%s: ", indentStr, key)
			fmt.Fprintf(r.output, "%v\n", v)
		}
	}
}

// displayProgressBar displays a progress bar with percentage.
func (r *DefaultRenderer) displayProgressBar(message string, progress int) {
	// Ensure progress is in valid range
	if progress < 0 {
		progress = 0
	}
	if progress > 100 {
		progress = 100
	}

	// Calculate bar width (40 characters)
	barWidth := 40
	filled := (progress * barWidth) / 100
	empty := barWidth - filled

	// Build progress bar
	bar := strings.Repeat("█", filled) + strings.Repeat("░", empty)

	// Display with percentage
	r.infoColor.Fprintf(r.output, "\r%s [%s] %d%%", message, bar, progress)

	// Add newline when complete
	if progress == 100 {
		fmt.Fprintln(r.output)
	}
}

// isTerminal checks if the given file is a terminal.
// This is used to determine if interactive prompts are supported.
func isTerminal(f *os.File) bool {
	// Check if file is a terminal by checking if it's stdout/stderr/stdin
	// and if it has a file descriptor
	if f == os.Stdout || f == os.Stderr || f == os.Stdin {
		// On Windows, check if it's a console
		// On Unix, check if it's a TTY
		// For now, we'll use a simple heuristic: if it's one of the standard streams, assume interactive
		return true
	}
	return false
}

// Spinner represents a simple text-based spinner for long-running operations.
type Spinner struct {
	renderer *DefaultRenderer
	message  string
	frames   []string
	current  int
	active   bool
}

// NewSpinner creates a new spinner with the given message.
func (r *DefaultRenderer) NewSpinner(message string) *Spinner {
	return &Spinner{
		renderer: r,
		message:  message,
		frames:   []string{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"},
		current:  0,
		active:   false,
	}
}

// Start starts the spinner animation.
func (s *Spinner) Start() {
	s.active = true
	go s.animate()
}

// Stop stops the spinner animation and clears the line.
func (s *Spinner) Stop() {
	s.active = false
	// Clear the spinner line
	fmt.Fprintf(s.renderer.output, "\r%s\r", strings.Repeat(" ", len(s.message)+10))
}

// animate runs the spinner animation loop.
func (s *Spinner) animate() {
	for s.active {
		frame := s.frames[s.current]
		s.renderer.infoColor.Fprintf(s.renderer.output, "\r%s %s", frame, s.message)
		s.current = (s.current + 1) % len(s.frames)
		time.Sleep(80 * time.Millisecond)
	}
}
