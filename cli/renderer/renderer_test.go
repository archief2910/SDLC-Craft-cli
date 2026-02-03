package renderer

import (
	"bytes"
	"errors"
	"strings"
	"testing"
	"time"
)

// TestNewDefaultRenderer tests the creation of a default renderer.
func TestNewDefaultRenderer(t *testing.T) {
	renderer := NewDefaultRenderer()

	if renderer == nil {
		t.Fatal("NewDefaultRenderer() returned nil")
	}

	if renderer.output == nil {
		t.Error("renderer.output is nil")
	}

	if renderer.verbose {
		t.Error("renderer.verbose should be false by default")
	}

	if renderer.successColor == nil {
		t.Error("renderer.successColor is nil")
	}

	if renderer.errorColor == nil {
		t.Error("renderer.errorColor is nil")
	}
}

// TestDisplayResult tests displaying various result types.
func TestDisplayResult(t *testing.T) {
	tests := []struct {
		name     string
		result   interface{}
		contains string
	}{
		{
			name:     "string result",
			result:   "Command executed successfully",
			contains: "Command executed successfully",
		},
		{
			name:     "nil result",
			result:   nil,
			contains: "",
		},
		{
			name:     "map result",
			result:   map[string]interface{}{"status": "ok", "count": 42},
			contains: "status",
		},
		{
			name:     "integer result",
			result:   42,
			contains: "42",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			buf := &bytes.Buffer{}
			renderer := NewDefaultRenderer()
			renderer.SetOutput(buf)

			renderer.DisplayResult(tt.result)

			output := buf.String()
			if tt.contains != "" && !strings.Contains(output, tt.contains) {
				t.Errorf("DisplayResult() output = %q, want to contain %q", output, tt.contains)
			}
		})
	}
}

// TestDisplayProgress tests displaying progress events.
func TestDisplayProgress(t *testing.T) {
	tests := []struct {
		name     string
		event    ExecutionEvent
		contains string
	}{
		{
			name: "progress event with percentage",
			event: ExecutionEvent{
				Type:      "progress",
				Message:   "Processing files",
				Progress:  50,
				Timestamp: time.Now(),
			},
			contains: "Processing files",
		},
		{
			name: "status event",
			event: ExecutionEvent{
				Type:      "status",
				Message:   "Analyzing code",
				Timestamp: time.Now(),
			},
			contains: "Analyzing code",
		},
		{
			name: "error event",
			event: ExecutionEvent{
				Type:      "error",
				Message:   "Failed to connect",
				Timestamp: time.Now(),
			},
			contains: "ERROR",
		},
		{
			name: "completion event",
			event: ExecutionEvent{
				Type:      "completion",
				Message:   "Analysis complete",
				Timestamp: time.Now(),
			},
			contains: "Analysis complete",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			buf := &bytes.Buffer{}
			renderer := NewDefaultRenderer()
			renderer.SetOutput(buf)

			renderer.DisplayProgress(tt.event)

			output := buf.String()
			if !strings.Contains(output, tt.contains) {
				t.Errorf("DisplayProgress() output = %q, want to contain %q", output, tt.contains)
			}
		})
	}
}

// TestDisplayProgress_VerboseMode tests that agent reasoning is only shown in verbose mode.
func TestDisplayProgress_VerboseMode(t *testing.T) {
	event := ExecutionEvent{
		Type:      "agent_reasoning",
		Message:   "Planning execution steps",
		Timestamp: time.Now(),
	}

	// Test non-verbose mode (should not show agent reasoning)
	t.Run("non-verbose", func(t *testing.T) {
		buf := &bytes.Buffer{}
		renderer := NewDefaultRenderer()
		renderer.SetOutput(buf)
		renderer.SetVerbose(false)

		renderer.DisplayProgress(event)

		output := buf.String()
		if strings.Contains(output, "Planning execution steps") {
			t.Error("Agent reasoning shown in non-verbose mode")
		}
	})

	// Test verbose mode (should show agent reasoning)
	t.Run("verbose", func(t *testing.T) {
		buf := &bytes.Buffer{}
		renderer := NewDefaultRenderer()
		renderer.SetOutput(buf)
		renderer.SetVerbose(true)

		renderer.DisplayProgress(event)

		output := buf.String()
		if !strings.Contains(output, "Planning execution steps") {
			t.Error("Agent reasoning not shown in verbose mode")
		}
	})
}

// TestDisplayError tests error display.
func TestDisplayError(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		contains string
	}{
		{
			name:     "simple error",
			err:      errors.New("connection failed"),
			contains: "connection failed",
		},
		{
			name:     "nil error",
			err:      nil,
			contains: "",
		},
		{
			name:     "wrapped error",
			err:      errors.New("database error: connection timeout"),
			contains: "database error",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			buf := &bytes.Buffer{}
			renderer := NewDefaultRenderer()
			renderer.SetOutput(buf)

			renderer.DisplayError(tt.err)

			output := buf.String()
			if tt.contains != "" && !strings.Contains(output, tt.contains) {
				t.Errorf("DisplayError() output = %q, want to contain %q", output, tt.contains)
			}
		})
	}
}

// TestDisplayAutoCorrection tests auto-correction display.
func TestDisplayAutoCorrection(t *testing.T) {
	tests := []struct {
		name        string
		original    string
		corrected   string
		explanation string
		wantOriginal bool
		wantCorrected bool
		wantExplanation bool
	}{
		{
			name:        "with explanation",
			original:    "sdlc stauts security",
			corrected:   "sdlc status security",
			explanation: "Corrected typo 'stauts' to 'status'",
			wantOriginal: true,
			wantCorrected: true,
			wantExplanation: true,
		},
		{
			name:        "without explanation",
			original:    "sdlc check security",
			corrected:   "sdlc status security",
			explanation: "",
			wantOriginal: true,
			wantCorrected: true,
			wantExplanation: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			buf := &bytes.Buffer{}
			renderer := NewDefaultRenderer()
			renderer.SetOutput(buf)

			renderer.DisplayAutoCorrection(tt.original, tt.corrected, tt.explanation)

			output := buf.String()

			if tt.wantOriginal && !strings.Contains(output, tt.original) {
				t.Errorf("DisplayAutoCorrection() output missing original: %q", tt.original)
			}

			if tt.wantCorrected && !strings.Contains(output, tt.corrected) {
				t.Errorf("DisplayAutoCorrection() output missing corrected: %q", tt.corrected)
			}

			if tt.wantExplanation && !strings.Contains(output, tt.explanation) {
				t.Errorf("DisplayAutoCorrection() output missing explanation: %q", tt.explanation)
			}
		})
	}
}

// TestSetVerbose tests verbose mode toggling.
func TestSetVerbose(t *testing.T) {
	buf := &bytes.Buffer{}
	renderer := NewDefaultRenderer()
	renderer.SetOutput(buf)

	// Initially not verbose
	if renderer.verbose {
		t.Error("renderer should not be verbose initially")
	}

	// Enable verbose
	renderer.SetVerbose(true)
	if !renderer.verbose {
		t.Error("renderer.verbose should be true after SetVerbose(true)")
	}

	output := buf.String()
	if !strings.Contains(output, "Verbose mode enabled") {
		t.Error("SetVerbose(true) should display confirmation message")
	}

	// Disable verbose
	renderer.SetVerbose(false)
	if renderer.verbose {
		t.Error("renderer.verbose should be false after SetVerbose(false)")
	}
}

// TestSetOutput tests output writer configuration.
func TestSetOutput(t *testing.T) {
	renderer := NewDefaultRenderer()

	buf := &bytes.Buffer{}
	renderer.SetOutput(buf)

	if renderer.output != buf {
		t.Error("SetOutput() did not set the output writer")
	}

	// Test that output goes to the new writer
	renderer.DisplayResult("test message")

	output := buf.String()
	if !strings.Contains(output, "test message") {
		t.Error("Output not written to custom writer")
	}
}

// TestDisplayProgressBar tests progress bar rendering.
func TestDisplayProgressBar(t *testing.T) {
	tests := []struct {
		name     string
		message  string
		progress int
		contains string
	}{
		{
			name:     "0% progress",
			message:  "Starting",
			progress: 0,
			contains: "0%",
		},
		{
			name:     "50% progress",
			message:  "Processing",
			progress: 50,
			contains: "50%",
		},
		{
			name:     "100% progress",
			message:  "Complete",
			progress: 100,
			contains: "100%",
		},
		{
			name:     "negative progress (clamped to 0)",
			message:  "Invalid",
			progress: -10,
			contains: "0%",
		},
		{
			name:     "over 100 progress (clamped to 100)",
			message:  "Invalid",
			progress: 150,
			contains: "100%",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			buf := &bytes.Buffer{}
			renderer := NewDefaultRenderer()
			renderer.SetOutput(buf)

			renderer.displayProgressBar(tt.message, tt.progress)

			output := buf.String()
			if !strings.Contains(output, tt.contains) {
				t.Errorf("displayProgressBar() output = %q, want to contain %q", output, tt.contains)
			}

			if !strings.Contains(output, tt.message) {
				t.Errorf("displayProgressBar() output missing message: %q", tt.message)
			}
		})
	}
}

// TestDisplayMap tests nested map display.
func TestDisplayMap(t *testing.T) {
	buf := &bytes.Buffer{}
	renderer := NewDefaultRenderer()
	renderer.SetOutput(buf)

	testMap := map[string]interface{}{
		"status": "ok",
		"count":  42,
		"nested": map[string]interface{}{
			"key1": "value1",
			"key2": "value2",
		},
		"array": []interface{}{"item1", "item2", "item3"},
	}

	renderer.displayMap(testMap, 0)

	output := buf.String()

	// Check that all keys are present
	expectedKeys := []string{"status", "count", "nested", "array", "key1", "key2"}
	for _, key := range expectedKeys {
		if !strings.Contains(output, key) {
			t.Errorf("displayMap() output missing key: %q", key)
		}
	}

	// Check that values are present
	expectedValues := []string{"ok", "42", "value1", "value2", "item1"}
	for _, value := range expectedValues {
		if !strings.Contains(output, value) {
			t.Errorf("displayMap() output missing value: %q", value)
		}
	}
}

// TestSpinner tests spinner creation and basic functionality.
func TestSpinner(t *testing.T) {
	buf := &bytes.Buffer{}
	renderer := NewDefaultRenderer()
	renderer.SetOutput(buf)

	spinner := renderer.NewSpinner("Loading")

	if spinner == nil {
		t.Fatal("NewSpinner() returned nil")
	}

	if spinner.message != "Loading" {
		t.Errorf("spinner.message = %q, want %q", spinner.message, "Loading")
	}

	if spinner.active {
		t.Error("spinner should not be active initially")
	}

	if len(spinner.frames) == 0 {
		t.Error("spinner.frames should not be empty")
	}
}

// TestExecutionEvent tests ExecutionEvent structure.
func TestExecutionEvent(t *testing.T) {
	event := ExecutionEvent{
		Type:      "progress",
		Message:   "Processing",
		Timestamp: time.Now(),
		Progress:  50,
		Metadata: map[string]interface{}{
			"file": "test.go",
		},
	}

	if event.Type != "progress" {
		t.Errorf("event.Type = %q, want %q", event.Type, "progress")
	}

	if event.Message != "Processing" {
		t.Errorf("event.Message = %q, want %q", event.Message, "Processing")
	}

	if event.Progress != 50 {
		t.Errorf("event.Progress = %d, want %d", event.Progress, 50)
	}

	if event.Metadata["file"] != "test.go" {
		t.Error("event.Metadata missing expected key")
	}
}

// TestRendererInterface tests that DefaultRenderer implements Renderer interface.
func TestRendererInterface(t *testing.T) {
	var _ Renderer = (*DefaultRenderer)(nil)
}

// TestDisplayResult_ErrorType tests that errors are displayed correctly.
func TestDisplayResult_ErrorType(t *testing.T) {
	buf := &bytes.Buffer{}
	renderer := NewDefaultRenderer()
	renderer.SetOutput(buf)

	err := errors.New("test error")
	renderer.DisplayResult(err)

	output := buf.String()
	if !strings.Contains(output, "test error") {
		t.Errorf("DisplayResult(error) output = %q, want to contain %q", output, "test error")
	}
}

// TestDisplayProgress_AllEventTypes tests all event types.
func TestDisplayProgress_AllEventTypes(t *testing.T) {
	eventTypes := []string{"progress", "status", "error", "completion", "agent_reasoning", "unknown"}

	for _, eventType := range eventTypes {
		t.Run(eventType, func(t *testing.T) {
			buf := &bytes.Buffer{}
			renderer := NewDefaultRenderer()
			renderer.SetOutput(buf)

			if eventType == "agent_reasoning" {
				renderer.SetVerbose(true)
			}

			event := ExecutionEvent{
				Type:      eventType,
				Message:   "Test message",
				Progress:  50,
				Timestamp: time.Now(),
			}

			// Should not panic
			renderer.DisplayProgress(event)

			output := buf.String()
			// All events should produce some output (except agent_reasoning in non-verbose)
			if eventType != "agent_reasoning" && output == "" {
				t.Error("DisplayProgress() produced no output")
			}
		})
	}
}

// TestDisplayAutoCorrection_EmptyStrings tests auto-correction with empty strings.
func TestDisplayAutoCorrection_EmptyStrings(t *testing.T) {
	buf := &bytes.Buffer{}
	renderer := NewDefaultRenderer()
	renderer.SetOutput(buf)

	// Should not panic with empty strings
	renderer.DisplayAutoCorrection("", "", "")

	output := buf.String()
	if output == "" {
		t.Error("DisplayAutoCorrection() with empty strings produced no output")
	}
}

// TestDisplayError_VerboseMode tests error display in verbose mode.
func TestDisplayError_VerboseMode(t *testing.T) {
	buf := &bytes.Buffer{}
	renderer := NewDefaultRenderer()
	renderer.SetOutput(buf)
	renderer.SetVerbose(true)

	err := errors.New("test error")
	renderer.DisplayError(err)

	output := buf.String()
	if !strings.Contains(output, "test error") {
		t.Error("DisplayError() in verbose mode missing error message")
	}

	// In verbose mode, should show additional details
	if !strings.Contains(output, "Details") {
		t.Error("DisplayError() in verbose mode missing details")
	}
}
