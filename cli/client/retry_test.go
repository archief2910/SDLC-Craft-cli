package client

import (
	"errors"
	"testing"
	"time"
)

// MockBackendClient is a mock implementation for testing.
type MockBackendClient struct {
	inferIntentFunc  func(*IntentRequest) (*IntentResponse, error)
	queryStateFunc   func(string) (*SDLCState, error)
	isAvailableFunc  func() bool
	executeIntentFunc func(*IntentRequest) (<-chan ExecutionEvent, error)
	callCount        int
}

func (m *MockBackendClient) InferIntent(request *IntentRequest) (*IntentResponse, error) {
	m.callCount++
	if m.inferIntentFunc != nil {
		return m.inferIntentFunc(request)
	}
	return nil, errors.New("not implemented")
}

func (m *MockBackendClient) ExecuteIntent(request *IntentRequest) (<-chan ExecutionEvent, error) {
	m.callCount++
	if m.executeIntentFunc != nil {
		return m.executeIntentFunc(request)
	}
	return nil, errors.New("not implemented")
}

func (m *MockBackendClient) QueryState(projectID string) (*SDLCState, error) {
	m.callCount++
	if m.queryStateFunc != nil {
		return m.queryStateFunc(projectID)
	}
	return nil, errors.New("not implemented")
}

func (m *MockBackendClient) IsAvailable() bool {
	if m.isAvailableFunc != nil {
		return m.isAvailableFunc()
	}
	return true
}

func (m *MockBackendClient) Close() error {
	return nil
}

// TestDefaultRetryConfig tests default retry configuration.
func TestDefaultRetryConfig(t *testing.T) {
	config := DefaultRetryConfig()

	if config.MaxAttempts != 3 {
		t.Errorf("MaxAttempts = %d, want 3", config.MaxAttempts)
	}

	if config.InitialDelay != 1*time.Second {
		t.Errorf("InitialDelay = %v, want 1s", config.InitialDelay)
	}

	if config.MaxDelay != 10*time.Second {
		t.Errorf("MaxDelay = %v, want 10s", config.MaxDelay)
	}

	if config.BackoffMultiplier != 2.0 {
		t.Errorf("BackoffMultiplier = %f, want 2.0", config.BackoffMultiplier)
	}
}

// TestRetryableBackendClient_Success tests successful request without retry.
func TestRetryableBackendClient_Success(t *testing.T) {
	mock := &MockBackendClient{
		inferIntentFunc: func(req *IntentRequest) (*IntentResponse, error) {
			return &IntentResponse{Intent: "status"}, nil
		},
	}

	config := RetryConfig{
		MaxAttempts:     3,
		InitialDelay:    10 * time.Millisecond,
		MaxDelay:        100 * time.Millisecond,
		BackoffMultiplier: 2.0,
	}

	client := NewRetryableBackendClient(mock, config)

	request := &IntentRequest{RawCommand: "status"}
	response, err := client.InferIntent(request)

	if err != nil {
		t.Fatalf("InferIntent() error = %v", err)
	}

	if response.Intent != "status" {
		t.Errorf("Intent = %s, want status", response.Intent)
	}

	if mock.callCount != 1 {
		t.Errorf("callCount = %d, want 1 (no retries)", mock.callCount)
	}
}

// TestRetryableBackendClient_RetrySuccess tests successful retry.
func TestRetryableBackendClient_RetrySuccess(t *testing.T) {
	attemptCount := 0

	mock := &MockBackendClient{
		inferIntentFunc: func(req *IntentRequest) (*IntentResponse, error) {
			attemptCount++
			if attemptCount < 3 {
				return nil, errors.New("temporary error")
			}
			return &IntentResponse{Intent: "status"}, nil
		},
	}

	config := RetryConfig{
		MaxAttempts:     3,
		InitialDelay:    10 * time.Millisecond,
		MaxDelay:        100 * time.Millisecond,
		BackoffMultiplier: 2.0,
	}

	client := NewRetryableBackendClient(mock, config)

	request := &IntentRequest{RawCommand: "status"}
	response, err := client.InferIntent(request)

	if err != nil {
		t.Fatalf("InferIntent() error = %v", err)
	}

	if response.Intent != "status" {
		t.Errorf("Intent = %s, want status", response.Intent)
	}

	if attemptCount != 3 {
		t.Errorf("attemptCount = %d, want 3 (2 retries)", attemptCount)
	}
}

// TestRetryableBackendClient_RetryFailure tests exhausted retries.
func TestRetryableBackendClient_RetryFailure(t *testing.T) {
	mock := &MockBackendClient{
		inferIntentFunc: func(req *IntentRequest) (*IntentResponse, error) {
			return nil, errors.New("persistent error")
		},
	}

	config := RetryConfig{
		MaxAttempts:     3,
		InitialDelay:    10 * time.Millisecond,
		MaxDelay:        100 * time.Millisecond,
		BackoffMultiplier: 2.0,
	}

	client := NewRetryableBackendClient(mock, config)

	request := &IntentRequest{RawCommand: "status"}
	_, err := client.InferIntent(request)

	if err == nil {
		t.Fatal("InferIntent() expected error, got nil")
	}

	if mock.callCount != 3 {
		t.Errorf("callCount = %d, want 3 (all attempts exhausted)", mock.callCount)
	}
}

// TestRetryableBackendClient_QueryState tests retry for QueryState.
func TestRetryableBackendClient_QueryState(t *testing.T) {
	attemptCount := 0

	mock := &MockBackendClient{
		queryStateFunc: func(projectID string) (*SDLCState, error) {
			attemptCount++
			if attemptCount < 2 {
				return nil, errors.New("temporary error")
			}
			return &SDLCState{ProjectID: projectID}, nil
		},
	}

	config := RetryConfig{
		MaxAttempts:     3,
		InitialDelay:    10 * time.Millisecond,
		MaxDelay:        100 * time.Millisecond,
		BackoffMultiplier: 2.0,
	}

	client := NewRetryableBackendClient(mock, config)

	state, err := client.QueryState("project123")

	if err != nil {
		t.Fatalf("QueryState() error = %v", err)
	}

	if state.ProjectID != "project123" {
		t.Errorf("ProjectID = %s, want project123", state.ProjectID)
	}

	if attemptCount != 2 {
		t.Errorf("attemptCount = %d, want 2 (1 retry)", attemptCount)
	}
}

// TestRetryableBackendClient_ExecuteIntent tests that ExecuteIntent is not retried.
func TestRetryableBackendClient_ExecuteIntent(t *testing.T) {
	mock := &MockBackendClient{
		executeIntentFunc: func(req *IntentRequest) (<-chan ExecutionEvent, error) {
			return nil, errors.New("error")
		},
	}

	config := DefaultRetryConfig()
	client := NewRetryableBackendClient(mock, config)

	request := &IntentRequest{RawCommand: "status"}
	_, err := client.ExecuteIntent(request)

	if err == nil {
		t.Fatal("ExecuteIntent() expected error, got nil")
	}

	// ExecuteIntent should not be retried (side effects)
	if mock.callCount != 1 {
		t.Errorf("callCount = %d, want 1 (no retries for execution)", mock.callCount)
	}
}

// TestCalculateDelay tests exponential backoff calculation.
func TestCalculateDelay(t *testing.T) {
	config := RetryConfig{
		InitialDelay:    1 * time.Second,
		MaxDelay:        10 * time.Second,
		BackoffMultiplier: 2.0,
	}

	client := &RetryableBackendClient{config: config}

	tests := []struct {
		attempt int
		want    time.Duration
	}{
		{1, 1 * time.Second},  // 1 * 2^0 = 1
		{2, 2 * time.Second},  // 1 * 2^1 = 2
		{3, 4 * time.Second},  // 1 * 2^2 = 4
		{4, 8 * time.Second},  // 1 * 2^3 = 8
		{5, 10 * time.Second}, // 1 * 2^4 = 16, capped at 10
		{6, 10 * time.Second}, // 1 * 2^5 = 32, capped at 10
	}

	for _, tt := range tests {
		got := client.calculateDelay(tt.attempt)
		if got != tt.want {
			t.Errorf("calculateDelay(%d) = %v, want %v", tt.attempt, got, tt.want)
		}
	}
}

// TestRetryableBackendClient_IsAvailable tests IsAvailable passthrough.
func TestRetryableBackendClient_IsAvailable(t *testing.T) {
	mock := &MockBackendClient{
		isAvailableFunc: func() bool {
			return true
		},
	}

	config := DefaultRetryConfig()
	client := NewRetryableBackendClient(mock, config)

	if !client.IsAvailable() {
		t.Error("IsAvailable() = false, want true")
	}
}

// TestRetryableBackendClient_Close tests Close passthrough.
func TestRetryableBackendClient_Close(t *testing.T) {
	mock := &MockBackendClient{}

	config := DefaultRetryConfig()
	client := NewRetryableBackendClient(mock, config)

	err := client.Close()
	if err != nil {
		t.Errorf("Close() error = %v", err)
	}
}
