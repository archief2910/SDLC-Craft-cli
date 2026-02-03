package parser

import (
	"strings"
	"testing"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// TestProperty6_GrammarPatternParsing tests that valid grammar patterns are always parsed correctly
// Property 6: Grammar Pattern Parsing
// Validates: Requirements 2.1
func TestProperty6_GrammarPatternParsing(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Valid intents for testing
	validIntents := []string{"status", "analyze", "improve", "test", "debug", "prepare", "release"}
	
	// Generate valid alphanumeric targets (matching \w+ pattern)
	genIntent := gen.OneConstOf(
		validIntents[0], validIntents[1], validIntents[2], 
		validIntents[3], validIntents[4], validIntents[5], validIntents[6],
	)
	genTarget := gen.RegexMatch(`[a-zA-Z][a-zA-Z0-9]{0,20}`)
	genModifiers := gen.SliceOf(gen.AlphaString().SuchThat(func(s string) bool { 
		return len(s) > 0 && len(s) < 20 
	}))

	properties.Property("Valid grammar patterns are parsed correctly", prop.ForAll(
		func(intent, target string, modifiers []string) bool {
			// Build command string
			cmdStr := "sdlc " + intent + " " + target
			if len(modifiers) > 0 {
				cmdStr += " " + strings.Join(modifiers, " ")
			}

			// Parse the command
			parser := NewDefaultParser()
			cmd, err := parser.Parse(cmdStr)

			// Verify parsing succeeded
			if err != nil {
				t.Logf("Failed to parse valid command: %s, error: %v", cmdStr, err)
				return false
			}

			// Verify intent was extracted correctly
			if cmd.Intent != intent {
				t.Logf("Intent mismatch: expected %s, got %s", intent, cmd.Intent)
				return false
			}

			// Verify target was extracted correctly (parser lowercases it)
			if cmd.Target != strings.ToLower(target) {
				t.Logf("Target mismatch: expected %s, got %s", strings.ToLower(target), cmd.Target)
				return false
			}

			// Verify raw command is preserved
			if cmd.Raw != cmdStr {
				t.Logf("Raw command not preserved: expected %s, got %s", cmdStr, cmd.Raw)
				return false
			}

			return true
		},
		genIntent,
		genTarget,
		genModifiers,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty_ParsedCommandsAreValid tests that all successfully parsed commands have valid structure
func TestProperty_ParsedCommandsAreValid(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Generate arbitrary strings
	genCommand := gen.AnyString()

	properties.Property("Successfully parsed commands have valid structure", prop.ForAll(
		func(cmdStr string) bool {
			parser := NewDefaultParser()
			cmd, err := parser.Parse(cmdStr)

			// If parsing succeeded, verify the command structure is valid
			if err == nil {
				// Intent must not be empty
				if cmd.Intent == "" {
					t.Logf("Parsed command has empty intent: %s", cmdStr)
					return false
				}

				// Raw must match input
				if cmd.Raw != cmdStr {
					t.Logf("Raw command doesn't match input: expected %s, got %s", cmdStr, cmd.Raw)
					return false
				}

				// If it's a structured command, it should have the sdlc prefix
				if strings.HasPrefix(strings.TrimSpace(cmdStr), "sdlc ") {
					// Structured commands should have a target
					if cmd.Target == "" {
						t.Logf("Structured command missing target: %s", cmdStr)
						return false
					}
				}
			}

			// If parsing failed, that's acceptable for invalid input
			return true
		},
		genCommand,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty_ParsingIsIdempotent tests that parsing the same command multiple times yields the same result
func TestProperty_ParsingIsIdempotent(t *testing.T) {
	properties := gopter.NewProperties(nil)

	genCommand := gen.AnyString()

	properties.Property("Parsing is idempotent", prop.ForAll(
		func(cmdStr string) bool {
			parser := NewDefaultParser()
			
			// Parse the command twice
			cmd1, err1 := parser.Parse(cmdStr)
			cmd2, err2 := parser.Parse(cmdStr)

			// Both should succeed or both should fail
			if (err1 == nil) != (err2 == nil) {
				t.Logf("Inconsistent parsing results for: %s", cmdStr)
				return false
			}

			// If both succeeded, results should be identical
			if err1 == nil && err2 == nil {
				if cmd1.Raw != cmd2.Raw || cmd1.Intent != cmd2.Intent || cmd1.Target != cmd2.Target {
					t.Logf("Parsing results differ for: %s", cmdStr)
					return false
				}
			}

			return true
		},
		genCommand,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty1_CommandRepairAttempts tests that commands with typos are always repaired or sent to backend
// Property 1: Command Repair Attempts
// Validates: Requirements 1.1, 1.4
// Feature: sdlcraft-cli, Property 1: Command Repair Attempts
//
// For any command with typos (edit distance ≤ 2 from valid intent), the CLI repair engine 
// should attempt correction using edit distance algorithms and either return a repaired 
// command or invoke the intent inference service (indicated by confidence < 0.5).
func TestProperty1_CommandRepairAttempts(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Valid intents and targets for generating test data
	validIntents := []string{"status", "analyze", "improve", "test", "debug", "prepare", "release"}
	validTargets := []string{"security", "performance", "coverage", "quality", "dependencies", "project", "tests", "build", "deployment"}

	// Generator for valid intents
	genValidIntent := gen.OneConstOf(
		validIntents[0], validIntents[1], validIntents[2], 
		validIntents[3], validIntents[4], validIntents[5], validIntents[6],
	)

	// Generator for valid targets
	genValidTarget := gen.OneConstOf(
		validTargets[0], validTargets[1], validTargets[2],
		validTargets[3], validTargets[4], validTargets[5],
		validTargets[6], validTargets[7], validTargets[8],
	)

	// Generator for typo distance (1 or 2)
	genTypoDistance := gen.IntRange(1, 2)

	properties.Property("Commands with typos (edit distance ≤ 2) are repaired or sent to backend", prop.ForAll(
		func(validIntent, validTarget string, typoDistance int) bool {
			parser := NewDefaultParser()
			engine := NewDefaultRepairEngine(parser)

			// Introduce a typo in the intent by modifying characters
			typoIntent := introduceTypo(validIntent, typoDistance)
			
			// Skip if typo generation failed or resulted in the same word
			if typoIntent == validIntent || typoIntent == "" {
				return true
			}

			// Create command with typo
			cmdStr := "sdlc " + typoIntent + " " + validTarget
			cmd := NewCommand(cmdStr)
			parser.parseStructured(cmd)

			// Attempt repair
			result, err := engine.Repair(cmd)

			// Verify repair was attempted without error
			if err != nil {
				t.Logf("Repair failed with error for command '%s': %v", cmdStr, err)
				return false
			}

			// Verify result is not nil
			if result == nil {
				t.Logf("Repair returned nil result for command '%s'", cmdStr)
				return false
			}

			// Property: Either repaired command exists OR confidence < 0.5 (fail to backend)
			// This validates Requirements 1.1 and 1.4:
			// - 1.1: Attempt deterministic repair using edit distance
			// - 1.4: When deterministic repair fails, invoke Intent_Inference_Service (confidence < 0.5)
			hasRepair := result.Repaired != nil
			hasCandidates := len(result.Candidates) > 0
			shouldFailToBackend := result.Confidence < 0.5

			// At least one of these should be true:
			// 1. We have a repaired command (high confidence, single candidate)
			// 2. We have multiple candidates (medium confidence)
			// 3. We should fail to backend (low confidence)
			if !hasRepair && !hasCandidates && !shouldFailToBackend {
				t.Logf("Repair attempt failed for command '%s': no repair, no candidates, and confidence >= 0.5 (%f)", 
					cmdStr, result.Confidence)
				return false
			}

			// If we have a repaired command, verify it's valid
			if hasRepair {
				if !result.Repaired.IsValid {
					t.Logf("Repaired command is not valid for '%s'", cmdStr)
					return false
				}
				
				// Verify the repaired intent is the original valid intent
				if result.Repaired.Intent != validIntent {
					t.Logf("Repaired intent '%s' doesn't match original '%s' for command '%s'", 
						result.Repaired.Intent, validIntent, cmdStr)
					return false
				}
			}

			// If we have candidates, verify they are all valid
			if hasCandidates {
				for _, candidate := range result.Candidates {
					if !candidate.IsValid {
						t.Logf("Candidate command is not valid for '%s'", cmdStr)
						return false
					}
				}
			}

			// Verify explanation is provided
			if result.Explanation == "" {
				t.Logf("No explanation provided for repair of '%s'", cmdStr)
				return false
			}

			return true
		},
		genValidIntent,
		genValidTarget,
		genTypoDistance,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty2_SingleCandidateAutoCorrection tests that commands with single repair candidates are auto-corrected
// Property 2: Single Candidate Auto-Correction
// Validates: Requirements 1.2
// Feature: sdlcraft-cli, Property 2: Single Candidate Auto-Correction
//
// For any command where repair produces a single candidate with confidence > 0.9, 
// the CLI should auto-correct and display the corrected command without requiring user selection.
func TestProperty2_SingleCandidateAutoCorrection(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Valid intents and targets for generating test data
	validIntents := []string{"status", "analyze", "improve", "test", "debug", "prepare", "release"}
	validTargets := []string{"security", "performance", "coverage", "quality", "dependencies", "project", "tests", "build", "deployment"}

	// Generator for valid intents
	genValidIntent := gen.OneConstOf(
		validIntents[0], validIntents[1], validIntents[2], 
		validIntents[3], validIntents[4], validIntents[5], validIntents[6],
	)

	// Generator for valid targets
	genValidTarget := gen.OneConstOf(
		validTargets[0], validTargets[1], validTargets[2],
		validTargets[3], validTargets[4], validTargets[5],
		validTargets[6], validTargets[7], validTargets[8],
	)

	// Generator for typo distance (1 or 2 for high confidence repairs)
	genTypoDistance := gen.IntRange(1, 2)

	properties.Property("Single candidate repairs have confidence > 0.9 and auto-correct", prop.ForAll(
		func(validIntent, validTarget string, typoDistance int) bool {
			parser := NewDefaultParser()
			engine := NewDefaultRepairEngine(parser)

			// Introduce a typo in the intent
			typoIntent := introduceTypo(validIntent, typoDistance)
			
			// Skip if typo generation failed or resulted in the same word
			if typoIntent == validIntent || typoIntent == "" {
				return true
			}

			// Create command with typo
			cmdStr := "sdlc " + typoIntent + " " + validTarget
			cmd := NewCommand(cmdStr)
			parser.parseStructured(cmd)

			// Attempt repair
			result, err := engine.Repair(cmd)

			// Verify repair was attempted without error
			if err != nil {
				t.Logf("Repair failed with error for command '%s': %v", cmdStr, err)
				return false
			}

			// Verify result is not nil
			if result == nil {
				t.Logf("Repair returned nil result for command '%s'", cmdStr)
				return false
			}

			// Property: If we have a single repaired candidate (not multiple candidates),
			// then confidence MUST be > 0.9 (auto-correct threshold)
			// This validates Requirement 1.2: auto-correct for confidence > 0.9 with single candidate
			
			hasSingleRepair := result.Repaired != nil && len(result.Candidates) == 0
			hasMultipleCandidates := len(result.Candidates) > 1
			
			// If we have a single repaired command (the auto-correct case)
			if hasSingleRepair {
				// Confidence MUST be > 0.9 for auto-correction
				if result.Confidence <= 0.9 {
					t.Logf("Single repair candidate has confidence <= 0.9 (%f) for command '%s'", 
						result.Confidence, cmdStr)
					return false
				}

				// The repaired command MUST be valid
				if !result.Repaired.IsValid {
					t.Logf("Auto-corrected command is not valid for '%s'", cmdStr)
					return false
				}

				// The repaired intent should match the original valid intent
				if result.Repaired.Intent != validIntent {
					t.Logf("Auto-corrected intent '%s' doesn't match original '%s' for command '%s'", 
						result.Repaired.Intent, validIntent, cmdStr)
					return false
				}

				// Explanation must be provided for auto-correction
				if result.Explanation == "" {
					t.Logf("No explanation provided for auto-correction of '%s'", cmdStr)
					return false
				}

				// The action should be "auto-correct"
				action := engine.DecideAction(result)
				if action != "auto-correct" {
					t.Logf("Expected action 'auto-correct' for single candidate with confidence %f, got '%s'", 
						result.Confidence, action)
					return false
				}
			}

			// If we have multiple candidates (not auto-correct case)
			if hasMultipleCandidates {
				// Confidence should be in the medium range (0.5-0.9)
				if result.Confidence <= 0.5 || result.Confidence > 0.9 {
					t.Logf("Multiple candidates have confidence outside 0.5-0.9 range: %f for command '%s'", 
						result.Confidence, cmdStr)
					return false
				}

				// The action should be "present-options"
				action := engine.DecideAction(result)
				if action != "present-options" {
					t.Logf("Expected action 'present-options' for multiple candidates, got '%s'", action)
					return false
				}
			}

			// If confidence is < 0.5, should fail to backend
			if result.Confidence < 0.5 {
				action := engine.DecideAction(result)
				if action != "fail-to-backend" {
					t.Logf("Expected action 'fail-to-backend' for low confidence %f, got '%s'", 
						result.Confidence, action)
					return false
				}
			}

			return true
		},
		genValidIntent,
		genValidTarget,
		genTypoDistance,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty3_MultipleCandidatePresentation tests that commands with multiple repair candidates present all options
// Property 3: Multiple Candidate Presentation
// Validates: Requirements 1.3
// Feature: sdlcraft-cli, Property 3: Multiple Candidate Presentation
//
// For any command where repair produces multiple candidates with confidence between 0.5-0.9, 
// the CLI should present all candidates to the user for selection.
func TestProperty3_MultipleCandidatePresentation(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Valid intents and targets for generating test data
	validIntents := []string{"status", "analyze", "improve", "test", "debug", "prepare", "release"}
	validTargets := []string{"security", "performance", "coverage", "quality", "dependencies", "project", "tests", "build", "deployment"}

	// Generator for valid intents
	genValidIntent := gen.OneConstOf(
		validIntents[0], validIntents[1], validIntents[2], 
		validIntents[3], validIntents[4], validIntents[5], validIntents[6],
	)

	// Generator for valid targets
	genValidTarget := gen.OneConstOf(
		validTargets[0], validTargets[1], validTargets[2],
		validTargets[3], validTargets[4], validTargets[5],
		validTargets[6], validTargets[7], validTargets[8],
	)

	// Generator for typo distance (2 for medium confidence repairs that may produce multiple candidates)
	genTypoDistance := gen.IntRange(2, 2)

	properties.Property("Multiple candidate repairs have confidence 0.5-0.9 and present all options", prop.ForAll(
		func(validIntent, validTarget string, typoDistance int) bool {
			parser := NewDefaultParser()
			engine := NewDefaultRepairEngine(parser)

			// Introduce a typo in the intent
			typoIntent := introduceTypo(validIntent, typoDistance)
			
			// Skip if typo generation failed or resulted in the same word
			if typoIntent == validIntent || typoIntent == "" {
				return true
			}

			// Create command with typo
			cmdStr := "sdlc " + typoIntent + " " + validTarget
			cmd := NewCommand(cmdStr)
			parser.parseStructured(cmd)

			// Attempt repair
			result, err := engine.Repair(cmd)

			// Verify repair was attempted without error
			if err != nil {
				t.Logf("Repair failed with error for command '%s': %v", cmdStr, err)
				return false
			}

			// Verify result is not nil
			if result == nil {
				t.Logf("Repair returned nil result for command '%s'", cmdStr)
				return false
			}

			// Property: If we have multiple candidates (more than 1),
			// then confidence MUST be between 0.5 and 0.9 (medium confidence range)
			// This validates Requirement 1.3: present options for confidence 0.5-0.9 with multiple candidates
			
			hasMultipleCandidates := len(result.Candidates) > 1
			
			// If we have multiple candidates (the present-options case)
			if hasMultipleCandidates {
				// Confidence MUST be between 0.5 and 0.9 for multiple candidates
				if result.Confidence < 0.5 || result.Confidence > 0.9 {
					t.Logf("Multiple candidates have confidence outside 0.5-0.9 range: %f for command '%s'", 
						result.Confidence, cmdStr)
					return false
				}

				// All candidates MUST be valid commands
				for i, candidate := range result.Candidates {
					if !candidate.IsValid {
						t.Logf("Candidate %d is not valid for command '%s'", i, cmdStr)
						return false
					}

					// Each candidate should have a valid intent
					if candidate.Intent == "" {
						t.Logf("Candidate %d has empty intent for command '%s'", i, cmdStr)
						return false
					}

					// Each candidate should preserve the target
					if candidate.Target != strings.ToLower(validTarget) {
						t.Logf("Candidate %d has incorrect target '%s' (expected '%s') for command '%s'", 
							i, candidate.Target, strings.ToLower(validTarget), cmdStr)
						return false
					}
				}

				// Explanation must be provided for multiple candidates
				if result.Explanation == "" {
					t.Logf("No explanation provided for multiple candidates of '%s'", cmdStr)
					return false
				}

				// The explanation should mention the number of candidates
				if !strings.Contains(result.Explanation, "possible corrections") && 
				   !strings.Contains(result.Explanation, "candidates") {
					t.Logf("Explanation doesn't mention multiple candidates for '%s': %s", 
						cmdStr, result.Explanation)
					return false
				}

				// The action should be "present-options"
				action := engine.DecideAction(result)
				if action != "present-options" {
					t.Logf("Expected action 'present-options' for multiple candidates with confidence %f, got '%s'", 
						result.Confidence, action)
					return false
				}

				// Should NOT have a single repaired command when we have multiple candidates
				if result.Repaired != nil {
					t.Logf("Should not have single repaired command when multiple candidates exist for '%s'", cmdStr)
					return false
				}
			}

			// If we have a single repaired command (auto-correct case)
			if result.Repaired != nil && len(result.Candidates) == 0 {
				// Confidence should be > 0.9 for auto-correction
				if result.Confidence <= 0.9 {
					t.Logf("Single repair has confidence <= 0.9 (%f) for command '%s'", 
						result.Confidence, cmdStr)
					return false
				}

				// The action should be "auto-correct"
				action := engine.DecideAction(result)
				if action != "auto-correct" {
					t.Logf("Expected action 'auto-correct' for single candidate with confidence %f, got '%s'", 
						result.Confidence, action)
					return false
				}
			}

			// If confidence is < 0.5, should fail to backend
			if result.Confidence < 0.5 {
				action := engine.DecideAction(result)
				if action != "fail-to-backend" {
					t.Logf("Expected action 'fail-to-backend' for low confidence %f, got '%s'", 
						result.Confidence, action)
					return false
				}

				// Should not have repaired command or candidates
				if result.Repaired != nil || len(result.Candidates) > 0 {
					t.Logf("Should not have repair or candidates when confidence < 0.5 for '%s'", cmdStr)
					return false
				}
			}

			return true
		},
		genValidIntent,
		genValidTarget,
		genTypoDistance,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// introduceTypo introduces a typo into a word by performing random edit operations
// up to the specified distance. Operations include: substitution, insertion, deletion.
func introduceTypo(word string, distance int) string {
	if word == "" || distance <= 0 {
		return word
	}

	runes := []rune(word)
	
	for i := 0; i < distance && len(runes) > 0; i++ {
		// Choose a random operation: 0=substitute, 1=insert, 2=delete
		operation := i % 3
		
		switch operation {
		case 0: // Substitute a character
			if len(runes) > 0 {
				pos := i % len(runes)
				// Substitute with a different character
				if runes[pos] == 'a' {
					runes[pos] = 'z'
				} else {
					runes[pos] = 'a'
				}
			}
		case 1: // Insert a character
			if len(runes) > 0 {
				pos := i % len(runes)
				// Insert 'x' at position
				runes = append(runes[:pos], append([]rune{'x'}, runes[pos:]...)...)
			}
		case 2: // Delete a character
			if len(runes) > 1 { // Keep at least one character
				pos := i % len(runes)
				runes = append(runes[:pos], runes[pos+1:]...)
			}
		}
	}
	
	return string(runes)
}
