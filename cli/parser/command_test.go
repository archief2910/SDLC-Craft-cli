package parser

import (
	"testing"
	"time"
)

// TestCommandInitialization tests that Command is properly initialized
func TestCommandInitialization(t *testing.T) {
	raw := "sdlc status project"
	cmd := NewCommand(raw)

	// Verify raw input is preserved
	if cmd.Raw != raw {
		t.Errorf("Expected Raw to be '%s', got '%s'", raw, cmd.Raw)
	}

	// Verify modifiers map is initialized
	if cmd.Modifiers == nil {
		t.Fatal("Expected Modifiers map to be initialized, got nil")
	}

	// Verify modifiers map is empty
	if len(cmd.Modifiers) != 0 {
		t.Errorf("Expected Modifiers map to be empty, got %d entries", len(cmd.Modifiers))
	}

	// Verify IsValid defaults to false
	if cmd.IsValid {
		t.Error("Expected IsValid to be false by default")
	}

	// Verify timestamp is set
	if cmd.Timestamp.IsZero() {
		t.Error("Expected Timestamp to be set")
	}

	// Verify timestamp is recent (within last second)
	now := time.Now()
	if now.Sub(cmd.Timestamp) > time.Second {
		t.Errorf("Expected Timestamp to be recent, got %v", cmd.Timestamp)
	}

	// Verify optional fields are empty
	if cmd.ID != "" {
		t.Error("Expected ID to be empty by default")
	}

	if cmd.Intent != "" {
		t.Error("Expected Intent to be empty by default")
	}

	if cmd.Target != "" {
		t.Error("Expected Target to be empty by default")
	}

	if cmd.UserID != "" {
		t.Error("Expected UserID to be empty by default")
	}

	if cmd.ProjectPath != "" {
		t.Error("Expected ProjectPath to be empty by default")
	}
}

// TestCommandModifiersManipulation tests that modifiers can be added and retrieved
func TestCommandModifiersManipulation(t *testing.T) {
	cmd := NewCommand("test input")

	// Add modifiers
	cmd.Modifiers["verbose"] = "true"
	cmd.Modifiers["format"] = "json"
	cmd.Modifiers["env"] = "production"

	// Verify modifiers are stored correctly
	if cmd.Modifiers["verbose"] != "true" {
		t.Errorf("Expected verbose to be 'true', got '%s'", cmd.Modifiers["verbose"])
	}

	if cmd.Modifiers["format"] != "json" {
		t.Errorf("Expected format to be 'json', got '%s'", cmd.Modifiers["format"])
	}

	if cmd.Modifiers["env"] != "production" {
		t.Errorf("Expected env to be 'production', got '%s'", cmd.Modifiers["env"])
	}

	// Verify count
	if len(cmd.Modifiers) != 3 {
		t.Errorf("Expected 3 modifiers, got %d", len(cmd.Modifiers))
	}
}

// TestCommandFieldAssignment tests that all fields can be assigned
func TestCommandFieldAssignment(t *testing.T) {
	cmd := NewCommand("sdlc status")

	// Assign all fields
	cmd.ID = "test-id-123"
	cmd.Intent = "status"
	cmd.Target = "project"
	cmd.IsValid = true
	cmd.UserID = "user-456"
	cmd.ProjectPath = "/home/user/project"

	// Verify assignments
	if cmd.ID != "test-id-123" {
		t.Errorf("Expected ID to be 'test-id-123', got '%s'", cmd.ID)
	}

	if cmd.Intent != "status" {
		t.Errorf("Expected Intent to be 'status', got '%s'", cmd.Intent)
	}

	if cmd.Target != "project" {
		t.Errorf("Expected Target to be 'project', got '%s'", cmd.Target)
	}

	if !cmd.IsValid {
		t.Error("Expected IsValid to be true")
	}

	if cmd.UserID != "user-456" {
		t.Errorf("Expected UserID to be 'user-456', got '%s'", cmd.UserID)
	}

	if cmd.ProjectPath != "/home/user/project" {
		t.Errorf("Expected ProjectPath to be '/home/user/project', got '%s'", cmd.ProjectPath)
	}
}

// TestMultipleCommandInstances tests that multiple commands are independent
func TestMultipleCommandInstances(t *testing.T) {
	cmd1 := NewCommand("sdlc status")
	cmd2 := NewCommand("sdlc analyze security")

	// Modify cmd1
	cmd1.Intent = "status"
	cmd1.Modifiers["verbose"] = "true"

	// Modify cmd2
	cmd2.Intent = "analyze"
	cmd2.Target = "security"
	cmd2.Modifiers["format"] = "json"

	// Verify cmd1 is not affected by cmd2
	if cmd1.Intent != "status" {
		t.Error("cmd1 Intent was modified")
	}

	if _, exists := cmd1.Modifiers["format"]; exists {
		t.Error("cmd1 Modifiers were affected by cmd2")
	}

	// Verify cmd2 is not affected by cmd1
	if cmd2.Target != "security" {
		t.Error("cmd2 Target was modified")
	}

	if _, exists := cmd2.Modifiers["verbose"]; exists {
		t.Error("cmd2 Modifiers were affected by cmd1")
	}
}
