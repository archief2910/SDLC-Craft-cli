package parser_test

import (
	"fmt"

	"github.com/sdlcraft/cli/parser"
)

// ExampleDefaultParser_Parse demonstrates basic command parsing
func ExampleDefaultParser_Parse() {
	p := parser.NewDefaultParser()

	// Parse a simple status command
	cmd, err := p.Parse("sdlc status")
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Intent: %s\n", cmd.Intent)
	fmt.Printf("Valid: %v\n", cmd.IsValid)
	// Output:
	// Intent: status
	// Valid: true
}

// ExampleDefaultParser_Parse_withTarget demonstrates parsing with a target
func ExampleDefaultParser_Parse_withTarget() {
	p := parser.NewDefaultParser()

	cmd, err := p.Parse("sdlc analyze security")
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Intent: %s\n", cmd.Intent)
	fmt.Printf("Target: %s\n", cmd.Target)
	fmt.Printf("Valid: %v\n", cmd.IsValid)
	// Output:
	// Intent: analyze
	// Target: security
	// Valid: true
}

// ExampleDefaultParser_Parse_withModifiers demonstrates parsing with modifiers
func ExampleDefaultParser_Parse_withModifiers() {
	p := parser.NewDefaultParser()

	cmd, err := p.Parse("sdlc status project --verbose --format=json")
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Intent: %s\n", cmd.Intent)
	fmt.Printf("Target: %s\n", cmd.Target)
	fmt.Printf("Verbose: %s\n", cmd.Modifiers["verbose"])
	fmt.Printf("Format: %s\n", cmd.Modifiers["format"])
	// Output:
	// Intent: status
	// Target: project
	// Verbose: true
	// Format: json
}

// ExampleDefaultParser_ValidateGrammar demonstrates grammar validation
func ExampleDefaultParser_ValidateGrammar() {
	p := parser.NewDefaultParser()

	// Valid command
	validCmd := &parser.Command{
		Intent: "status",
		Target: "project",
	}

	err := p.ValidateGrammar(validCmd)
	fmt.Printf("Valid command error: %v\n", err)

	// Invalid command (analyze without target)
	invalidCmd := &parser.Command{
		Intent: "analyze",
		Target: "",
	}

	err = p.ValidateGrammar(invalidCmd)
	fmt.Printf("Invalid command has error: %v\n", err != nil)
	// Output:
	// Valid command error: <nil>
	// Invalid command has error: true
}

// ExampleNewCommand demonstrates creating a new command
func ExampleNewCommand() {
	cmd := parser.NewCommand("sdlc status project")

	fmt.Printf("Raw: %s\n", cmd.Raw)
	fmt.Printf("Has modifiers map: %v\n", cmd.Modifiers != nil)
	fmt.Printf("Is valid by default: %v\n", cmd.IsValid)
	// Output:
	// Raw: sdlc status project
	// Has modifiers map: true
	// Is valid by default: false
}
