package parser

import (
	"fmt"
	"testing"
	"time"
)

// TestDecideAction_AutoCorrect tests that high confidence (> 0.9) with a single
// candidate results in "auto-correct" action.
// Validates Requirement 1.2: auto-correct for confidence > 0.9
func TestDecideAction_AutoCorrect(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		confidence float64
		hasRepaired bool
		hasCandidates bool
		expectedAction string
	}{
		{
			name:       "confidence 0.95 with repaired command",
			confidence: 0.95,
			hasRepaired: true,
			hasCandidates: false,
			expectedAction: "auto-correct",
		},
		{
			name:       "confidence 0.91 with repaired command",
			confidence: 0.91,
			hasRepaired: true,
			hasCandidates: false,
			expectedAction: "auto-correct",
		},
		{
			name:       "confidence 1.0 (perfect match)",
			confidence: 1.0,
			hasRepaired: true,
			hasCandidates: false,
			expectedAction: "auto-correct",
		},
		{
			name:       "confidence 0.98 with repaired command",
			confidence: 0.98,
			hasRepaired: true,
			hasCandidates: false,
			expectedAction: "auto-correct",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := &RepairResult{
				Confidence: tt.confidence,
			}

			if tt.hasRepaired {
				result.Repaired = &Command{
					Intent: "status",
					Target: "security",
					IsValid: true,
				}
			}

			if tt.hasCandidates {
				result.Candidates = []*Command{
					{Intent: "status", Target: "security", IsValid: true},
				}
			}

			action := engine.DecideAction(result)
			if action != tt.expectedAction {
				t.Errorf("DecideAction() = %v, want %v", action, tt.expectedAction)
			}
		})
	}
}

// TestDecideAction_PresentOptions tests that medium confidence (0.5-0.9) with
// multiple candidates results in "present-options" action.
// Validates Requirement 1.3: present options for confidence 0.5-0.9
func TestDecideAction_PresentOptions(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		confidence float64
		numCandidates int
		expectedAction string
	}{
		{
			name:       "confidence 0.7 with 2 candidates",
			confidence: 0.7,
			numCandidates: 2,
			expectedAction: "present-options",
		},
		{
			name:       "confidence 0.5 with 3 candidates",
			confidence: 0.5,
			numCandidates: 3,
			expectedAction: "present-options",
		},
		{
			name:       "confidence 0.9 with 2 candidates",
			confidence: 0.9,
			numCandidates: 2,
			expectedAction: "present-options",
		},
		{
			name:       "confidence 0.6 with 4 candidates",
			confidence: 0.6,
			numCandidates: 4,
			expectedAction: "present-options",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := &RepairResult{
				Confidence: tt.confidence,
				Candidates: make([]*Command, tt.numCandidates),
			}

			// Populate candidates
			for i := 0; i < tt.numCandidates; i++ {
				result.Candidates[i] = &Command{
					Intent: "status",
					Target: "security",
					IsValid: true,
				}
			}

			action := engine.DecideAction(result)
			if action != tt.expectedAction {
				t.Errorf("DecideAction() = %v, want %v", action, tt.expectedAction)
			}
		})
	}
}

// TestDecideAction_FailToBackend tests that low confidence (< 0.5) results in
// "fail-to-backend" action.
// Validates Requirement 1.4: invoke Intent_Inference_Service when deterministic repair fails
func TestDecideAction_FailToBackend(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		confidence float64
		hasRepaired bool
		hasCandidates bool
		expectedAction string
	}{
		{
			name:       "confidence 0.0 (complete failure)",
			confidence: 0.0,
			hasRepaired: false,
			hasCandidates: false,
			expectedAction: "fail-to-backend",
		},
		{
			name:       "confidence 0.3 (low confidence)",
			confidence: 0.3,
			hasRepaired: false,
			hasCandidates: false,
			expectedAction: "fail-to-backend",
		},
		{
			name:       "confidence 0.49 (just below threshold)",
			confidence: 0.49,
			hasRepaired: false,
			hasCandidates: false,
			expectedAction: "fail-to-backend",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := &RepairResult{
				Confidence: tt.confidence,
			}

			if tt.hasRepaired {
				result.Repaired = &Command{
					Intent: "status",
					Target: "security",
					IsValid: true,
				}
			}

			if tt.hasCandidates {
				result.Candidates = []*Command{
					{Intent: "status", Target: "security", IsValid: true},
				}
			}

			action := engine.DecideAction(result)
			if action != tt.expectedAction {
				t.Errorf("DecideAction() = %v, want %v", action, tt.expectedAction)
			}
		})
	}
}

// TestDecideAction_EdgeCases tests edge cases in confidence-based decision logic.
func TestDecideAction_EdgeCases(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		result     *RepairResult
		expectedAction string
		description string
	}{
		{
			name: "high confidence but no repaired command",
			result: &RepairResult{
				Confidence: 0.95,
				Repaired: nil,
				Candidates: []*Command{
					{Intent: "status", Target: "security", IsValid: true},
				},
			},
			expectedAction: "present-options",
			description: "Should present options when confidence is high but no single repaired command",
		},
		{
			name: "medium confidence with single candidate",
			result: &RepairResult{
				Confidence: 0.7,
				Repaired: nil,
				Candidates: []*Command{
					{Intent: "status", Target: "security", IsValid: true},
				},
			},
			expectedAction: "present-options",
			description: "Should present options even with single candidate if confidence is medium",
		},
		{
			name: "high confidence with both repaired and candidates",
			result: &RepairResult{
				Confidence: 0.95,
				Repaired: &Command{Intent: "status", Target: "security", IsValid: true},
				Candidates: []*Command{
					{Intent: "analyze", Target: "security", IsValid: true},
				},
			},
			expectedAction: "auto-correct",
			description: "Should auto-correct when confidence is high and repaired command exists",
		},
		{
			name: "exactly 0.9 confidence with repaired command",
			result: &RepairResult{
				Confidence: 0.9,
				Repaired: &Command{Intent: "status", Target: "security", IsValid: true},
				Candidates: nil,
			},
			expectedAction: "fail-to-backend",
			description: "Confidence must be > 0.9 for auto-correct, not >= 0.9",
		},
		{
			name: "exactly 0.5 confidence with candidates",
			result: &RepairResult{
				Confidence: 0.5,
				Repaired: nil,
				Candidates: []*Command{
					{Intent: "status", Target: "security", IsValid: true},
					{Intent: "analyze", Target: "security", IsValid: true},
				},
			},
			expectedAction: "present-options",
			description: "Confidence of 0.5 should present options (inclusive lower bound)",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			action := engine.DecideAction(tt.result)
			if action != tt.expectedAction {
				t.Errorf("DecideAction() = %v, want %v\nDescription: %s", 
					action, tt.expectedAction, tt.description)
			}
		})
	}
}

// TestRepairWithDecision_Integration tests the integrated repair and decision workflow.
func TestRepairWithDecision_Integration(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		input      string
		expectedAction string
		description string
	}{
		{
			name:       "typo in intent - auto-correct",
			input:      "sdlc stauts security",
			expectedAction: "auto-correct",
			description: "Single character typo should result in high confidence auto-correct",
		},
		{
			name:       "synonym - auto-correct",
			input:      "sdlc check security",
			expectedAction: "auto-correct",
			description: "Known synonym should result in high confidence auto-correct",
		},
		{
			name:       "valid command - auto-correct",
			input:      "sdlc status security",
			expectedAction: "auto-correct",
			description: "Valid command should have confidence 1.0 and auto-correct",
		},
		{
			name:       "swapped arguments - auto-correct",
			input:      "sdlc security status",
			expectedAction: "auto-correct",
			description: "Swapped arguments should be detected and auto-corrected",
		},
		{
			name:       "completely invalid - fail to backend",
			input:      "sdlc xyzabc qwerty",
			expectedAction: "fail-to-backend",
			description: "Unrecognizable command should fail to backend",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)
			if err != nil {
				t.Fatalf("Parse() error = %v", err)
			}

			result, action, err := engine.RepairWithDecision(cmd)
			if err != nil {
				t.Fatalf("RepairWithDecision() error = %v", err)
			}

			if action != tt.expectedAction {
				t.Errorf("RepairWithDecision() action = %v, want %v\nDescription: %s\nConfidence: %.2f\nExplanation: %s",
					action, tt.expectedAction, tt.description, result.Confidence, result.Explanation)
			}

			// Verify result consistency
			switch action {
			case "auto-correct":
				if result.Repaired == nil {
					t.Error("auto-correct action but no repaired command")
				}
				if result.Confidence <= 0.9 {
					t.Errorf("auto-correct action but confidence %.2f <= 0.9", result.Confidence)
				}
			case "present-options":
				if len(result.Candidates) == 0 {
					t.Error("present-options action but no candidates")
				}
				if result.Confidence < 0.5 || result.Confidence > 0.9 {
					t.Errorf("present-options action but confidence %.2f not in [0.5, 0.9]", result.Confidence)
				}
			case "fail-to-backend":
				if result.Confidence >= 0.5 {
					t.Errorf("fail-to-backend action but confidence %.2f >= 0.5", result.Confidence)
				}
			}
		})
	}
}

// TestRepairWithDecision_AllRepairStrategies tests that all repair strategies
// work correctly with the decision logic.
func TestRepairWithDecision_AllRepairStrategies(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		input      string
		strategy   string
		expectedAction string
	}{
		{
			name:       "flag normalization strategy",
			input:      "sdlc status security --Verbose",
			strategy:   "flag normalization",
			expectedAction: "auto-correct",
		},
		{
			name:       "argument ordering strategy",
			input:      "sdlc security analyze",
			strategy:   "argument ordering",
			expectedAction: "auto-correct",
		},
		{
			name:       "synonym expansion strategy",
			input:      "sdlc scan security",
			strategy:   "synonym expansion",
			expectedAction: "auto-correct",
		},
		{
			name:       "typo correction strategy",
			input:      "sdlc analyz security",
			strategy:   "typo correction",
			expectedAction: "auto-correct",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd, err := parser.Parse(tt.input)
			if err != nil {
				t.Fatalf("Parse() error = %v", err)
			}

			result, action, err := engine.RepairWithDecision(cmd)
			if err != nil {
				t.Fatalf("RepairWithDecision() error = %v", err)
			}

			if action != tt.expectedAction {
				t.Errorf("%s: RepairWithDecision() action = %v, want %v\nConfidence: %.2f\nExplanation: %s",
					tt.strategy, action, tt.expectedAction, result.Confidence, result.Explanation)
			}

			// Verify the repaired command is valid
			if action == "auto-correct" && result.Repaired != nil {
				if !result.Repaired.IsValid {
					t.Errorf("%s: repaired command is not valid", tt.strategy)
				}
			}
		})
	}
}

// TestDecideAction_ConfidenceThresholds tests the exact confidence threshold boundaries.
func TestDecideAction_ConfidenceThresholds(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		confidence float64
		hasRepaired bool
		hasCandidates bool
		expectedAction string
	}{
		// Test upper threshold for auto-correct (> 0.9)
		{confidence: 0.91, hasRepaired: true, hasCandidates: false, expectedAction: "auto-correct"},
		{confidence: 0.90, hasRepaired: true, hasCandidates: false, expectedAction: "fail-to-backend"},
		
		// Test lower threshold for present-options (>= 0.5)
		{confidence: 0.50, hasRepaired: false, hasCandidates: true, expectedAction: "present-options"},
		{confidence: 0.49, hasRepaired: false, hasCandidates: true, expectedAction: "fail-to-backend"},
		
		// Test upper threshold for present-options (<= 0.9)
		{confidence: 0.90, hasRepaired: false, hasCandidates: true, expectedAction: "present-options"},
		{confidence: 0.91, hasRepaired: false, hasCandidates: true, expectedAction: "fail-to-backend"},
	}

	for _, tt := range tests {
		t.Run(fmt.Sprintf("confidence_%.2f", tt.confidence), func(t *testing.T) {
			result := &RepairResult{
				Confidence: tt.confidence,
			}

			if tt.hasRepaired {
				result.Repaired = &Command{
					Intent: "status",
					Target: "security",
					IsValid: true,
				}
			}

			if tt.hasCandidates {
				result.Candidates = []*Command{
					{Intent: "status", Target: "security", IsValid: true},
					{Intent: "analyze", Target: "security", IsValid: true},
				}
			}

			action := engine.DecideAction(result)
			if action != tt.expectedAction {
				t.Errorf("confidence %.2f: DecideAction() = %v, want %v", 
					tt.confidence, action, tt.expectedAction)
			}
		})
	}
}

// TestRepairWithDecision_ErrorHandling tests error handling in the integrated workflow.
func TestRepairWithDecision_ErrorHandling(t *testing.T) {
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	tests := []struct {
		name       string
		cmd        *Command
		expectError bool
	}{
		{
			name: "nil command",
			cmd: nil,
			expectError: false, // Should handle gracefully
		},
		{
			name: "empty command",
			cmd: &Command{
				Raw: "",
				Timestamp: time.Now(),
			},
			expectError: false, // Should return fail-to-backend action
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, action, err := engine.RepairWithDecision(tt.cmd)
			
			if tt.expectError && err == nil {
				t.Error("expected error but got none")
			}
			
			if !tt.expectError && err != nil {
				t.Errorf("unexpected error: %v", err)
			}

			// Even with errors, should return a valid action
			if err == nil && action == "" {
				t.Error("no error but action is empty")
			}

			// Result should never be nil when there's no error
			if err == nil && result == nil {
				t.Error("no error but result is nil")
			}
		})
	}
}
