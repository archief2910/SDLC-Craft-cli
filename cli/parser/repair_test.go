package parser

import (
	"testing"
)

func TestLevenshteinDistance(t *testing.T) {
	tests := []struct {
		name     string
		s1       string
		s2       string
		expected int
	}{
		{
			name:     "identical strings",
			s1:       "status",
			s2:       "status",
			expected: 0,
		},
		{
			name:     "single character substitution",
			s1:       "status",
			s2:       "statuz",
			expected: 1,
		},
		{
			name:     "single character insertion",
			s1:       "status",
			s2:       "statuss",
			expected: 1,
		},
		{
			name:     "single character deletion",
			s1:       "status",
			s2:       "statu",
			expected: 1,
		},
		{
			name:     "two character difference",
			s1:       "status",
			s2:       "stauts",
			expected: 2,
		},
		{
			name:     "completely different",
			s1:       "status",
			s2:       "xyz",
			expected: 6,
		},
		{
			name:     "empty strings",
			s1:       "",
			s2:       "",
			expected: 0,
		},
		{
			name:     "one empty string",
			s1:       "status",
			s2:       "",
			expected: 6,
		},
		{
			name:     "case sensitive",
			s1:       "Status",
			s2:       "status",
			expected: 1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := levenshteinDistance(tt.s1, tt.s2)
			if result != tt.expected {
				t.Errorf("levenshteinDistance(%q, %q) = %d, want %d", tt.s1, tt.s2, result, tt.expected)
			}
		})
	}
}

func TestRepairEngine_Repair_ValidCommand(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	// Parse a valid command
	cmd, err := parser.Parse("sdlc status project")
	if err != nil {
		t.Fatalf("Failed to parse valid command: %v", err)
	}

	result, err := engine.Repair(cmd)
	if err != nil {
		t.Fatalf("Repair failed: %v", err)
	}

	if result.Confidence != 1.0 {
		t.Errorf("Expected confidence 1.0 for valid command, got %f", result.Confidence)
	}

	if result.Repaired == nil {
		t.Error("Expected repaired command to be set for valid command")
	}

	if result.Explanation != "Command is already valid" {
		t.Errorf("Expected explanation 'Command is already valid', got %q", result.Explanation)
	}
}

func TestRepairEngine_Repair_IntentTypo(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name              string
		input             string
		expectedIntent    string
		expectedConfMin   float64
		expectedConfMax   float64
		shouldHaveRepair  bool
	}{
		{
			name:             "single character typo",
			input:            "sdlc statuz project",
			expectedIntent:   "status",
			expectedConfMin:  0.85,
			expectedConfMax:  1.0,
			shouldHaveRepair: true,
		},
		{
			name:             "transposition typo",
			input:            "sdlc analzye security",
			expectedIntent:   "analyze",
			expectedConfMin:  0.85,
			expectedConfMax:  1.0,
			shouldHaveRepair: false, // Edit distance 2 = confidence 0.85, should return candidates
		},
		{
			name:             "missing character",
			input:            "sdlc improv performance",
			expectedIntent:   "improve",
			expectedConfMin:  0.85,
			expectedConfMax:  1.0,
			shouldHaveRepair: true,
		},
		{
			name:             "extra character",
			input:            "sdlc testt coverage",
			expectedIntent:   "test",
			expectedConfMin:  0.85,
			expectedConfMax:  1.0,
			shouldHaveRepair: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Parse the command (will be invalid due to typo)
			cmd := NewCommand(tt.input)
			parser.parseStructured(cmd)

			result, err := engine.Repair(cmd)
			if err != nil {
				t.Fatalf("Repair failed: %v", err)
			}

			if tt.shouldHaveRepair {
				if result.Repaired == nil {
					t.Error("Expected repaired command, got nil")
					return
				}

				if result.Repaired.Intent != tt.expectedIntent {
					t.Errorf("Expected intent %q, got %q", tt.expectedIntent, result.Repaired.Intent)
				}

				if result.Confidence < tt.expectedConfMin || result.Confidence > tt.expectedConfMax {
					t.Errorf("Expected confidence between %f and %f, got %f", tt.expectedConfMin, tt.expectedConfMax, result.Confidence)
				}

				if !result.Repaired.IsValid {
					t.Error("Expected repaired command to be valid")
				}
			} else {
				// Should have candidates instead of repaired command
				if len(result.Candidates) == 0 {
					t.Error("Expected candidates, got none")
					return
				}

				// Check that at least one candidate has the expected intent
				found := false
				for _, candidate := range result.Candidates {
					if candidate.Intent == tt.expectedIntent {
						found = true
						break
					}
				}
				if !found {
					t.Errorf("Expected candidate with intent %q, not found", tt.expectedIntent)
				}

				if result.Confidence < tt.expectedConfMin || result.Confidence > tt.expectedConfMax {
					t.Errorf("Expected confidence between %f and %f, got %f", tt.expectedConfMin, tt.expectedConfMax, result.Confidence)
				}
			}
		})
	}
}

func TestRepairEngine_Repair_TargetTypo(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name             string
		input            string
		expectedTarget   string
		shouldHaveRepair bool
	}{
		{
			name:             "security typo",
			input:            "sdlc analyze securty",
			expectedTarget:   "security",
			shouldHaveRepair: true,
		},
		{
			name:             "performance typo",
			input:            "sdlc improve performace",
			expectedTarget:   "performance",
			shouldHaveRepair: true,
		},
		{
			name:             "coverage typo",
			input:            "sdlc test coverge",
			expectedTarget:   "coverage",
			shouldHaveRepair: true,
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

			if tt.shouldHaveRepair {
				if result.Repaired == nil {
					t.Error("Expected repaired command, got nil")
					return
				}

				if result.Repaired.Target != tt.expectedTarget {
					t.Errorf("Expected target %q, got %q", tt.expectedTarget, result.Repaired.Target)
				}

				if !result.Repaired.IsValid {
					t.Error("Expected repaired command to be valid")
				}
			}
		})
	}
}

func TestRepairEngine_Repair_SynonymExpansion(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name           string
		input          string
		expectedIntent string
	}{
		{
			name:           "check to status",
			input:          "sdlc check project",
			expectedIntent: "status",
		},
		{
			name:           "show to status",
			input:          "sdlc show project",
			expectedIntent: "status",
		},
		{
			name:           "scan to analyze",
			input:          "sdlc scan security",
			expectedIntent: "analyze",
		},
		{
			name:           "optimize to improve",
			input:          "sdlc optimize performance",
			expectedIntent: "improve",
		},
		{
			name:           "run to test",
			input:          "sdlc run tests",
			expectedIntent: "test",
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

func TestRepairEngine_Repair_MultipleCandidates(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	// Create a command with a typo that could match multiple intents
	// "tast" could be "test" (distance 1) but we need to ensure our dictionary
	// doesn't have other words at distance 1-2
	cmd := NewCommand("sdlc tast coverage")
	parser.parseStructured(cmd)

	result, err := engine.Repair(cmd)
	if err != nil {
		t.Fatalf("Repair failed: %v", err)
	}

	// For "tast", we should get "test" as the only candidate at distance 1
	if result.Repaired != nil {
		// Single candidate case
		if result.Repaired.Intent != "test" {
			t.Errorf("Expected intent 'test', got %q", result.Repaired.Intent)
		}
	} else if len(result.Candidates) > 0 {
		// Multiple candidates case
		if result.Confidence < 0.5 || result.Confidence > 0.9 {
			t.Errorf("Expected confidence between 0.5 and 0.9 for multiple candidates, got %f", result.Confidence)
		}
	}
}

func TestRepairEngine_Repair_UnrepairableCommand(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	// Command with intent too far from any valid intent
	cmd := NewCommand("sdlc xyz abc")
	parser.parseStructured(cmd)

	result, err := engine.Repair(cmd)
	if err != nil {
		t.Fatalf("Repair failed: %v", err)
	}

	if result.Confidence >= 0.5 {
		t.Errorf("Expected low confidence for unrepairable command, got %f", result.Confidence)
	}

	if result.Repaired != nil {
		t.Error("Expected no repaired command for unrepairable input")
	}

	if len(result.Candidates) > 0 {
		t.Error("Expected no candidates for unrepairable input")
	}
}

func TestRepairEngine_SuggestCorrections(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name                string
		input               string
		expectSuggestions   bool
		minSuggestions      int
	}{
		{
			name:               "valid command",
			input:              "sdlc status project",
			expectSuggestions:  true,
			minSuggestions:     1, // Should return the valid command itself
		},
		{
			name:               "typo in intent",
			input:              "sdlc statuz project",
			expectSuggestions:  true,
			minSuggestions:     1,
		},
		{
			name:               "synonym",
			input:              "sdlc check project",
			expectSuggestions:  true,
			minSuggestions:     1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)
			if err != nil {
				// If parse fails, create command manually
				cmd = NewCommand(tt.input)
				parser.parseStructured(cmd)
			}

			suggestions, err := engine.SuggestCorrections(cmd)
			if err != nil {
				t.Fatalf("SuggestCorrections failed: %v", err)
			}

			if tt.expectSuggestions {
				if len(suggestions) < tt.minSuggestions {
					t.Errorf("Expected at least %d suggestions, got %d", tt.minSuggestions, len(suggestions))
				}
			}
		})
	}
}

func TestRepairEngine_Repair_PreservesModifiers(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	// Command with typo but also has modifiers
	cmd := NewCommand("sdlc statuz project --verbose --format=json")
	parser.parseStructured(cmd)

	result, err := engine.Repair(cmd)
	if err != nil {
		t.Fatalf("Repair failed: %v", err)
	}

	if result.Repaired == nil {
		t.Fatal("Expected repaired command, got nil")
	}

	// Check that modifiers are preserved
	if result.Repaired.Modifiers["verbose"] != "true" {
		t.Error("Expected verbose modifier to be preserved")
	}

	if result.Repaired.Modifiers["format"] != "json" {
		t.Error("Expected format modifier to be preserved")
	}
}

func TestRepairEngine_Repair_PreservesMetadata(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	// Create command with metadata
	cmd := NewCommand("sdlc statuz project")
	cmd.UserID = "test-user"
	cmd.ProjectPath = "/path/to/project"
	parser.parseStructured(cmd)

	result, err := engine.Repair(cmd)
	if err != nil {
		t.Fatalf("Repair failed: %v", err)
	}

	if result.Repaired == nil {
		t.Fatal("Expected repaired command, got nil")
	}

	// Check that metadata is preserved
	if result.Repaired.UserID != "test-user" {
		t.Errorf("Expected UserID to be preserved, got %q", result.Repaired.UserID)
	}

	if result.Repaired.ProjectPath != "/path/to/project" {
		t.Errorf("Expected ProjectPath to be preserved, got %q", result.Repaired.ProjectPath)
	}

	if result.Repaired.ID != cmd.ID {
		t.Error("Expected ID to be preserved")
	}
}

func TestRepairEngine_CalculateConfidence(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		original   string
		corrected  string
		confidence float64
	}{
		{
			name:       "identical",
			original:   "status",
			corrected:  "status",
			confidence: 1.0,
		},
		{
			name:       "distance 1",
			original:   "statuz",
			corrected:  "status",
			confidence: 0.95,
		},
		{
			name:       "distance 2",
			original:   "stauts",
			corrected:  "status",
			confidence: 0.85,
		},
		{
			name:       "distance 3+",
			original:   "xyz",
			corrected:  "status",
			confidence: 0.5,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			confidence := engine.calculateConfidence(tt.original, tt.corrected)
			if confidence != tt.confidence {
				t.Errorf("Expected confidence %f, got %f", tt.confidence, confidence)
			}
		})
	}
}

func TestRepairEngine_FindTypoCandidates(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name            string
		word            string
		maxDistance     int
		expectedCount   int
		shouldContain   []string
	}{
		{
			name:          "exact match excluded",
			word:          "status",
			maxDistance:   2,
			expectedCount: 0,
			shouldContain: []string{},
		},
		{
			name:          "distance 1",
			word:          "statuz",
			maxDistance:   1,
			expectedCount: 1,
			shouldContain: []string{"status"},
		},
		{
			name:          "distance 2",
			word:          "stauts",
			maxDistance:   2,
			expectedCount: 1,
			shouldContain: []string{"status"},
		},
		{
			name:          "no matches",
			word:          "xyz",
			maxDistance:   1,
			expectedCount: 0,
			shouldContain: []string{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			candidates := engine.findTypoCandidates(tt.word, engine.validIntents, tt.maxDistance)

			if len(candidates) != tt.expectedCount {
				t.Errorf("Expected %d candidates, got %d", tt.expectedCount, len(candidates))
			}

			for _, expected := range tt.shouldContain {
				found := false
				for _, candidate := range candidates {
					if candidate == expected {
						found = true
						break
					}
				}
				if !found {
					t.Errorf("Expected candidates to contain %q", expected)
				}
			}
		})
	}
}
