package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// TestProperty30_JSONCommunicationProtocol tests that all messages between CLI and Backend are valid JSON
// Property 30: JSON Communication Protocol
// Validates: Requirements 8.4
// Feature: sdlcraft-cli, Property 30: JSON Communication Protocol
//
// For any message sent between CLI and Backend, the message should be valid JSON 
// and conform to the defined request/response schemas.
func TestProperty30_JSONCommunicationProtocol(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Generators for request fields
	genRawCommand := gen.AlphaString().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 100 })
	genUserID := gen.RegexMatch(`user[0-9]{1,5}`)
	genProjectID := gen.RegexMatch(`project[0-9]{1,5}`)
	genProjectPath := gen.OneConstOf("/home/user/project", "/workspace/app", "/code/service")
	
	// Generator for context map
	genContext := gen.MapOf(
		gen.OneConstOf("key1", "key2", "key3"),
		gen.OneConstOf("value1", "value2", "value3"),
	).Map(func(m map[string]string) map[string]interface{} {
		result := make(map[string]interface{})
		for k, v := range m {
			result[k] = v
		}
		return result
	})

	properties.Property("IntentRequest serializes to valid JSON and deserializes correctly", prop.ForAll(
		func(rawCommand, userID, projectID, projectPath string, context map[string]interface{}) bool {
			// Create IntentRequest
			request := &IntentRequest{
				RawCommand:  rawCommand,
				UserID:      userID,
				ProjectID:   projectID,
				ProjectPath: projectPath,
				Context:     context,
			}

			// Serialize to JSON
			jsonData, err := json.Marshal(request)
			if err != nil {
				t.Logf("Failed to marshal IntentRequest: %v", err)
				return false
			}

			// Verify it's valid JSON by checking it's not empty
			if len(jsonData) == 0 {
				t.Logf("Marshaled JSON is empty")
				return false
			}

			// Deserialize back
			var decoded IntentRequest
			if err := json.Unmarshal(jsonData, &decoded); err != nil {
				t.Logf("Failed to unmarshal IntentRequest: %v", err)
				return false
			}

			// Verify round-trip preserves data
			if decoded.RawCommand != request.RawCommand {
				t.Logf("RawCommand mismatch: expected %s, got %s", request.RawCommand, decoded.RawCommand)
				return false
			}

			if decoded.UserID != request.UserID {
				t.Logf("UserID mismatch: expected %s, got %s", request.UserID, decoded.UserID)
				return false
			}

			if decoded.ProjectID != request.ProjectID {
				t.Logf("ProjectID mismatch: expected %s, got %s", request.ProjectID, decoded.ProjectID)
				return false
			}

			if decoded.ProjectPath != request.ProjectPath {
				t.Logf("ProjectPath mismatch: expected %s, got %s", request.ProjectPath, decoded.ProjectPath)
				return false
			}

			// Verify context map size matches
			if len(decoded.Context) != len(request.Context) {
				t.Logf("Context size mismatch: expected %d, got %d", len(request.Context), len(decoded.Context))
				return false
			}

			return true
		},
		genRawCommand,
		genUserID,
		genProjectID,
		genProjectPath,
		genContext,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty30_IntentResponseJSONProtocol tests IntentResponse JSON serialization
func TestProperty30_IntentResponseJSONProtocol(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Generators for response fields
	genIntent := gen.OneConstOf("status", "analyze", "improve", "test", "debug", "prepare", "release")
	genTarget := gen.OneConstOf("security", "performance", "coverage", "quality", "dependencies")
	genConfidence := gen.Float64Range(0.0, 1.0)
	genExplanation := gen.RegexMatch(`[a-zA-Z]{5,50}`)
	genRiskLevel := gen.OneConstOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
	genBool := gen.Bool()
	
	// Generator for modifiers map
	genModifiers := gen.MapOf(
		gen.OneConstOf("verbose", "force", "dry-run"),
		gen.OneConstOf("true", "false", "yes", "no"),
	)

	// Generator for clarification questions - use a simpler approach
	genQuestions := gen.SliceOf(gen.Const("What is the target?"))

	properties.Property("IntentResponse serializes to valid JSON and deserializes correctly", prop.ForAll(
		func(intent, target string, modifiers map[string]string, confidence float64, 
			explanation string, questions []string, requiresConfirmation bool, 
			riskLevel, impactDesc string) bool {
			
			// Create IntentResponse
			response := &IntentResponse{
				Intent:                 intent,
				Target:                 target,
				Modifiers:              modifiers,
				Confidence:             confidence,
				Explanation:            explanation,
				ClarificationQuestions: questions,
				RequiresConfirmation:   requiresConfirmation,
				RiskLevel:              riskLevel,
				ImpactDescription:      impactDesc,
			}

			// Serialize to JSON
			jsonData, err := json.Marshal(response)
			if err != nil {
				t.Logf("Failed to marshal IntentResponse: %v", err)
				return false
			}

			// Verify it's valid JSON
			if len(jsonData) == 0 {
				t.Logf("Marshaled JSON is empty")
				return false
			}

			// Deserialize back
			var decoded IntentResponse
			if err := json.Unmarshal(jsonData, &decoded); err != nil {
				t.Logf("Failed to unmarshal IntentResponse: %v", err)
				return false
			}

			// Verify round-trip preserves data
			if decoded.Intent != response.Intent {
				t.Logf("Intent mismatch: expected %s, got %s", response.Intent, decoded.Intent)
				return false
			}

			if decoded.Target != response.Target {
				t.Logf("Target mismatch: expected %s, got %s", response.Target, decoded.Target)
				return false
			}

			if decoded.Confidence != response.Confidence {
				t.Logf("Confidence mismatch: expected %f, got %f", response.Confidence, decoded.Confidence)
				return false
			}

			if decoded.RequiresConfirmation != response.RequiresConfirmation {
				t.Logf("RequiresConfirmation mismatch: expected %v, got %v", 
					response.RequiresConfirmation, decoded.RequiresConfirmation)
				return false
			}

			if decoded.RiskLevel != response.RiskLevel {
				t.Logf("RiskLevel mismatch: expected %s, got %s", response.RiskLevel, decoded.RiskLevel)
				return false
			}

			// Verify modifiers map
			if len(decoded.Modifiers) != len(response.Modifiers) {
				t.Logf("Modifiers size mismatch: expected %d, got %d", 
					len(response.Modifiers), len(decoded.Modifiers))
				return false
			}

			// Verify clarification questions slice
			if len(decoded.ClarificationQuestions) != len(response.ClarificationQuestions) {
				t.Logf("ClarificationQuestions size mismatch: expected %d, got %d", 
					len(response.ClarificationQuestions), len(decoded.ClarificationQuestions))
				return false
			}

			return true
		},
		genIntent,
		genTarget,
		genModifiers,
		genConfidence,
		genExplanation,
		genQuestions,
		genBool,
		genRiskLevel,
		genExplanation, // reuse for impact description
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty30_SDLCStateJSONProtocol tests SDLCState JSON serialization
func TestProperty30_SDLCStateJSONProtocol(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Generators for state fields
	genProjectID := gen.RegexMatch(`project[0-9]{1,5}`)
	genPhase := gen.OneConstOf("PLANNING", "DEVELOPMENT", "TESTING", "STAGING", "PRODUCTION")
	genRiskLevel := gen.OneConstOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
	genCoverage := gen.Float64Range(0.0, 1.0)
	genIssues := gen.IntRange(0, 100)
	genReadiness := gen.Float64Range(0.0, 1.0)
	genTimestamp := gen.OneConstOf("2024-01-01T00:00:00Z", "2024-06-15T12:30:00Z", "2024-12-31T23:59:59Z")
	
	// Generator for custom metrics
	genMetrics := gen.MapOf(
		gen.OneConstOf("metric1", "metric2", "metric3"),
		gen.Float64Range(0.0, 100.0),
	).Map(func(m map[string]float64) map[string]interface{} {
		result := make(map[string]interface{})
		for k, v := range m {
			result[k] = v
		}
		return result
	})

	properties.Property("SDLCState serializes to valid JSON and deserializes correctly", prop.ForAll(
		func(projectID, phase, riskLevel string, coverage float64, openIssues, totalIssues int,
			readiness float64, metrics map[string]interface{}, timestamp string) bool {
			
			// Create SDLCState
			state := &SDLCState{
				ProjectID:        projectID,
				CurrentPhase:     phase,
				RiskLevel:        riskLevel,
				TestCoverage:     coverage,
				OpenIssues:       openIssues,
				TotalIssues:      totalIssues,
				ReleaseReadiness: readiness,
				CustomMetrics:    metrics,
				UpdatedAt:        timestamp,
			}

			// Serialize to JSON
			jsonData, err := json.Marshal(state)
			if err != nil {
				t.Logf("Failed to marshal SDLCState: %v", err)
				return false
			}

			// Verify it's valid JSON
			if len(jsonData) == 0 {
				t.Logf("Marshaled JSON is empty")
				return false
			}

			// Deserialize back
			var decoded SDLCState
			if err := json.Unmarshal(jsonData, &decoded); err != nil {
				t.Logf("Failed to unmarshal SDLCState: %v", err)
				return false
			}

			// Verify round-trip preserves data
			if decoded.ProjectID != state.ProjectID {
				t.Logf("ProjectID mismatch: expected %s, got %s", state.ProjectID, decoded.ProjectID)
				return false
			}

			if decoded.CurrentPhase != state.CurrentPhase {
				t.Logf("CurrentPhase mismatch: expected %s, got %s", state.CurrentPhase, decoded.CurrentPhase)
				return false
			}

			if decoded.RiskLevel != state.RiskLevel {
				t.Logf("RiskLevel mismatch: expected %s, got %s", state.RiskLevel, decoded.RiskLevel)
				return false
			}

			if decoded.TestCoverage != state.TestCoverage {
				t.Logf("TestCoverage mismatch: expected %f, got %f", state.TestCoverage, decoded.TestCoverage)
				return false
			}

			if decoded.OpenIssues != state.OpenIssues {
				t.Logf("OpenIssues mismatch: expected %d, got %d", state.OpenIssues, decoded.OpenIssues)
				return false
			}

			if decoded.ReleaseReadiness != state.ReleaseReadiness {
				t.Logf("ReleaseReadiness mismatch: expected %f, got %f", 
					state.ReleaseReadiness, decoded.ReleaseReadiness)
				return false
			}

			// Verify custom metrics map
			if len(decoded.CustomMetrics) != len(state.CustomMetrics) {
				t.Logf("CustomMetrics size mismatch: expected %d, got %d", 
					len(state.CustomMetrics), len(decoded.CustomMetrics))
				return false
			}

			return true
		},
		genProjectID,
		genPhase,
		genRiskLevel,
		genCoverage,
		genIssues,
		genIssues,
		genReadiness,
		genMetrics,
		genTimestamp,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty30_ExecutionEventJSONProtocol tests ExecutionEvent JSON serialization
func TestProperty30_ExecutionEventJSONProtocol(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Generators for event fields
	genEventType := gen.OneConstOf("status", "progress", "completion", "error", "warning")
	genMessage := gen.AlphaString().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 200 })
	genTimestamp := gen.OneConstOf("2024-01-01T00:00:00Z", "2024-06-15T12:30:00Z", "2024-12-31T23:59:59Z")
	genProgress := gen.IntRange(0, 100)
	
	// Generator for metadata map
	genMetadata := gen.MapOf(
		gen.OneConstOf("agent", "step", "duration"),
		gen.OneConstOf("planner", "executor", "validator"),
	).Map(func(m map[string]string) map[string]interface{} {
		result := make(map[string]interface{})
		for k, v := range m {
			result[k] = v
		}
		return result
	})

	properties.Property("ExecutionEvent serializes to valid JSON and deserializes correctly", prop.ForAll(
		func(eventType, message, timestamp string, progress int, metadata map[string]interface{}) bool {
			// Create ExecutionEvent
			event := &ExecutionEvent{
				Type:      eventType,
				Message:   message,
				Timestamp: timestamp,
				Progress:  progress,
				Metadata:  metadata,
			}

			// Serialize to JSON
			jsonData, err := json.Marshal(event)
			if err != nil {
				t.Logf("Failed to marshal ExecutionEvent: %v", err)
				return false
			}

			// Verify it's valid JSON
			if len(jsonData) == 0 {
				t.Logf("Marshaled JSON is empty")
				return false
			}

			// Deserialize back
			var decoded ExecutionEvent
			if err := json.Unmarshal(jsonData, &decoded); err != nil {
				t.Logf("Failed to unmarshal ExecutionEvent: %v", err)
				return false
			}

			// Verify round-trip preserves data
			if decoded.Type != event.Type {
				t.Logf("Type mismatch: expected %s, got %s", event.Type, decoded.Type)
				return false
			}

			if decoded.Message != event.Message {
				t.Logf("Message mismatch: expected %s, got %s", event.Message, decoded.Message)
				return false
			}

			if decoded.Timestamp != event.Timestamp {
				t.Logf("Timestamp mismatch: expected %s, got %s", event.Timestamp, decoded.Timestamp)
				return false
			}

			if decoded.Progress != event.Progress {
				t.Logf("Progress mismatch: expected %d, got %d", event.Progress, decoded.Progress)
				return false
			}

			// Verify metadata map
			if len(decoded.Metadata) != len(event.Metadata) {
				t.Logf("Metadata size mismatch: expected %d, got %d", 
					len(event.Metadata), len(decoded.Metadata))
				return false
			}

			return true
		},
		genEventType,
		genMessage,
		genTimestamp,
		genProgress,
		genMetadata,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProperty30_HTTPCommunicationProtocol tests end-to-end HTTP communication with JSON
func TestProperty30_HTTPCommunicationProtocol(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// Generators for request/response
	genRawCommand := gen.AlphaString().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 100 })
	genUserID := gen.RegexMatch(`user[0-9]{1,5}`)
	genProjectID := gen.RegexMatch(`project[0-9]{1,5}`)
	genIntent := gen.OneConstOf("status", "analyze", "improve", "test", "debug")
	genTarget := gen.OneConstOf("security", "performance", "coverage")
	genConfidence := gen.Float64Range(0.5, 1.0)

	properties.Property("HTTP communication uses valid JSON for requests and responses", prop.ForAll(
		func(rawCommand, userID, projectID, intent, target string, confidence float64) bool {
			// Create mock server that validates JSON
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				// Verify Content-Type header
				contentType := r.Header.Get("Content-Type")
				if contentType != "application/json" {
					t.Logf("Invalid Content-Type: %s", contentType)
					w.WriteHeader(http.StatusBadRequest)
					return
				}

				// Decode request as JSON
				var req IntentRequest
				if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
					t.Logf("Failed to decode request JSON: %v", err)
					w.WriteHeader(http.StatusBadRequest)
					return
				}

				// Verify request fields are present
				if req.RawCommand == "" || req.UserID == "" || req.ProjectID == "" {
					t.Logf("Request missing required fields")
					w.WriteHeader(http.StatusBadRequest)
					return
				}

				// Send valid JSON response
				response := IntentResponse{
					Intent:      intent,
					Target:      target,
					Confidence:  confidence,
					Explanation: "Test response",
				}

				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusOK)
				if err := json.NewEncoder(w).Encode(response); err != nil {
					t.Logf("Failed to encode response JSON: %v", err)
				}
			}))
			defer server.Close()

			// Create client
			client := NewHTTPBackendClient(server.URL, 5*time.Second)

			// Create request
			request := &IntentRequest{
				RawCommand: rawCommand,
				UserID:     userID,
				ProjectID:  projectID,
			}

			// Send request
			response, err := client.InferIntent(request)
			if err != nil {
				t.Logf("InferIntent failed: %v", err)
				return false
			}

			// Verify response is valid
			if response == nil {
				t.Logf("Response is nil")
				return false
			}

			if response.Intent != intent {
				t.Logf("Intent mismatch: expected %s, got %s", intent, response.Intent)
				return false
			}

			if response.Target != target {
				t.Logf("Target mismatch: expected %s, got %s", target, response.Target)
				return false
			}

			if response.Confidence != confidence {
				t.Logf("Confidence mismatch: expected %f, got %f", confidence, response.Confidence)
				return false
			}

			return true
		},
		genRawCommand,
		genUserID,
		genProjectID,
		genIntent,
		genTarget,
		genConfidence,
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}
