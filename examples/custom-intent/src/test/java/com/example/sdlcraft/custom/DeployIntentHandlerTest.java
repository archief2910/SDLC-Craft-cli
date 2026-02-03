package com.example.sdlcraft.custom;

import com.sdlcraft.backend.intent.IntentRequest;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.agent.AgentOrchestrator;
import com.sdlcraft.backend.agent.ExecutionResult;
import com.sdlcraft.backend.agent.ExecutionStatus;
import com.sdlcraft.backend.sdlc.SDLCStateMachine;
import com.sdlcraft.backend.sdlc.SDLCState;
import com.sdlcraft.backend.sdlc.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DeployIntentHandlerTest {
    
    @Mock
    private AgentOrchestrator orchestrator;
    
    @Mock
    private SDLCStateMachine stateMachine;
    
    private DeployIntentHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new DeployIntentHandler(orchestrator, stateMachine);
    }
    
    @Test
    void shouldHandleDeployToStaging() {
        // Given
        IntentRequest request = createRequest("project-123", "user-456");
        IntentResult intent = createIntent("deploy", "staging", Map.of());
        
        SDLCState currentState = new SDLCState();
        currentState.setCurrentPhase(Phase.TESTING);
        when(stateMachine.getCurrentState("project-123")).thenReturn(currentState);
        
        ExecutionResult expectedResult = ExecutionResult.builder()
            .executionId("exec-789")
            .status(ExecutionStatus.SUCCESS)
            .build();
        when(orchestrator.execute(any())).thenReturn(expectedResult);
        
        // When
        ExecutionResult result = handler.handle(request, intent);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        verify(orchestrator).execute(any());
        verify(stateMachine).transitionTo("project-123", Phase.STAGING);
    }
    
    @Test
    void shouldHandleDeployToProduction() {
        // Given
        IntentRequest request = createRequest("project-123", "user-456");
        IntentResult intent = createIntent("deploy", "production", 
            Map.of("version", "1.2.3"));
        
        SDLCState currentState = new SDLCState();
        currentState.setCurrentPhase(Phase.STAGING);
        when(stateMachine.getCurrentState("project-123")).thenReturn(currentState);
        
        ExecutionResult expectedResult = ExecutionResult.builder()
            .executionId("exec-789")
            .status(ExecutionStatus.SUCCESS)
            .build();
        when(orchestrator.execute(any())).thenReturn(expectedResult);
        
        // When
        ExecutionResult result = handler.handle(request, intent);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        verify(orchestrator).execute(any());
        verify(stateMachine).transitionTo("project-123", Phase.PRODUCTION);
    }
    
    @Test
    void shouldRejectInvalidEnvironment() {
        // Given
        IntentRequest request = createRequest("project-123", "user-456");
        IntentResult intent = createIntent("deploy", "invalid-env", Map.of());
        
        // When
        ExecutionResult result = handler.handle(request, intent);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getError()).contains("Invalid environment");
        verify(orchestrator, never()).execute(any());
    }
    
    @Test
    void shouldRejectDeploymentFromInvalidPhase() {
        // Given
        IntentRequest request = createRequest("project-123", "user-456");
        IntentResult intent = createIntent("deploy", "production", Map.of());
        
        SDLCState currentState = new SDLCState();
        currentState.setCurrentPhase(Phase.DEVELOPMENT);
        when(stateMachine.getCurrentState("project-123")).thenReturn(currentState);
        
        // When
        ExecutionResult result = handler.handle(request, intent);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getError()).contains("Cannot deploy to production");
        verify(orchestrator, never()).execute(any());
    }
    
    @Test
    void shouldHandleDryRun() {
        // Given
        IntentRequest request = createRequest("project-123", "user-456");
        IntentResult intent = createIntent("deploy", "staging", 
            Map.of("dry-run", "true"));
        
        SDLCState currentState = new SDLCState();
        currentState.setCurrentPhase(Phase.TESTING);
        when(stateMachine.getCurrentState("project-123")).thenReturn(currentState);
        
        ExecutionResult expectedResult = ExecutionResult.builder()
            .executionId("exec-789")
            .status(ExecutionStatus.SUCCESS)
            .build();
        when(orchestrator.execute(any())).thenReturn(expectedResult);
        
        // When
        ExecutionResult result = handler.handle(request, intent);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        verify(orchestrator).execute(any());
        // Should NOT update state for dry run
        verify(stateMachine, never()).transitionTo(any(), any());
    }
    
    @Test
    void shouldSupportDeployIntent() {
        assertThat(handler.supports("deploy")).isTrue();
        assertThat(handler.supports("other")).isFalse();
    }
    
    @Test
    void shouldReturnCorrectIntentName() {
        assertThat(handler.getIntentName()).isEqualTo("deploy");
    }
    
    private IntentRequest createRequest(String projectId, String userId) {
        IntentRequest request = new IntentRequest();
        request.setProjectId(projectId);
        request.setUserId(userId);
        request.setRawCommand("sdlc deploy staging");
        return request;
    }
    
    private IntentResult createIntent(String intent, String target, Map<String, String> modifiers) {
        IntentResult result = new IntentResult();
        result.setIntent(intent);
        result.setTarget(target);
        result.setModifiers(new HashMap<>(modifiers));
        result.setConfidence(0.95);
        return result;
    }
}
