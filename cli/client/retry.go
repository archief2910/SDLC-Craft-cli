package client

import (
	"fmt"
	"math"
	"time"
)

// RetryConfig configures retry behavior for backend requests.
type RetryConfig struct {
	MaxAttempts     int
	InitialDelay    time.Duration
	MaxDelay        time.Duration
	BackoffMultiplier float64
}

// DefaultRetryConfig returns the default retry configuration.
// - 3 attempts total
// - Initial delay of 1 second
// - Max delay of 10 seconds
// - Exponential backoff with multiplier of 2
func DefaultRetryConfig() RetryConfig {
	return RetryConfig{
		MaxAttempts:     3,
		InitialDelay:    1 * time.Second,
		MaxDelay:        10 * time.Second,
		BackoffMultiplier: 2.0,
	}
}

// RetryableBackendClient wraps a BackendClient with retry logic.
type RetryableBackendClient struct {
	client BackendClient
	config RetryConfig
}

// NewRetryableBackendClient creates a new backend client with retry logic.
func NewRetryableBackendClient(client BackendClient, config RetryConfig) *RetryableBackendClient {
	return &RetryableBackendClient{
		client: client,
		config: config,
	}
}

// InferIntent implements BackendClient with retry logic.
func (r *RetryableBackendClient) InferIntent(request *IntentRequest) (*IntentResponse, error) {
	var lastErr error

	for attempt := 1; attempt <= r.config.MaxAttempts; attempt++ {
		response, err := r.client.InferIntent(request)
		if err == nil {
			return response, nil
		}

		lastErr = err

		// Don't retry on last attempt
		if attempt == r.config.MaxAttempts {
			break
		}

		// Calculate backoff delay
		delay := r.calculateDelay(attempt)

		// Wait before retrying
		time.Sleep(delay)
	}

	return nil, fmt.Errorf("failed after %d attempts: %w", r.config.MaxAttempts, lastErr)
}

// ExecuteIntent implements BackendClient with retry logic.
func (r *RetryableBackendClient) ExecuteIntent(request *IntentRequest) (<-chan ExecutionEvent, error) {
	// Execution is not retried as it may have side effects
	return r.client.ExecuteIntent(request)
}

// QueryState implements BackendClient with retry logic.
func (r *RetryableBackendClient) QueryState(projectID string) (*SDLCState, error) {
	var lastErr error

	for attempt := 1; attempt <= r.config.MaxAttempts; attempt++ {
		state, err := r.client.QueryState(projectID)
		if err == nil {
			return state, nil
		}

		lastErr = err

		// Don't retry on last attempt
		if attempt == r.config.MaxAttempts {
			break
		}

		// Calculate backoff delay
		delay := r.calculateDelay(attempt)

		// Wait before retrying
		time.Sleep(delay)
	}

	return nil, fmt.Errorf("failed after %d attempts: %w", r.config.MaxAttempts, lastErr)
}

// IsAvailable implements BackendClient.
func (r *RetryableBackendClient) IsAvailable() bool {
	return r.client.IsAvailable()
}

// Close implements BackendClient.
func (r *RetryableBackendClient) Close() error {
	return r.client.Close()
}

// calculateDelay calculates the exponential backoff delay for the given attempt.
func (r *RetryableBackendClient) calculateDelay(attempt int) time.Duration {
	// Exponential backoff: initialDelay * (multiplier ^ (attempt - 1))
	delay := float64(r.config.InitialDelay) * math.Pow(r.config.BackoffMultiplier, float64(attempt-1))

	// Cap at max delay
	if delay > float64(r.config.MaxDelay) {
		delay = float64(r.config.MaxDelay)
	}

	return time.Duration(delay)
}
