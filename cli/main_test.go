package main

import (
	"testing"
)

// TestRootCommand verifies that the root command is properly initialized
func TestRootCommand(t *testing.T) {
	if rootCmd == nil {
		t.Fatal("rootCmd should not be nil")
	}

	if rootCmd.Use != "sdlc" {
		t.Errorf("Expected Use to be 'sdlc', got '%s'", rootCmd.Use)
	}

	if rootCmd.Short == "" {
		t.Error("Short description should not be empty")
	}

	if rootCmd.Long == "" {
		t.Error("Long description should not be empty")
	}
}
