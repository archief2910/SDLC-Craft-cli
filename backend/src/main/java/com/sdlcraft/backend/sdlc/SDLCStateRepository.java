package com.sdlcraft.backend.sdlc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for SDLCStateEntity.
 * 
 * Provides database access for SDLC state persistence and querying.
 */
@Repository
public interface SDLCStateRepository extends JpaRepository<SDLCStateEntity, String> {
    
    /**
     * Finds all projects in a specific phase.
     * 
     * @param phase the phase to filter by
     * @return list of projects in that phase
     */
    List<SDLCStateEntity> findByCurrentPhase(Phase phase);
    
    /**
     * Finds all projects with a specific risk level.
     * 
     * @param riskLevel the risk level to filter by
     * @return list of projects with that risk level
     */
    List<SDLCStateEntity> findByRiskLevel(RiskLevel riskLevel);
    
    /**
     * Finds all projects with risk level higher than or equal to the specified level.
     * 
     * @param riskLevel the minimum risk level
     * @return list of projects with risk level >= specified level
     */
    List<SDLCStateEntity> findByRiskLevelGreaterThanEqual(RiskLevel riskLevel);
}
