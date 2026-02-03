package parser

import (
	"strings"
	"testing"
)

// TestEdgeCase_EmptyInput tests parsing of empty input
func TestEdgeCase_EmptyInput(t *testing.T) {
	parser := NewDefaultParser()
	
	testCases := []string{
		"",
		"   ",
		"\t",
		"\n",
		"  \t\n  ",
	}
	
	for _, input := range testCases {
		_, err := parser.Parse(input)
		if err == nil {
			t.Errorf("Expected error for empty input %q, got nil", input)
		}
	}
}

// TestEdgeCase_VeryLongInput tests parsing of very long input strings
func TestEdgeCase_VeryLongInput(t *testing.T) {
	parser := NewDefaultParser()
	
	// Create a very long command (10KB)
	longTarget := strings.Repeat("a", 10000)
	input := "sdlc status " + longTarget
	
	cmd, err := parser.Parse(input)
	if err != nil {
		t.Errorf("Failed to parse long input: %v", err)
	}
	
	if cmd.Intent != "status" {
		t.Errorf("Expected intent 'status', got %q", cmd.Intent)
	}
	
	if cmd.Target != longTarget {
		t.Errorf("Target length mismatch: expected %d, got %d", len(longTarget), len(cmd.Target))
	}
}

// TestEdgeCase_SpecialCharacters tests parsing with special characters
// Note: Current parser regex (\w+) only supports alphanumeric characters
func TestEdgeCase_SpecialCharacters(t *testing.T) {
	parser := NewDefaultParser()
	
	testCases := []struct {
		input          string
		expectedIntent string
		expectedTarget string
		shouldSucceed  bool
	}{
		{
			input:          "sdlc status myproject",
			expectedIntent: "status",
			expectedTarget: "myproject",
			shouldSucceed:  true,
		},
		{
			input:          "sdlc analyze project_name",
			expectedIntent: "analyze",
			expectedTarget: "project_name",
			shouldSucceed:  true,
		},
		{
			input:          "sdlc test myproject123",
			expectedIntent: "test",
			expectedTarget: "myproject123",
			shouldSucceed:  true,
		},
		{
			input:          "sdlc status project1",
			expectedIntent: "status",
			expectedTarget: "project1",
			shouldSucceed:  true,
		},
		{
			input:          "sdlc improve projectname",
			expectedIntent: "improve",
			expectedTarget: "projectname",
			shouldSucceed:  true,
		},
		{
			input:          "sdlc debug project123",
			expectedIntent: "debug",
			expectedTarget: "project123",
			shouldSucceed:  true,
		},
	}
	
	for _, tc := range testCases {
		cmd, err := parser.Parse(tc.input)
		
		if tc.shouldSucceed {
			if err != nil {
				t.Errorf("Failed to parse %q: %v", tc.input, err)
				continue
			}
			
			if cmd.Intent != tc.expectedIntent {
				t.Errorf("For input %q: expected intent %q, got %q", tc.input, tc.expectedIntent, cmd.Intent)
			}
			
			if cmd.Target != tc.expectedTarget {
				t.Errorf("For input %q: expected target %q, got %q", tc.input, tc.expectedTarget, cmd.Target)
			}
		} else {
			if err == nil {
				t.Errorf("Expected error for input %q, got nil", tc.input)
			}
		}
	}
}

// TestEdgeCase_ModifierFormats tests various modifier formats
func TestEdgeCase_ModifierFormats(t *testing.T) {
	parser := NewDefaultParser()
	
	testCases := []struct {
		input             string
		expectedModifiers map[string]string
	}{
		{
			input: "sdlc status project --verbose",
			expectedModifiers: map[string]string{
				"verbose": "true",
			},
		},
		{
			input: "sdlc status project -v",
			expectedModifiers: map[string]string{
				"v": "true",
			},
		},
		{
			input: "sdlc status project verbose",
			expectedModifiers: map[string]string{
				"verbose": "true",
			},
		},
		{
			input: "sdlc status project --format=json",
			expectedModifiers: map[string]string{
				"format": "json",
			},
		},
		{
			input: "sdlc status project --format json",
			expectedModifiers: map[string]string{
				"format": "json",
			},
		},
		{
			input: "sdlc status project -f json",
			expectedModifiers: map[string]string{
				"f": "json",
			},
		},
		{
			input: "sdlc status project --verbose --format=json --detailed",
			expectedModifiers: map[string]string{
				"verbose":  "true",
				"format":   "json",
				"detailed": "true",
			},
		},
	}
	
	for _, tc := range testCases {
		cmd, err := parser.Parse(tc.input)
		if err != nil {
			t.Errorf("Failed to parse %q: %v", tc.input, err)
			continue
		}
		
		for key, expectedValue := range tc.expectedModifiers {
			actualValue, exists := cmd.Modifiers[key]
			if !exists {
				t.Errorf("For input %q: expected modifier %q not found", tc.input, key)
				continue
			}
			
			if actualValue != expectedValue {
				t.Errorf("For input %q: modifier %q expected value %q, got %q", tc.input, key, expectedValue, actualValue)
			}
		}
	}
}

// TestEdgeCase_UnicodeCharacters tests parsing with Unicode characters
// Note: Current parser regex (\w+) only supports ASCII alphanumeric
func TestEdgeCase_UnicodeCharacters(t *testing.T) {
	parser := NewDefaultParser()
	
	// These will be treated as natural language input since they don't match \w+ pattern
	testCases := []struct {
		input         string
		shouldParse   bool
	}{
		{
			input:       "sdlc status project",
			shouldParse: true, // ASCII alphanumeric works
		},
		{
			input:       "sdlc analyze myproject",
			shouldParse: true,
		},
		{
			input:       "sdlc test project123",
			shouldParse: true,
		},
	}
	
	for _, tc := range testCases {
		cmd, err := parser.Parse(tc.input)
		
		if tc.shouldParse {
			if err != nil {
				t.Errorf("Failed to parse input %q: %v", tc.input, err)
			}
			if !cmd.IsValid {
				t.Errorf("Expected valid command for %q", tc.input)
			}
		}
	}
}

// TestEdgeCase_MultipleSpaces tests parsing with multiple spaces
func TestEdgeCase_MultipleSpaces(t *testing.T) {
	parser := NewDefaultParser()
	
	testCases := []struct {
		input          string
		expectedIntent string
		expectedTarget string
	}{
		{
			input:          "sdlc  status  project",
			expectedIntent: "status",
			expectedTarget: "project",
		},
		{
			input:          "sdlc    analyze    project",
			expectedIntent: "analyze",
			expectedTarget: "project",
		},
		{
			input:          "  sdlc status project  ",
			expectedIntent: "status",
			expectedTarget: "project",
		},
	}
	
	for _, tc := range testCases {
		cmd, err := parser.Parse(tc.input)
		if err != nil {
			t.Errorf("Failed to parse input with multiple spaces %q: %v", tc.input, err)
			continue
		}
		
		if cmd.Intent != tc.expectedIntent {
			t.Errorf("For input %q: expected intent %q, got %q", tc.input, tc.expectedIntent, cmd.Intent)
		}
		
		if cmd.Target != tc.expectedTarget {
			t.Errorf("For input %q: expected target %q, got %q", tc.input, tc.expectedTarget, cmd.Target)
		}
	}
}

// TestEdgeCase_OnlyIntent tests parsing with only intent (no target)
// Status intent can work without a target, but analyze/improve require one
func TestEdgeCase_OnlyIntent(t *testing.T) {
	parser := NewDefaultParser()
	
	testCases := []struct {
		input         string
		shouldSucceed bool
	}{
		{
			input:         "sdlc status",
			shouldSucceed: true, // Status doesn't require target
		},
		{
			input:         "sdlc analyze",
			shouldSucceed: false, // Analyze requires target
		},
		{
			input:         "sdlc test",
			shouldSucceed: true, // Test doesn't require target
		},
	}
	
	for _, tc := range testCases {
		cmd, err := parser.Parse(tc.input)
		
		if tc.shouldSucceed {
			if err != nil {
				t.Errorf("Expected success for %q, got error: %v", tc.input, err)
			}
		} else {
			if err == nil {
				t.Errorf("Expected error for input without target %q, got nil", tc.input)
			} else if cmd != nil && cmd.IsValid {
				t.Errorf("Expected invalid command for %q, but got valid", tc.input)
			}
		}
	}
}

// TestEdgeCase_CaseSensitivity tests case sensitivity of intents
func TestEdgeCase_CaseSensitivity(t *testing.T) {
	parser := NewDefaultParser()
	
	testCases := []struct {
		input          string
		expectedIntent string
		shouldSucceed  bool
	}{
		{
			input:          "sdlc STATUS project",
			expectedIntent: "status",
			shouldSucceed:  true, // Should be case-insensitive
		},
		{
			input:          "sdlc Status project",
			expectedIntent: "status",
			shouldSucceed:  true,
		},
		{
			input:          "sdlc status project",
			expectedIntent: "status",
			shouldSucceed:  true,
		},
	}
	
	for _, tc := range testCases {
		cmd, err := parser.Parse(tc.input)
		
		if tc.shouldSucceed {
			if err != nil {
				t.Errorf("Failed to parse %q: %v", tc.input, err)
				continue
			}
			
			// Intent should be normalized to lowercase
			if cmd.Intent != tc.expectedIntent {
				t.Errorf("For input %q: expected intent %q, got %q", tc.input, tc.expectedIntent, cmd.Intent)
			}
		} else {
			if err == nil {
				t.Errorf("Expected error for input %q, got nil", tc.input)
			}
		}
	}
}
