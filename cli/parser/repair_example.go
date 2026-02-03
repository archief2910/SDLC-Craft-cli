package parser

import "fmt"

// ExampleRepairEngine demonstrates the usage of the RepairEngine.
// This file serves as both documentation and a manual verification tool.
func ExampleRepairEngine() {
	// Create parser and repair engine
	parser := NewDefaultParser()
	engine := NewDefaultRepairEngine(parser)

	// Example 1: Valid command (no repair needed)
	fmt.Println("=== Example 1: Valid Command ===")
	cmd1, _ := parser.Parse("sdlc status project")
	result1, _ := engine.Repair(cmd1)
	fmt.Printf("Original: %s\n", cmd1.Raw)
	fmt.Printf("Confidence: %.2f\n", result1.Confidence)
	fmt.Printf("Explanation: %s\n\n", result1.Explanation)

	// Example 2: Intent typo (single character)
	fmt.Println("=== Example 2: Intent Typo ===")
	cmd2 := NewCommand("sdlc statuz project")
	parser.parseStructured(cmd2)
	result2, _ := engine.Repair(cmd2)
	fmt.Printf("Original: %s\n", cmd2.Raw)
	if result2.Repaired != nil {
		fmt.Printf("Repaired Intent: %s\n", result2.Repaired.Intent)
		fmt.Printf("Confidence: %.2f\n", result2.Confidence)
		fmt.Printf("Explanation: %s\n\n", result2.Explanation)
	}

	// Example 3: Synonym expansion
	fmt.Println("=== Example 3: Synonym Expansion ===")
	cmd3 := NewCommand("sdlc check project")
	parser.parseStructured(cmd3)
	result3, _ := engine.Repair(cmd3)
	fmt.Printf("Original: %s\n", cmd3.Raw)
	if result3.Repaired != nil {
		fmt.Printf("Repaired Intent: %s\n", result3.Repaired.Intent)
		fmt.Printf("Confidence: %.2f\n", result3.Confidence)
		fmt.Printf("Explanation: %s\n\n", result3.Explanation)
	}

	// Example 4: Target typo
	fmt.Println("=== Example 4: Target Typo ===")
	cmd4 := NewCommand("sdlc analyze securty")
	parser.parseStructured(cmd4)
	result4, _ := engine.Repair(cmd4)
	fmt.Printf("Original: %s\n", cmd4.Raw)
	if result4.Repaired != nil {
		fmt.Printf("Repaired Target: %s\n", result4.Repaired.Target)
		fmt.Printf("Confidence: %.2f\n", result4.Confidence)
		fmt.Printf("Explanation: %s\n\n", result4.Explanation)
	}

	// Example 5: Unrepairable command
	fmt.Println("=== Example 5: Unrepairable Command ===")
	cmd5 := NewCommand("sdlc xyz abc")
	parser.parseStructured(cmd5)
	result5, _ := engine.Repair(cmd5)
	fmt.Printf("Original: %s\n", cmd5.Raw)
	fmt.Printf("Confidence: %.2f\n", result5.Confidence)
	fmt.Printf("Explanation: %s\n\n", result5.Explanation)

	// Example 6: Modifiers preserved
	fmt.Println("=== Example 6: Modifiers Preserved ===")
	cmd6 := NewCommand("sdlc statuz project --verbose --format=json")
	parser.parseStructured(cmd6)
	result6, _ := engine.Repair(cmd6)
	fmt.Printf("Original: %s\n", cmd6.Raw)
	if result6.Repaired != nil {
		fmt.Printf("Repaired Intent: %s\n", result6.Repaired.Intent)
		fmt.Printf("Modifiers: %v\n", result6.Repaired.Modifiers)
		fmt.Printf("Confidence: %.2f\n", result6.Confidence)
		fmt.Printf("Explanation: %s\n\n", result6.Explanation)
	}

	// Output:
	// === Example 1: Valid Command ===
	// Original: sdlc status project
	// Confidence: 1.00
	// Explanation: Command is already valid
	//
	// === Example 2: Intent Typo ===
	// Original: sdlc statuz project
	// Repaired Intent: status
	// Confidence: 0.95
	// Explanation: Corrected intent typo 'statuz' to 'status'
	//
	// === Example 3: Synonym Expansion ===
	// Original: sdlc check project
	// Repaired Intent: status
	// Confidence: 0.95
	// Explanation: Expanded synonym 'check' to intent 'status'
	//
	// === Example 4: Target Typo ===
	// Original: sdlc analyze securty
	// Repaired Target: security
	// Confidence: 0.95
	// Explanation: Corrected target typo 'securty' to 'security'
	//
	// === Example 5: Unrepairable Command ===
	// Original: sdlc xyz abc
	// Confidence: 0.00
	// Explanation: Unable to repair command deterministically, requires intent inference
	//
	// === Example 6: Modifiers Preserved ===
	// Original: sdlc statuz project --verbose --format=json
	// Repaired Intent: status
	// Modifiers: map[format:json verbose:true]
	// Confidence: 0.95
	// Explanation: Corrected intent typo 'statuz' to 'status'
}
