package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// TestNewHTTPBackendClient tests client creation.
func TestNewHTTPBackendClient(t *testing.T) {
	client := NewHTTPBackendClient("http://localhost:8080", 30*time.Second)

	if client == nil {
		t.Fatal("NewHTTPBackendClient() returned nil")
	}

	if client.baseURL != "http://localhost:8080" {
		t.Errorf("baseURL = %s, want http://localhost:8080", client.baseURL)
	}

	if client.timeout != 30*time.Second {
		t.Errorf("timeout = %v, want 30s", client.timeout)
	}
}

// TestNewHTTPBackendClient_DefaultTimeout tests default timeout.
func TestNewHTTPBackendClient_DefaultTimeout(t *testing.T) {
	client := NewHTTPBackendClient("http://localhost:8080", 0)

	if client.timeout != 30*time.Second {
		t.Errorf("timeout = %v, want 30s (default)", client.timeout)
	}
}

// TestInferIntent_Success tests successful intent inference.
func TestInferIntent_Success(t *testing.T) {
	// Create mock server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Verify request
		if r.Method != "POST" {
			t.Errorf("Method = %s, want POST", r.Method)
		}

		if r.URL.Path != "/api/intent/infer" {
			t.Errorf("Path = %s, want /api/intent/infer", r.URL.Path)
		}

		// Parse request
		var req IntentRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			t.Errorf("Failed to decode request: %v", err)
		}

		// Send response
		response := IntentResponse{
			Intent:      "analyze",
			Target:      "security",
			Confidence:  0.9,
			Explanation: "Inferred from command",
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	// Create client
	client := NewHTTPBackendClient(server.URL, 5*time.Second)

	// Test InferIntent
	request := &IntentRequest{
		RawCommand: "analyze security",
		UserID:     "user123",
		ProjectID:  "project456",
	}

	response, err := client.InferIntent(request)
	if err != nil {
		t.Fatalf("InferIntent() error = %v", err)
	}

	if response.Intent != "analyze" {
		t.Errorf("Intent = %s, want analyze", response.Intent)
	}

	if response.Target != "security" {
		t.Errorf("Target = %s, want security", response.Target)
	}

	if response.Confidence != 0.9 {
		t.Errorf("Confidence = %f, want 0.9", response.Confidence)
	}
}

// TestInferIntent_ServerError tests handling of server errors.
func TestInferIntent_ServerError(t *testing.T) {
	// Create mock server that returns error
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("Internal server error"))
	}))
	defer server.Close()

	// Create client
	client := NewHTTPBackendClient(server.URL, 5*time.Second)

	// Test InferIntent
	request := &IntentRequest{
		RawCommand: "analyze security",
	}

	_, err := client.InferIntent(request)
	if err == nil {
		t.Error("InferIntent() expected error, got nil")
	}
}

// TestQueryState_Success tests successful state query.
func TestQueryState_Success(t *testing.T) {
	// Create mock server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Verify request
		if r.Method != "GET" {
			t.Errorf("Method = %s, want GET", r.Method)
		}

		if r.URL.Path != "/api/state/project123" {
			t.Errorf("Path = %s, want /api/state/project123", r.URL.Path)
		}

		// Send response
		state := SDLCState{
			ProjectID:        "project123",
			CurrentPhase:     "DEVELOPMENT",
			RiskLevel:        "LOW",
			TestCoverage:     0.85,
			OpenIssues:       5,
			ReleaseReadiness: 0.75,
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(state)
	}))
	defer server.Close()

	// Create client
	client := NewHTTPBackendClient(server.URL, 5*time.Second)

	// Test QueryState
	state, err := client.QueryState("project123")
	if err != nil {
		t.Fatalf("QueryState() error = %v", err)
	}

	if state.ProjectID != "project123" {
		t.Errorf("ProjectID = %s, want project123", state.ProjectID)
	}

	if state.CurrentPhase != "DEVELOPMENT" {
		t.Errorf("CurrentPhase = %s, want DEVELOPMENT", state.CurrentPhase)
	}

	if state.TestCoverage != 0.85 {
		t.Errorf("TestCoverage = %f, want 0.85", state.TestCoverage)
	}
}

// TestIsAvailable_Success tests successful health check.
func TestIsAvailable_Success(t *testing.T) {
	// Create mock server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/actuator/health" {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{"status":"UP"}`))
		}
	}))
	defer server.Close()

	// Create client
	client := NewHTTPBackendClient(server.URL, 5*time.Second)

	// Test IsAvailable
	if !client.IsAvailable() {
		t.Error("IsAvailable() = false, want true")
	}
}

// TestIsAvailable_Failure tests failed health check.
func TestIsAvailable_Failure(t *testing.T) {
	// Create client with invalid URL
	client := NewHTTPBackendClient("http://localhost:99999", 1*time.Second)

	// Test IsAvailable
	if client.IsAvailable() {
		t.Error("IsAvailable() = true, want false")
	}
}

// TestExecuteIntent tests command execution.
func TestExecuteIntent(t *testing.T) {
	client := NewHTTPBackendClient("http://localhost:8080", 5*time.Second)

	request := &IntentRequest{
		RawCommand: "analyze security",
	}

	events, err := client.ExecuteIntent(request)
	if err != nil {
		t.Fatalf("ExecuteIntent() error = %v", err)
	}

	// Read events
	eventCount := 0
	for event := range events {
		eventCount++
		if event.Type == "" {
			t.Error("Event type is empty")
		}
	}

	if eventCount == 0 {
		t.Error("No events received")
	}
}

// TestClose tests client cleanup.
func TestClose(t *testing.T) {
	client := NewHTTPBackendClient("http://localhost:8080", 5*time.Second)

	err := client.Close()
	if err != nil {
		t.Errorf("Close() error = %v", err)
	}
}
