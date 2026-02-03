package parser

import (
	"time"
)

// Command represents a parsed CLI command with structured fields.
// It captures both the raw user input and the extracted intent, target, and modifiers.
type Command struct {
	// ID is a unique identifier for this command instance
	ID string

	// Raw is the original unmodified user input
	Raw string

	// Intent is the high-level goal (e.g., "status", "analyze", "improve", "test", "debug", "prepare", "release")
	Intent string

	// Target is the specific area or component the intent applies to (e.g., "security", "performance", "project")
	Target string

	// Modifiers are additional parameters that refine the intent execution
	// Examples: {"verbose": "true", "format": "json", "env": "production"}
	Modifiers map[string]string

	// IsValid indicates whether the command passed grammar validation
	IsValid bool

	// Timestamp records when the command was created
	Timestamp time.Time

	// UserID identifies the user who issued the command (optional, for audit purposes)
	UserID string

	// ProjectPath is the path to the project this command operates on (optional)
	ProjectPath string
}

// NewCommand creates a new Command instance with the given raw input.
// It initializes the command with a timestamp and empty collections.
func NewCommand(raw string) *Command {
	return &Command{
		Raw:       raw,
		Modifiers: make(map[string]string),
		Timestamp: time.Now(),
		IsValid:   false,
	}
}
