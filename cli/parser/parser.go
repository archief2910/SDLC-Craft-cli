package parser

import (
	"errors"
	"fmt"
	"regexp"
	"strings"

	"github.com/google/uuid"
)

var (
	// ErrEmptyInput is returned when the input string is empty or contains only whitespace
	ErrEmptyInput = errors.New("input cannot be empty")

	// ErrInvalidGrammar is returned when the command doesn't match the expected grammar pattern
	ErrInvalidGrammar = errors.New("command does not match expected grammar")

	// ErrMissingIntent is returned when no intent is found in the command
	ErrMissingIntent = errors.New("intent is required")
)

// Parser defines the interface for parsing and validating CLI commands.
// Implementations should extract structured information from raw user input
// and validate it against the expected grammar rules.
type Parser interface {
	// Parse takes raw user input and returns a structured Command object.
	// It extracts the intent, target, and modifiers from the input.
	// Returns an error if the input cannot be parsed.
	Parse(input string) (*Command, error)

	// ValidateGrammar checks if a Command conforms to the expected grammar rules.
	// It verifies that required fields are present and valid.
	// Returns an error if validation fails, nil if the command is valid.
	ValidateGrammar(cmd *Command) error
}

// DefaultParser is the standard implementation of the Parser interface.
// It supports the grammar pattern: sdlc <intent> <target> [modifiers]
// and can detect both structured and natural language input.
type DefaultParser struct {
	// validIntents is the set of recognized intent keywords
	validIntents map[string]bool

	// grammarPattern is the regex for matching structured commands
	grammarPattern *regexp.Regexp

	// modifierPattern is the regex for matching modifiers (flags)
	modifierPattern *regexp.Regexp
}

// NewDefaultParser creates a new DefaultParser with the standard intent set.
func NewDefaultParser() *DefaultParser {
	return &DefaultParser{
		validIntents: map[string]bool{
			"status":  true,
			"analyze": true,
			"improve": true,
			"test":    true,
			"debug":   true,
			"prepare": true,
			"release": true,
		},
		// Pattern matches: sdlc <intent> <target> [modifiers]
		// Example: "sdlc analyze security --verbose"
		grammarPattern: regexp.MustCompile(`^sdlc\s+(\w+)(?:\s+(\w+))?(?:\s+(.*))?$`),
		// Pattern matches various flag formats: --flag=value, --flag value, -f value, flag=value
		modifierPattern: regexp.MustCompile(`(?:--?)?(\w+)(?:=|\s+)([^\s-]+)`),
	}
}

// Parse implements the Parser interface.
// It extracts intent, target, and modifiers from the raw input string.
func (p *DefaultParser) Parse(input string) (*Command, error) {
	// Trim whitespace
	input = strings.TrimSpace(input)

	// Check for empty input
	if input == "" {
		return nil, ErrEmptyInput
	}

	// Create new command with raw input
	cmd := NewCommand(input)
	cmd.ID = uuid.New().String()

	// Try to parse as structured command first
	if p.parseStructured(cmd) {
		// Validate the parsed command
		if err := p.ValidateGrammar(cmd); err != nil {
			cmd.IsValid = false
			return cmd, err
		}
		cmd.IsValid = true
		return cmd, nil
	}

	// If structured parsing fails, mark as natural language input
	// The command will be sent to the backend for intent inference
	cmd.IsValid = false
	return cmd, nil
}

// parseStructured attempts to parse the input as a structured command.
// Returns true if parsing succeeded, false otherwise.
func (p *DefaultParser) parseStructured(cmd *Command) bool {
	matches := p.grammarPattern.FindStringSubmatch(cmd.Raw)
	if matches == nil {
		return false
	}

	// Extract intent (required)
	if len(matches) > 1 && matches[1] != "" {
		cmd.Intent = strings.ToLower(matches[1])
	}

	// Extract target (optional)
	if len(matches) > 2 && matches[2] != "" {
		cmd.Target = strings.ToLower(matches[2])
	}

	// Extract modifiers (optional)
	if len(matches) > 3 && matches[3] != "" {
		p.parseModifiers(cmd, matches[3])
	}

	return true
}

// parseModifiers extracts modifiers from the modifier string.
// Supports various formats: --flag=value, --flag value, -f value, flag=value
func (p *DefaultParser) parseModifiers(cmd *Command, modifierStr string) {
	// Split by spaces to handle different flag formats
	parts := strings.Fields(modifierStr)
	
	for i := 0; i < len(parts); i++ {
		part := parts[i]
		
		// Handle --flag=value or flag=value format
		if strings.Contains(part, "=") {
			kv := strings.SplitN(part, "=", 2)
			key := strings.TrimPrefix(strings.TrimPrefix(kv[0], "--"), "-")
			if len(kv) == 2 {
				cmd.Modifiers[key] = kv[1]
			}
			continue
		}
		
		// Handle --flag value or -f value format
		if strings.HasPrefix(part, "-") {
			key := strings.TrimPrefix(strings.TrimPrefix(part, "--"), "-")
			// Check if next part is the value (not another flag)
			if i+1 < len(parts) && !strings.HasPrefix(parts[i+1], "-") {
				cmd.Modifiers[key] = parts[i+1]
				i++ // Skip next part as we've consumed it
			} else {
				// Boolean flag without value
				cmd.Modifiers[key] = "true"
			}
			continue
		}
		
		// Handle standalone flag (boolean)
		cmd.Modifiers[part] = "true"
	}
}

// ValidateGrammar implements the Parser interface.
// It checks that the command has a valid intent and follows grammar rules.
func (p *DefaultParser) ValidateGrammar(cmd *Command) error {
	// Check for empty intent
	if cmd.Intent == "" {
		return ErrMissingIntent
	}

	// Check if intent is valid
	if !p.validIntents[cmd.Intent] {
		return fmt.Errorf("%w: unknown intent '%s'", ErrInvalidGrammar, cmd.Intent)
	}

	// Intent-specific validation rules
	switch cmd.Intent {
	case "analyze", "improve":
		// These intents require a target
		if cmd.Target == "" {
			return fmt.Errorf("%w: intent '%s' requires a target", ErrInvalidGrammar, cmd.Intent)
		}
	case "status":
		// Status can work with or without a target
		// No additional validation needed
	}

	return nil
}

// IsValidIntent checks if the given string is a recognized intent.
func (p *DefaultParser) IsValidIntent(intent string) bool {
	return p.validIntents[strings.ToLower(intent)]
}

// GetValidIntents returns a list of all valid intents.
func (p *DefaultParser) GetValidIntents() []string {
	intents := make([]string, 0, len(p.validIntents))
	for intent := range p.validIntents {
		intents = append(intents, intent)
	}
	return intents
}
