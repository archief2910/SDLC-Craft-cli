package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// IntentRequest represents a request to infer intent from the backend.
type IntentRequest struct {
	RawCommand  string                 `json:"rawCommand"`
	UserID      string                 `json:"userId"`
	ProjectID   string                 `json:"projectId"`
	ProjectPath string                 `json:"projectPath"`
	Context     map[string]interface{} `json:"context"`
}

// IntentResponse represents the backend's intent inference response.
type IntentResponse struct {
	Intent                string            `json:"intent"`
	Target                string            `json:"target"`
	Modifiers             map[string]string `json:"modifiers"`
	Confidence            float64           `json:"confidence"`
	Explanation           string            `json:"explanation"`
	ClarificationQuestions []string         `json:"clarificationQuestions"`
	RequiresConfirmation  bool              `json:"requiresConfirmation"`
	RiskLevel             string            `json:"riskLevel"`
	ImpactDescription     string            `json:"impactDescription"`
}

// SDLCState represents the current SDLC state from the backend.
type SDLCState struct {
	ProjectID        string                 `json:"projectId"`
	CurrentPhase     string                 `json:"currentPhase"`
	RiskLevel        string                 `json:"riskLevel"`
	TestCoverage     float64                `json:"testCoverage"`
	OpenIssues       int                    `json:"openIssues"`
	TotalIssues      int                    `json:"totalIssues"`
	ReleaseReadiness float64                `json:"releaseReadiness"`
	CustomMetrics    map[string]interface{} `json:"customMetrics"`
	UpdatedAt        string                 `json:"updatedAt"`
}

// ExecutionEvent represents a single event during command execution.
type ExecutionEvent struct {
	Type      string                 `json:"type"`
	Message   string                 `json:"message"`
	Timestamp string                 `json:"timestamp"`
	Progress  int                    `json:"progress"`
	Metadata  map[string]interface{} `json:"metadata"`
}

// BackendClient defines the interface for communicating with the Spring Boot backend.
// It handles intent inference, state queries, and command execution.
type BackendClient interface {
	// InferIntent sends a command to the backend for intent inference.
	// Returns the inferred intent with confidence and explanation.
	InferIntent(request *IntentRequest) (*IntentResponse, error)

	// ExecuteIntent sends an intent to the backend for execution.
	// Returns a channel of execution events for real-time progress updates.
	ExecuteIntent(request *IntentRequest) (<-chan ExecutionEvent, error)

	// QueryState retrieves the current SDLC state for a project.
	QueryState(projectID string) (*SDLCState, error)

	// IsAvailable checks if the backend is reachable.
	IsAvailable() bool

	// Close closes any open connections.
	Close() error
}

// HTTPBackendClient implements BackendClient using HTTP.
type HTTPBackendClient struct {
	baseURL    string
	httpClient *http.Client
	timeout    time.Duration
}

// NewHTTPBackendClient creates a new HTTP backend client.
func NewHTTPBackendClient(baseURL string, timeout time.Duration) *HTTPBackendClient {
	if timeout == 0 {
		timeout = 120 * time.Second // Increased from 30s to 120s for LLM calls
	}

	return &HTTPBackendClient{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		timeout: timeout,
	}
}

// InferIntent implements the BackendClient interface.
func (c *HTTPBackendClient) InferIntent(request *IntentRequest) (*IntentResponse, error) {
	url := fmt.Sprintf("%s/api/intent/infer", c.baseURL)

	// Marshal request to JSON
	jsonData, err := json.Marshal(request)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	// Create HTTP request
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	// Send request
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// Check status code
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("backend returned status %d: %s", resp.StatusCode, string(body))
	}

	// Parse response
	var response IntentResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &response, nil
}

// ExecuteIntent implements the BackendClient interface.
func (c *HTTPBackendClient) ExecuteIntent(request *IntentRequest) (<-chan ExecutionEvent, error) {
	events := make(chan ExecutionEvent, 10)

	go func() {
		defer close(events)

		// Send a status event
		events <- ExecutionEvent{
			Type:      "status",
			Message:   "Executing command...",
			Timestamp: time.Now().Format(time.RFC3339),
		}

		// Call backend execute endpoint
		url := fmt.Sprintf("%s/api/intent/execute", c.baseURL)
		jsonData, err := json.Marshal(request)
		if err != nil {
			events <- ExecutionEvent{
				Type:      "error",
				Message:   fmt.Sprintf("Failed to marshal request: %v", err),
				Timestamp: time.Now().Format(time.RFC3339),
			}
			return
		}

		req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
		if err != nil {
			events <- ExecutionEvent{
				Type:      "error",
				Message:   fmt.Sprintf("Failed to create request: %v", err),
				Timestamp: time.Now().Format(time.RFC3339),
			}
			return
		}

		req.Header.Set("Content-Type", "application/json")

		resp, err := c.httpClient.Do(req)
		if err != nil {
			events <- ExecutionEvent{
				Type:      "error",
				Message:   fmt.Sprintf("Failed to send request: %v", err),
				Timestamp: time.Now().Format(time.RFC3339),
			}
			return
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			body, _ := io.ReadAll(resp.Body)
			events <- ExecutionEvent{
				Type:      "error",
				Message:   fmt.Sprintf("Backend returned status %d: %s", resp.StatusCode, string(body)),
				Timestamp: time.Now().Format(time.RFC3339),
			}
			return
		}

		// Parse response
		var result map[string]interface{}
		if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
			events <- ExecutionEvent{
				Type:      "error",
				Message:   fmt.Sprintf("Failed to decode response: %v", err),
				Timestamp: time.Now().Format(time.RFC3339),
			}
			return
		}

		// Send result as completion event
		resultJSON, _ := json.MarshalIndent(result, "", "  ")
		events <- ExecutionEvent{
			Type:      "completion",
			Message:   string(resultJSON),
			Timestamp: time.Now().Format(time.RFC3339),
			Metadata:  result,
		}
	}()

	return events, nil
}

// QueryState implements the BackendClient interface.
func (c *HTTPBackendClient) QueryState(projectID string) (*SDLCState, error) {
	url := fmt.Sprintf("%s/api/state/%s", c.baseURL, projectID)

	// Create HTTP request
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Send request
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// Check status code
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("backend returned status %d: %s", resp.StatusCode, string(body))
	}

	// Parse response
	var state SDLCState
	if err := json.NewDecoder(resp.Body).Decode(&state); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &state, nil
}

// IsAvailable implements the BackendClient interface.
func (c *HTTPBackendClient) IsAvailable() bool {
	url := fmt.Sprintf("%s/actuator/health", c.baseURL)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return false
	}

	// Use a shorter timeout for health check
	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()

	return resp.StatusCode == http.StatusOK
}

// Close implements the BackendClient interface.
func (c *HTTPBackendClient) Close() error {
	// HTTP client doesn't need explicit closing
	return nil
}
