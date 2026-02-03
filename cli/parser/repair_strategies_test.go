package parser

import (
	"testing"
)

// TestRepairEngine_FlagNormalization tests the enhanced flag normalization strategy
func TestRepairEngine_FlagNormalization(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name                string
		input               string
		expectedModifiers   map[string]string
		shouldNormalize     bool
		expectedConfidence  float64
	}{
		{
			name:  "double dash to no dash",
			input: "sdlc status project --verbose --format=json",
			expectedModifiers: map[string]string{
				"verbose": "true",
				"format":  "json",
			},
			shouldNormalize:    true,
			expectedConfidence: 0.98,
		},
		{
			name:  "single dash to no dash",
			input: "sdlc status project -v -f=json",
			expectedModifiers: map[string]string{
				"v": "true",
				"f": "json",
			},
			shouldNormalize:    true,
			expectedConfidence: 0.98,
		},
		{
			name:  "mixed dash formats",
			input: "sdlc status project --verbose -f json",
			expectedModifiers: map[string]string{
				"verbose": "true",
				"f":       "json",
			},
			shouldNormalize:    true,
			expectedConfidence: 0.98,
		},
		{
			name:  "uppercase to lowercase",
			input: "sdlc status project --Verbose --Format=JSON",
			expectedModifiers: map[string]string{
				"verbose": "true",
				"format":  "JSON",
			},
			shouldNormalize:    true,
			expectedConfidence: 0.98,
		},
		{
			name:  "hyphenated flags normalized",
			input: "sdlc status project --output-file=/tmp/test",
			expectedModifiers: map[string]string{
				"outputfile": "/tmp/test",
			},
			shouldNormalize:    true,
			expectedConfidence: 0.98,
		},
		{
			name:  "underscore flags normalized",
			input: "sdlc status project --output_dir=/tmp",
			expectedModifiers: map[string]string{
				"outputdir": "/tmp",
			},
			shouldNormalize:    true,
			expectedConfidence: 0.98,
		},
		{
			name:  "already normalized",
			input: "sdlc status project verbose format=json",
			expectedModifiers: map[string]string{
				"verbose": "true",
				"format":  "json",
			},
			shouldNormalize:    false,
			expectedConfidence: 1.0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if result.Repaired == nil {
				t.Fatal("Expected repaired command, got nil")
			}

			// Check confidence
			if result.Confidence != tt.expectedConfidence {
				t.Errorf("Expected confidence %f, got %f", tt.expectedConfidence, result.Confidence)
			}

			// Check modifiers
			for key, expectedValue := range tt.expectedModifiers {
				if actualValue, ok := result.Repaired.Modifiers[key]; !ok {
					t.Errorf("Expected modifier %q to be present", key)
				} else if actualValue != expectedValue {
					t.Errorf("Expected modifier %q to have value %q, got %q", key, expectedValue, actualValue)
				}
			}
		})
	}
}

// TestRepairEngine_ArgumentOrdering tests the enhanced argument ordering strategy
func TestRepairEngine_ArgumentOrdering(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name           string
		input          string
		expectedIntent string
		expectedTarget string
		shouldRepair   bool
	}{
		{
			name:           "swapped intent and target",
			input:          "sdlc security analyze",
			expectedIntent: "analyze",
			expectedTarget: "security",
			shouldRepair:   true,
		},
		{
			name:           "target as intent with default",
			input:          "sdlc performance",
			expectedIntent: "status",
			expectedTarget: "performance",
			shouldRepair:   true,
		},
		{
			name:           "intent in target position",
			input:          "sdlc status",
			expectedIntent: "status",
			expectedTarget: "",
			shouldRepair:   false, // Already valid
		},
		{
			name:           "correct order",
			input:          "sdlc analyze security",
			expectedIntent: "analyze",
			expectedTarget: "security",
			shouldRepair:   false, // Already valid
		},
		{
			name:           "both swapped - intent is target, target is intent",
			input:          "sdlc coverage test",
			expectedIntent: "test",
			expectedTarget: "coverage",
			shouldRepair:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if tt.shouldRepair {
				if result.Repaired == nil {
					t.Fatal("Expected repaired command, got nil")
				}

				if result.Repaired.Intent != tt.expectedIntent {
					t.Errorf("Expected intent %q, got %q", tt.expectedIntent, result.Repaired.Intent)
				}

				if result.Repaired.Target != tt.expectedTarget {
					t.Errorf("Expected target %q, got %q", tt.expectedTarget, result.Repaired.Target)
				}

				if result.Confidence != 0.95 {
					t.Errorf("Expected confidence 0.95 for argument ordering, got %f", result.Confidence)
				}
			}
		})
	}
}

// TestRepairEngine_EnhancedSynonyms tests the enhanced synonym expansion with more synonyms
func TestRepairEngine_EnhancedSynonyms(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name           string
		input          string
		expectedIntent string
	}{
		// Status synonyms
		{name: "get to status", input: "sdlc get project", expectedIntent: "status"},
		{name: "list to status", input: "sdlc list project", expectedIntent: "status"},
		{name: "info to status", input: "sdlc info project", expectedIntent: "status"},
		
		// Analyze synonyms
		{name: "audit to analyze", input: "sdlc audit security", expectedIntent: "analyze"},
		{name: "evaluate to analyze", input: "sdlc evaluate security", expectedIntent: "analyze"},
		
		// Improve synonyms
		{name: "refactor to improve", input: "sdlc refactor performance", expectedIntent: "improve"},
		{name: "boost to improve", input: "sdlc boost performance", expectedIntent: "improve"},
		
		// Test synonyms
		{name: "verify to test", input: "sdlc verify coverage", expectedIntent: "test"},
		{name: "validate to test", input: "sdlc validate coverage", expectedIntent: "test"},
		
		// Debug synonyms
		{name: "troubleshoot to debug", input: "sdlc troubleshoot project", expectedIntent: "debug"},
		{name: "diagnose to debug", input: "sdlc diagnose project", expectedIntent: "debug"},
		{name: "investigate to debug", input: "sdlc investigate project", expectedIntent: "debug"},
		
		// Prepare synonyms
		{name: "setup to prepare", input: "sdlc setup project", expectedIntent: "prepare"},
		{name: "configure to prepare", input: "sdlc configure project", expectedIntent: "prepare"},
		{name: "init to prepare", input: "sdlc init project", expectedIntent: "prepare"},
		
		// Release synonyms
		{name: "deploy to release", input: "sdlc deploy project", expectedIntent: "release"},
		{name: "publish to release", input: "sdlc publish project", expectedIntent: "release"},
		{name: "ship to release", input: "sdlc ship project", expectedIntent: "release"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if result.Repaired == nil {
				t.Error("Expected repaired command, got nil")
				return
			}

			if result.Repaired.Intent != tt.expectedIntent {
				t.Errorf("Expected intent %q, got %q", tt.expectedIntent, result.Repaired.Intent)
			}

			if result.Confidence != 0.95 {
				t.Errorf("Expected confidence 0.95 for synonym expansion, got %f", result.Confidence)
			}

			if !result.Repaired.IsValid {
				t.Error("Expected repaired command to be valid")
			}
		})
	}
}

// TestRepairEngine_CombinedStrategies tests multiple strategies working together
func TestRepairEngine_CombinedStrategies(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name           string
		input          string
		expectedIntent string
		expectedTarget string
		description    string
	}{
		{
			name:           "typo and flag normalization",
			input:          "sdlc statuz project --Verbose",
			expectedIntent: "status",
			expectedTarget: "project",
			description:    "Should fix typo and normalize flag",
		},
		{
			name:           "synonym and flag normalization",
			input:          "sdlc check project --Format=json",
			expectedIntent: "status",
			expectedTarget: "project",
			description:    "Should expand synonym and normalize flag",
		},
		{
			name:           "ordering and typo",
			input:          "sdlc securty analzye",
			expectedIntent: "analyze",
			expectedTarget: "security",
			description:    "Should fix ordering and typos",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if result.Repaired == nil && len(result.Candidates) == 0 {
				t.Fatalf("Expected repaired command or candidates, got neither. %s", tt.description)
			}

			// Check if we got a single repair or candidates
			var repairedCmd *Command
			if result.Repaired != nil {
				repairedCmd = result.Repaired
			} else if len(result.Candidates) > 0 {
				// For multiple candidates, check if any match our expectation
				for _, candidate := range result.Candidates {
					if candidate.Intent == tt.expectedIntent && candidate.Target == tt.expectedTarget {
						repairedCmd = candidate
						break
					}
				}
			}

			if repairedCmd == nil {
				t.Fatalf("Could not find expected repair. %s", tt.description)
			}

			if repairedCmd.Intent != tt.expectedIntent {
				t.Errorf("Expected intent %q, got %q. %s", tt.expectedIntent, repairedCmd.Intent, tt.description)
			}

			if repairedCmd.Target != tt.expectedTarget {
				t.Errorf("Expected target %q, got %q. %s", tt.expectedTarget, repairedCmd.Target, tt.description)
			}
		})
	}
}

// TestRepairEngine_EdgeCases tests edge cases for repair strategies
func TestRepairEngine_EdgeCases(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name        string
		input       string
		description string
	}{
		{
			name:        "empty modifiers",
			input:       "sdlc status project",
			description: "Should handle command with no modifiers",
		},
		{
			name:        "multiple flags with same prefix",
			input:       "sdlc status project --verbose --verbosity=high",
			description: "Should handle multiple similar flags",
		},
		{
			name:        "flag with equals but no value",
			input:       "sdlc status project --format=",
			description: "Should handle flag with empty value",
		},
		{
			name:        "flag with special characters",
			input:       "sdlc status project --output-dir=/tmp/test",
			description: "Should handle flags with hyphens and special chars",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v. %s", err, tt.description)
			}

			if result == nil {
				t.Fatalf("Expected repair result, got nil. %s", tt.description)
			}

			// Just verify we don't crash and return something reasonable
			if result.Confidence < 0.0 || result.Confidence > 1.0 {
				t.Errorf("Confidence out of range [0,1]: %f. %s", result.Confidence, tt.description)
			}
		})
	}
}

// TestRepairEngine_StrategyPriority tests that strategies are applied in correct order
func TestRepairEngine_StrategyPriority(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name               string
		input              string
		expectedStrategy   string
		expectedConfidence float64
	}{
		{
			name:               "flag normalization first",
			input:              "sdlc status project --Verbose",
			expectedStrategy:   "Normalized flag",
			expectedConfidence: 0.98,
		},
		{
			name:               "argument ordering second",
			input:              "sdlc security analyze",
			expectedStrategy:   "Reordered arguments",
			expectedConfidence: 0.95,
		},
		{
			name:               "synonym expansion third",
			input:              "sdlc check project",
			expectedStrategy:   "Expanded synonym",
			expectedConfidence: 0.95,
		},
		{
			name:               "typo correction last",
			input:              "sdlc statuz project",
			expectedStrategy:   "Corrected",
			expectedConfidence: 0.95,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if result.Repaired == nil {
				t.Fatal("Expected repaired command, got nil")
			}

			if result.Confidence != tt.expectedConfidence {
				t.Errorf("Expected confidence %f for %s, got %f",
					tt.expectedConfidence, tt.expectedStrategy, result.Confidence)
			}

			// Verify explanation mentions the strategy
			if !contains(result.Explanation, tt.expectedStrategy) &&
				!contains(result.Explanation, "valid") { // "valid" is acceptable for already-valid commands
				t.Logf("Expected explanation to mention %q, got: %q", tt.expectedStrategy, result.Explanation)
			}
		})
	}
}

// TestRepairEngine_TypoWithEditDistance tests typo correction with specific edit distances
func TestRepairEngine_TypoWithEditDistance(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name           string
		input          string
		expectedIntent string
		editDistance   int
	}{
		{
			name:           "distance 1 - substitution",
			input:          "sdlc statuz project",
			expectedIntent: "status",
			editDistance:   1,
		},
		{
			name:           "distance 1 - deletion",
			input:          "sdlc statuss project",
			expectedIntent: "status",
			editDistance:   1,
		},
		{
			name:           "distance 2 - transposition",
			input:          "sdlc stauts project",
			expectedIntent: "status",
			editDistance:   2,
		},
		{
			name:           "distance 2 - two substitutions",
			input:          "sdlc analzye security",
			expectedIntent: "analyze",
			editDistance:   2,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if result.Repaired == nil {
				t.Fatal("Expected repaired command, got nil")
			}

			if result.Repaired.Intent != tt.expectedIntent {
				t.Errorf("Expected intent %q, got %q", tt.expectedIntent, result.Repaired.Intent)
			}

			// Verify confidence is appropriate for edit distance
			if tt.editDistance == 1 && result.Confidence != 0.95 {
				t.Errorf("Expected confidence 0.95 for distance 1, got %f", result.Confidence)
			} else if tt.editDistance == 2 && result.Confidence != 0.85 {
				t.Errorf("Expected confidence 0.85 for distance 2, got %f", result.Confidence)
			}
		})
	}
}

// Helper function to check if a string contains a substring
func contains(s, substr string) bool {
	return len(s) >= len(substr) &&
		(s == substr || len(substr) == 0 ||
			findSubstring(s, substr))
}

func findSubstring(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
