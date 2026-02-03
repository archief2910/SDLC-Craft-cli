package com.sdlcraft.backend.intent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for IntentDefinition entities.
 * 
 * Provides database access for intent definitions, allowing intents to be
 * loaded, saved, and queried.
 */
@Repository
public interface IntentDefinitionRepository extends JpaRepository<IntentDefinition, String> {
    
    /**
     * Finds an intent definition by name.
     * 
     * @param name the intent name
     * @return optional containing the intent definition if found
     */
    Optional<IntentDefinition> findByName(String name);
    
    /**
     * Finds all enabled intent definitions.
     * 
     * @return list of enabled intent definitions
     */
    List<IntentDefinition> findByEnabledTrue();
    
    /**
     * Checks if an intent with the given name exists.
     * 
     * @param name the intent name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);
}
