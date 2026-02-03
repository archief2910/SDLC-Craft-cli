package parser

import (
	"strings"
	"testing"
)

// TestNewCommand verifies that NewCommand creates a properly initialized Command
func TestNewCommand(t *testing.T) {
	raw := "sdlc status"
	cmd := NewCommand(raw)

	if cmd.Raw != raw {
		t.Errorf("Expected Raw to be '%s', got '%s'", raw, cmd.Raw)
	}

	if cmd.Modifiers == nil {
		t.Error("Expected Modifiers map to be initialized")
	}

	if cmd.IsValid {
		t.Error("Expected IsValid to be false by default")
	}

	if cmd.Timestamp.IsZero() {
		t.Error("Expected Timestamp to be set")
	}
}

// TestParseStructuredCommand tests parsing of well-formed structured commands
func TestParseStructuredCommand(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		name           string
		input          string
		expectedIntent string
		expectedTarget string
		expectedValid  bool
	}{
		{
			name:           "simple status command",
			input:          "sdlc status",
			expectedIntent: "status",
			expectedTarget: "",
			expectedValid:  true,
		},
		{
			name:           "status with target",
			input:          "sdlc status project",
			expectedIntent: "status",
			expectedTarget: "project",
			expectedValid:  true,
		},
		{
			name:           "analyze with target",
			input:          "sdlc analyze security",
			expectedIntent: "analyze",
			expectedTarget: "security",
			expectedValid:  true,
		},
		{
			name:           "improve with target",
			input:          "sdlc improve performance",
			expectedIntent: "improve",
			expectedTarget: "performance",
			expectedValid:  true,
		},
		{
			name:           "command with extra whitespace",
			input:          "  sdlc   status   project  ",
			expectedIntent: "status",
			expectedTarget: "project",
			expectedValid:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)

			if tt.expectedValid && err != nil {
				t.Errorf("Expected no error, got: %v", err)
			}

			if cmd == nil {
				t.Fatal("Expected command to be non-nil")
			}

			if cmd.Intent != tt.expectedIntent {
				t.Errorf("Expected intent '%s', got '%s'", tt.expectedIntent, cmd.Intent)
			}

			if cmd.Target != tt.expectedTarget {
				t.Errorf("Expected target '%s', got '%s'", tt.expectedTarget, cmd.Target)
			}

			if cmd.IsValid != tt.expectedValid {
				t.Errorf("Expected IsValid to be %v, got %v", tt.expectedValid, cmd.IsValid)
			}

			if cmd.ID == "" {
				t.Error("Expected command ID to be set")
			}
		})
	}
}

// TestParseModifiers tests parsing of various modifier formats
func TestParseModifiers(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		name              string
		input             string
		expectedModifiers map[string]string
	}{
		{
			name:  "double dash with equals",
			input: "sdlc status --verbose=true",
			expectedModifiers: map[string]string{
				"verbose": "true",
			},
		},
		{
			name:  "double dash with space",
			input: "sdlc status --format json",
			expectedModifiers: map[string]string{
				"format": "json",
			},
		},
		{
			name:  "single dash with space",
			input: "sdlc status -f json",
			expectedModifiers: map[string]string{
				"f": "json",
			},
		},
		{
			name:  "multiple modifiers",
			input: "sdlc analyze security --verbose=true --format json -e production",
			expectedModifiers: map[string]string{
				"verbose": "true",
				"format":  "json",
				"e":       "production",
			},
		},
		{
			name:  "boolean flag without value",
			input: "sdlc status --verbose",
			expectedModifiers: map[string]string{
				"verbose": "true",
			},
		},
		{
			name:  "mixed formats",
			input: "sdlc test --coverage=80 --verbose -q",
			expectedModifiers: map[string]string{
				"coverage": "80",
				"verbose":  "true",
				"q":        "true",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)

			if err != nil {
				t.Errorf("Expected no error, got: %v", err)
			}

			if cmd == nil {
				t.Fatal("Expected command to be non-nil")
			}

			for key, expectedValue := range tt.expectedModifiers {
				actualValue, exists := cmd.Modifiers[key]
				if !exists {
					t.Errorf("Expected modifier '%s' to exist", key)
					continue
				}
				if actualValue != expectedValue {
					t.Errorf("Expected modifier '%s' to be '%s', got '%s'", key, expectedValue, actualValue)
				}
			}

			// Check no extra modifiers
			if len(cmd.Modifiers) != len(tt.expectedModifiers) {
				t.Errorf("Expected %d modifiers, got %d", len(tt.expectedModifiers), len(cmd.Modifiers))
			}
		})
	}
}

// TestValidateGrammar tests grammar validation rules
func TestValidateGrammar(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		name        string
		cmd         *Command
		expectError bool
		errorType   error
	}{
		{
			name: "valid status command",
			cmd: &Command{
				Intent: "status",
				Target: "",
			},
			expectError: false,
		},
		{
			name: "valid analyze with target",
			cmd: &Command{
				Intent: "analyze",
				Target: "security",
			},
			expectError: false,
		},
		{
			name: "missing intent",
			cmd: &Command{
				Intent: "",
				Target: "security",
			},
			expectError: true,
			errorType:   ErrMissingIntent,
		},
		{
			name: "invalid intent",
			cmd: &Command{
				Intent: "invalid",
				Target: "",
			},
			expectError: true,
			errorType:   ErrInvalidGrammar,
		},
		{
			name: "analyze without target",
			cmd: &Command{
				Intent: "analyze",
				Target: "",
			},
			expectError: true,
			errorType:   ErrInvalidGrammar,
		},
		{
			name: "improve without target",
			cmd: &Command{
				Intent: "improve",
				Target: "",
			},
			expectError: true,
			errorType:   ErrInvalidGrammar,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := parser.ValidateGrammar(tt.cmd)

			if tt.expectError && err == nil {
				t.Error("Expected an error but got none")
			}

			if !tt.expectError && err != nil {
				t.Errorf("Expected no error, got: %v", err)
			}
		})
	}
}

// TestParseEmptyInput tests handling of empty input
func TestParseEmptyInput(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		name  string
		input string
	}{
		{"empty string", ""},
		{"only whitespace", "   "},
		{"only tabs", "\t\t"},
		{"mixed whitespace", " \t \n "},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)

			if err != ErrEmptyInput {
				t.Errorf("Expected ErrEmptyInput, got: %v", err)
			}

			if cmd != nil {
				t.Error("Expected nil command for empty input")
			}
		})
	}
}

// TestParseNaturalLanguage tests detection of natural language input
func TestParseNaturalLanguage(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		name  string
		input string
	}{
		{"question format", "what is the status of my project?"},
		{"imperative format", "check the security of the application"},
		{"conversational", "I want to improve the performance"},
		{"missing sdlc prefix", "status project"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)

			// Natural language should parse without error but be marked invalid
			if err != nil {
				t.Errorf("Expected no error for natural language, got: %v", err)
			}

			if cmd == nil {
				t.Fatal("Expected command to be non-nil")
			}

			if cmd.IsValid {
				t.Error("Expected natural language command to be marked invalid")
			}

			if cmd.Raw != tt.input {
				t.Errorf("Expected raw input to be preserved: '%s', got '%s'", tt.input, cmd.Raw)
			}
		})
	}
}

// TestParseEdgeCases tests edge cases and special characters
func TestParseEdgeCases(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		name        string
		input       string
		expectError bool
	}{
		{
			name:        "very long input",
			input:       "sdlc status " + strings.Repeat("a", 1000),
			expectError: false,
		},
		{
			name:        "special characters in modifiers",
			input:       "sdlc status --path=/home/user/project",
			expectError: false,
		},
		{
			name:        "unicode characters",
			input:       "sdlc status project --name=测试",
			expectError: false,
		},
		{
			name:        "numbers in target",
			input:       "sdlc test module123",
			expectError: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)

			if tt.expectError && err == nil {
				t.Error("Expected an error but got none")
			}

			if !tt.expectError && cmd == nil {
				t.Error("Expected command to be non-nil")
			}
		})
	}
}

// TestIsValidIntent tests the intent validation helper
func TestIsValidIntent(t *testing.T) {
	parser := NewDefaultParser()

	validIntents := []string{"status", "analyze", "improve", "test", "debug", "prepare", "release"}
	invalidIntents := []string{"invalid", "unknown", "foo", "bar"}

	for _, intent := range validIntents {
		if !parser.IsValidIntent(intent) {
			t.Errorf("Expected '%s' to be a valid intent", intent)
		}
	}

	for _, intent := range invalidIntents {
		if parser.IsValidIntent(intent) {
			t.Errorf("Expected '%s' to be an invalid intent", intent)
		}
	}
}

// TestGetValidIntents tests retrieval of all valid intents
func TestGetValidIntents(t *testing.T) {
	parser := NewDefaultParser()
	intents := parser.GetValidIntents()

	expectedCount := 7 // status, analyze, improve, test, debug, prepare, release
	if len(intents) != expectedCount {
		t.Errorf("Expected %d intents, got %d", expectedCount, len(intents))
	}

	// Verify all expected intents are present
	expectedIntents := map[string]bool{
		"status": true, "analyze": true, "improve": true, "test": true,
		"debug": true, "prepare": true, "release": true,
	}

	for _, intent := range intents {
		if !expectedIntents[intent] {
			t.Errorf("Unexpected intent in list: %s", intent)
		}
	}
}

// TestCaseInsensitivity tests that intents are case-insensitive
func TestCaseInsensitivity(t *testing.T) {
	parser := NewDefaultParser()

	tests := []struct {
		input          string
		expectedIntent string
	}{
		{"sdlc STATUS", "status"},
		{"sdlc Status", "status"},
		{"sdlc ANALYZE security", "analyze"},
		{"sdlc Analyze Security", "analyze"},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)

			if err != nil {
				t.Errorf("Expected no error, got: %v", err)
			}

			if cmd.Intent != tt.expectedIntent {
				t.Errorf("Expected intent '%s', got '%s'", tt.expectedIntent, cmd.Intent)
			}
		})
	}
}
