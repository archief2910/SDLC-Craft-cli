package parser

import (
	"fmt"
	"strings"
)

// RepairResult represents the outcome of attempting to repair an invalid command.
// It includes the original command, the repaired version (if successful),
// a confidence score, and an explanation of what was repaired.
type RepairResult struct {
	// Original is the original command that was attempted to be repaired
	Original *Command

	// Repaired is the corrected command (nil if repair failed)
	Repaired *Command

	// Confidence is a score from 0.0 to 1.0 indicating repair confidence
	// > 0.9: High confidence, auto-correct
	// 0.5-0.9: Medium confidence, present options
	// < 0.5: Low confidence, fail to backend
	Confidence float64

	// Explanation describes what was repaired and why
	Explanation string

	// Candidates contains alternative repair options when confidence is medium
	Candidates []*Command
}

// RepairEngine defines the interface for deterministic command repair.
// It attempts to fix typos, ordering issues, and flag problems in commands
// without requiring backend calls.
type RepairEngine interface {
	// Repair attempts to fix an invalid command using deterministic strategies.
	// Returns a RepairResult with the repaired command and confidence score.
	Repair(cmd *Command) (*RepairResult, error)

	// SuggestCorrections returns multiple possible corrections for a command.
	// Used when repair produces multiple candidates with similar confidence.
	SuggestCorrections(cmd *Command) ([]*Command, error)

	// DecideAction determines the appropriate action based on repair confidence.
	// Returns the action to take: "auto-correct", "present-options", or "fail-to-backend"
	DecideAction(result *RepairResult) string
}

// DefaultRepairEngine implements the RepairEngine interface using
// edit distance algorithms and pattern matching.
type DefaultRepairEngine struct {
	// parser is used to validate repaired commands
	parser Parser

	// validIntents is the dictionary of recognized intents
	validIntents []string

	// commonTargets is the dictionary of common target values
	commonTargets []string

	// intentSynonyms maps common synonyms to valid intents
	intentSynonyms map[string]string
}

// NewDefaultRepairEngine creates a new repair engine with the given parser.
func NewDefaultRepairEngine(parser Parser) *DefaultRepairEngine {
	return &DefaultRepairEngine{
		parser: parser,
		validIntents: []string{
			"status",
			"analyze",
			"improve",
			"test",
			"debug",
			"prepare",
			"release",
		},
		commonTargets: []string{
			"security",
			"performance",
			"coverage",
			"quality",
			"dependencies",
			"project",
			"tests",
			"build",
			"deployment",
		},
		intentSynonyms: map[string]string{
			// Status synonyms
			"check":    "status",
			"show":     "status",
			"display":  "status",
			"view":     "status",
			"get":      "status",
			"list":     "status",
			"info":     "status",
			// Analyze synonyms
			"scan":     "analyze",
			"examine":  "analyze",
			"inspect":  "analyze",
			"review":   "analyze",
			"audit":    "analyze",
			"evaluate": "analyze",
			// Improve synonyms
			"enhance":  "improve",
			"optimize": "improve",
			"fix":      "improve",
			"upgrade":  "improve",
			"refactor": "improve",
			"boost":    "improve",
			// Test synonyms
			"run":      "test",
			"execute":  "test",
			"verify":   "test",
			"validate": "test",
			// Debug synonyms
			"troubleshoot": "debug",
			"diagnose":     "debug",
			"investigate":  "debug",
			// Prepare synonyms
			"setup":     "prepare",
			"configure": "prepare",
			"init":      "prepare",
			"initialize": "prepare",
			// Release synonyms
			"deploy":  "release",
			"publish": "release",
			"ship":    "release",
		},
	}
}

// Repair implements the RepairEngine interface.
// It attempts multiple repair strategies in order:
// 1. Flag normalization
// 2. Argument ordering fixes
// 3. Synonym expansion
// 4. Typo correction using edit distance
func (r *DefaultRepairEngine) Repair(cmd *Command) (*RepairResult, error) {
	// Check for nil command
	if cmd == nil {
		return nil, fmt.Errorf("cannot repair nil command")
	}

	result := &RepairResult{
		Original:   cmd,
		Candidates: make([]*Command, 0),
	}

	// If command is already valid, no repair needed
	if cmd.IsValid {
		result.Repaired = cmd
		result.Confidence = 1.0
		result.Explanation = "Command is already valid"
		return result, nil
	}

	// Strategy 1: Try flag normalization
	if repaired := r.tryFlagNormalization(cmd); repaired != nil {
		result.Repaired = repaired
		result.Confidence = 0.98
		result.Explanation = "Normalized flag formats to standard form"
		return result, nil
	}

	// Strategy 2: Try argument ordering fixes
	if repaired := r.tryArgumentOrdering(cmd); repaired != nil {
		result.Repaired = repaired
		result.Confidence = 0.95
		result.Explanation = fmt.Sprintf("Reordered arguments to match grammar pattern")
		return result, nil
	}

	// Strategy 3: Try synonym expansion
	if repaired := r.trySynonymExpansion(cmd); repaired != nil {
		result.Repaired = repaired
		result.Confidence = 0.95
		result.Explanation = fmt.Sprintf("Expanded synonym '%s' to intent '%s'", cmd.Intent, repaired.Intent)
		return result, nil
	}

	// Strategy 2: Try typo correction on intent
	if cmd.Intent != "" {
		candidates := r.findTypoCandidates(cmd.Intent, r.validIntents, 2)
		if len(candidates) == 1 {
			// Single candidate - check confidence to decide auto-correct vs present-options
			repaired := r.createRepairedCommand(cmd, candidates[0], cmd.Target)
			if err := r.parser.ValidateGrammar(repaired); err == nil {
				confidence := r.calculateConfidence(cmd.Intent, candidates[0])
				
				// Only auto-correct if confidence > 0.9 (Requirement 1.2)
				if confidence > 0.9 {
					result.Repaired = repaired
					result.Confidence = confidence
					result.Explanation = fmt.Sprintf("Corrected intent typo '%s' to '%s'", cmd.Intent, candidates[0])
					return result, nil
				} else {
					// Medium confidence: add as candidate for user selection (Requirement 1.3)
					result.Candidates = append(result.Candidates, repaired)
					result.Confidence = confidence
					result.Explanation = fmt.Sprintf("Found possible correction for intent '%s': '%s'", cmd.Intent, candidates[0])
					return result, nil
				}
			}
		} else if len(candidates) > 1 {
			// Multiple candidates with medium confidence
			for _, candidate := range candidates {
				repaired := r.createRepairedCommand(cmd, candidate, cmd.Target)
				if err := r.parser.ValidateGrammar(repaired); err == nil {
					result.Candidates = append(result.Candidates, repaired)
				}
			}
			if len(result.Candidates) > 0 {
				result.Confidence = 0.7
				result.Explanation = fmt.Sprintf("Found %d possible corrections for intent '%s'", len(result.Candidates), cmd.Intent)
				return result, nil
			}
		}
	}

	// Strategy 3: Try typo correction on target
	if cmd.Target != "" && cmd.Intent != "" {
		candidates := r.findTypoCandidates(cmd.Target, r.commonTargets, 2)
		if len(candidates) == 1 {
			repaired := r.createRepairedCommand(cmd, cmd.Intent, candidates[0])
			if err := r.parser.ValidateGrammar(repaired); err == nil {
				confidence := r.calculateConfidence(cmd.Target, candidates[0])
				
				// Only auto-correct if confidence > 0.9 (Requirement 1.2)
				if confidence > 0.9 {
					result.Repaired = repaired
					result.Confidence = confidence
					result.Explanation = fmt.Sprintf("Corrected target typo '%s' to '%s'", cmd.Target, candidates[0])
					return result, nil
				} else {
					// Medium confidence: add as candidate for user selection (Requirement 1.3)
					result.Candidates = append(result.Candidates, repaired)
					result.Confidence = confidence
					result.Explanation = fmt.Sprintf("Found possible correction for target '%s': '%s'", cmd.Target, candidates[0])
					return result, nil
				}
			}
		} else if len(candidates) > 1 {
			for _, candidate := range candidates {
				repaired := r.createRepairedCommand(cmd, cmd.Intent, candidate)
				if err := r.parser.ValidateGrammar(repaired); err == nil {
					result.Candidates = append(result.Candidates, repaired)
				}
			}
			if len(result.Candidates) > 0 {
				result.Confidence = 0.7
				result.Explanation = fmt.Sprintf("Found %d possible corrections for target '%s'", len(result.Candidates), cmd.Target)
				return result, nil
			}
		}
	}

	// Strategy 4: Try both intent and target correction
	if cmd.Intent != "" && cmd.Target != "" {
		intentCandidates := r.findTypoCandidates(cmd.Intent, r.validIntents, 2)
		targetCandidates := r.findTypoCandidates(cmd.Target, r.commonTargets, 2)

		for _, intentCandidate := range intentCandidates {
			for _, targetCandidate := range targetCandidates {
				repaired := r.createRepairedCommand(cmd, intentCandidate, targetCandidate)
				if err := r.parser.ValidateGrammar(repaired); err == nil {
					result.Candidates = append(result.Candidates, repaired)
				}
			}
		}

		if len(result.Candidates) > 0 {
			result.Confidence = 0.6
			result.Explanation = fmt.Sprintf("Found %d possible corrections for both intent and target", len(result.Candidates))
			return result, nil
		}
	}

	// All repair strategies failed
	result.Confidence = 0.0
	result.Explanation = "Unable to repair command deterministically, requires intent inference"
	return result, nil
}

// SuggestCorrections implements the RepairEngine interface.
// It returns all possible corrections for a command.
func (r *DefaultRepairEngine) SuggestCorrections(cmd *Command) ([]*Command, error) {
	result, err := r.Repair(cmd)
	if err != nil {
		return nil, err
	}

	if result.Repaired != nil {
		return []*Command{result.Repaired}, nil
	}

	return result.Candidates, nil
}

// tryFlagNormalization attempts to normalize flag formats to standard form.
// Converts various flag formats (--flag, -f, flag) to the standard format.
// This strategy handles:
// - Removing leading dashes (--verbose, -v → verbose, v)
// - Converting to lowercase (--Verbose → verbose)
// - Normalizing boolean flags (--flag=true → flag: true)
// - Handling common flag variations (--output-file → outputfile)
func (r *DefaultRepairEngine) tryFlagNormalization(cmd *Command) *Command {
	if len(cmd.Modifiers) == 0 {
		return nil
	}

	normalized := false
	normalizedModifiers := make(map[string]string)

	// Normalize each modifier
	for key, value := range cmd.Modifiers {
		normalizedKey := key
		
		// Remove leading dashes if present (handles both -- and -)
		normalizedKey = strings.TrimPrefix(normalizedKey, "--")
		normalizedKey = strings.TrimPrefix(normalizedKey, "-")
		
		// Convert to lowercase for consistency
		normalizedKey = strings.ToLower(normalizedKey)
		
		// Remove hyphens and underscores for consistency (output-file → outputfile)
		// This allows matching flags regardless of separator style
		normalizedKey = strings.ReplaceAll(normalizedKey, "-", "")
		normalizedKey = strings.ReplaceAll(normalizedKey, "_", "")
		
		// Check if normalization changed the key
		if normalizedKey != key {
			normalized = true
		}
		
		// Normalize boolean values
		normalizedValue := value
		if value == "" || strings.ToLower(value) == "true" {
			normalizedValue = "true"
		} else if strings.ToLower(value) == "false" {
			normalizedValue = "false"
		}
		
		normalizedModifiers[normalizedKey] = normalizedValue
	}

	// If no normalization occurred, return nil
	if !normalized {
		return nil
	}

	// Create repaired command with normalized modifiers
	repaired := r.createRepairedCommand(cmd, cmd.Intent, cmd.Target)
	repaired.Modifiers = normalizedModifiers

	// Validate the repaired command
	if err := r.parser.ValidateGrammar(repaired); err == nil {
		repaired.IsValid = true
		return repaired
	}

	return nil
}

// tryArgumentOrdering attempts to fix argument ordering issues.
// Handles cases where intent and target are swapped or in wrong positions.
// This strategy addresses common user errors:
// - Swapped intent and target (e.g., "sdlc security analyze" → "sdlc analyze security")
// - Intent in target position (e.g., "sdlc status" when target is missing)
// - Target in intent position (e.g., "sdlc security" → "sdlc status security")
func (r *DefaultRepairEngine) tryArgumentOrdering(cmd *Command) *Command {
	// Strategy 1: If both intent and target are present and command is invalid,
	// try swapping them to see if that fixes the issue
	if cmd.Intent != "" && cmd.Target != "" {
		// Check if target looks like a valid intent
		for _, validIntent := range r.validIntents {
			if strings.EqualFold(cmd.Target, validIntent) {
				// Try swapping intent and target
				repaired := r.createRepairedCommand(cmd, cmd.Target, cmd.Intent)
				if err := r.parser.ValidateGrammar(repaired); err == nil {
					repaired.IsValid = true
					return repaired
				}
			}
		}
		
		// Also check if intent looks like a target and target looks like an intent
		// This handles cases where both are valid but in wrong order
		intentIsTarget := false
		targetIsIntent := false
		
		for _, commonTarget := range r.commonTargets {
			if strings.EqualFold(cmd.Intent, commonTarget) {
				intentIsTarget = true
				break
			}
		}
		
		for _, validIntent := range r.validIntents {
			if strings.EqualFold(cmd.Target, validIntent) {
				targetIsIntent = true
				break
			}
		}
		
		if intentIsTarget && targetIsIntent {
			// Swap them
			repaired := r.createRepairedCommand(cmd, cmd.Target, cmd.Intent)
			if err := r.parser.ValidateGrammar(repaired); err == nil {
				repaired.IsValid = true
				return repaired
			}
		}
	}

	// Strategy 2: If intent is empty but target is present, check if target is actually an intent
	if cmd.Intent == "" && cmd.Target != "" {
		for _, validIntent := range r.validIntents {
			if strings.EqualFold(cmd.Target, validIntent) {
				// Move target to intent position
				repaired := r.createRepairedCommand(cmd, cmd.Target, "")
				if err := r.parser.ValidateGrammar(repaired); err == nil {
					repaired.IsValid = true
					return repaired
				}
			}
		}
	}

	// Strategy 3: If intent looks like a target and target is empty,
	// try to infer the intent as "status" (most common default)
	if cmd.Intent != "" && cmd.Target == "" {
		for _, commonTarget := range r.commonTargets {
			if strings.EqualFold(cmd.Intent, commonTarget) {
				// Intent is actually a target, use "status" as default intent
				repaired := r.createRepairedCommand(cmd, "status", cmd.Intent)
				if err := r.parser.ValidateGrammar(repaired); err == nil {
					repaired.IsValid = true
					return repaired
				}
			}
		}
	}

	return nil
}

// trySynonymExpansion attempts to expand a synonym to a valid intent.
// This strategy maps common user terms to standard intents.
// Examples: "check" → "status", "scan" → "analyze", "optimize" → "improve"
func (r *DefaultRepairEngine) trySynonymExpansion(cmd *Command) *Command {
	if cmd.Intent == "" {
		return nil
	}

	// Check if intent is a known synonym (case-insensitive)
	intentLower := strings.ToLower(cmd.Intent)
	if validIntent, ok := r.intentSynonyms[intentLower]; ok {
		repaired := r.createRepairedCommand(cmd, validIntent, cmd.Target)
		if err := r.parser.ValidateGrammar(repaired); err == nil {
			repaired.IsValid = true
			return repaired
		}
	}

	return nil
}

// findTypoCandidates finds words in the dictionary within the given edit distance.
func (r *DefaultRepairEngine) findTypoCandidates(word string, dictionary []string, maxDistance int) []string {
	candidates := make([]string, 0)
	word = strings.ToLower(word)

	for _, dictWord := range dictionary {
		distance := levenshteinDistance(word, strings.ToLower(dictWord))
		if distance > 0 && distance <= maxDistance {
			candidates = append(candidates, dictWord)
		}
	}

	return candidates
}

// calculateConfidence calculates the confidence score based on edit distance.
func (r *DefaultRepairEngine) calculateConfidence(original, corrected string) float64 {
	distance := levenshteinDistance(strings.ToLower(original), strings.ToLower(corrected))
	
	// Confidence decreases with edit distance
	switch distance {
	case 0:
		return 1.0
	case 1:
		return 0.95
	case 2:
		return 0.85
	default:
		return 0.5
	}
}

// createRepairedCommand creates a new command with corrected intent and target.
func (r *DefaultRepairEngine) createRepairedCommand(original *Command, intent, target string) *Command {
	repaired := &Command{
		ID:          original.ID,
		Raw:         original.Raw,
		Intent:      intent,
		Target:      target,
		Modifiers:   make(map[string]string),
		IsValid:     false,
		Timestamp:   original.Timestamp,
		UserID:      original.UserID,
		ProjectPath: original.ProjectPath,
	}

	// Copy modifiers
	for k, v := range original.Modifiers {
		repaired.Modifiers[k] = v
	}

	// Validate the repaired command
	if err := r.parser.ValidateGrammar(repaired); err == nil {
		repaired.IsValid = true
	}

	return repaired
}

// levenshteinDistance calculates the Levenshtein distance between two strings.
// This is the minimum number of single-character edits (insertions, deletions, or substitutions)
// required to change one string into the other.
func levenshteinDistance(s1, s2 string) int {
	// Convert strings to rune slices to handle Unicode correctly
	r1 := []rune(s1)
	r2 := []rune(s2)
	
	len1 := len(r1)
	len2 := len(r2)

	// Create a 2D matrix to store distances
	matrix := make([][]int, len1+1)
	for i := range matrix {
		matrix[i] = make([]int, len2+1)
	}

	// Initialize first row and column
	for i := 0; i <= len1; i++ {
		matrix[i][0] = i
	}
	for j := 0; j <= len2; j++ {
		matrix[0][j] = j
	}

	// Fill the matrix using dynamic programming
	for i := 1; i <= len1; i++ {
		for j := 1; j <= len2; j++ {
			cost := 0
			if r1[i-1] != r2[j-1] {
				cost = 1
			}

			// Take minimum of three operations:
			// 1. Deletion: matrix[i-1][j] + 1
			// 2. Insertion: matrix[i][j-1] + 1
			// 3. Substitution: matrix[i-1][j-1] + cost
			matrix[i][j] = min(
				matrix[i-1][j]+1,      // deletion
				matrix[i][j-1]+1,      // insertion
				matrix[i-1][j-1]+cost, // substitution
			)
		}
	}

	return matrix[len1][len2]
}

// min returns the minimum of three integers.
func min(a, b, c int) int {
	if a < b {
		if a < c {
			return a
		}
		return c
	}
	if b < c {
		return b
	}
	return c
}

// DecideAction implements the RepairEngine interface.
// It determines the appropriate action based on repair confidence:
// - confidence > 0.9 (single candidate): "auto-correct" - automatically apply the repair
// - confidence 0.5-0.9 (multiple candidates): "present-options" - show options to user
// - confidence < 0.5: "fail-to-backend" - send to backend for AI-based intent inference
//
// This implements the confidence-based decision logic specified in Requirements 1.2, 1.3.
func (r *DefaultRepairEngine) DecideAction(result *RepairResult) string {
	// High confidence with single candidate: auto-correct
	// This satisfies Requirement 1.2: auto-correct for confidence > 0.9
	if result.Confidence > 0.9 && result.Repaired != nil {
		return "auto-correct"
	}

	// Medium confidence with multiple candidates: present options
	// This satisfies Requirement 1.3: present options for confidence 0.5-0.9
	if result.Confidence >= 0.5 && result.Confidence <= 0.9 && len(result.Candidates) > 0 {
		return "present-options"
	}

	// Low confidence: fail gracefully and send to backend
	// This satisfies Requirement 1.4: invoke Intent_Inference_Service when deterministic repair fails
	return "fail-to-backend"
}

// RepairWithDecision combines repair and decision logic into a single operation.
// This is a convenience method that performs repair and returns both the result
// and the recommended action.
//
// Returns:
// - RepairResult: the repair result with confidence and candidates
// - string: the recommended action ("auto-correct", "present-options", "fail-to-backend")
// - error: any error that occurred during repair
func (r *DefaultRepairEngine) RepairWithDecision(cmd *Command) (*RepairResult, string, error) {
	result, err := r.Repair(cmd)
	if err != nil {
		return nil, "", err
	}

	action := r.DecideAction(result)
	return result, action, nil
}
