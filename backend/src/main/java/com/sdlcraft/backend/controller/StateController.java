package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.sdlc.SDLCState;
import com.sdlcraft.backend.sdlc.SDLCStateMachine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/state")
public class StateController {
    
    private final SDLCStateMachine stateMachine;
    
    public StateController(SDLCStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }
    
    @GetMapping("/{projectId}")
    public ResponseEntity<SDLCState> getState(@PathVariable String projectId) {
        try {
            SDLCState state = stateMachine.getCurrentState(projectId);
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            // Initialize project if it doesn't exist
            SDLCState state = stateMachine.initializeProject(projectId);
            return ResponseEntity.ok(state);
        }
    }
}
