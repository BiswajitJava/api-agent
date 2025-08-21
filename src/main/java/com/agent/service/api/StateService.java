package com.agent.service.api;

import com.agent.model.ApiSpecification;

/**
 * An interface defining the contract for managing the persistent state of the application.
 * This service is responsible for storing and retrieving learned API specifications and
 * their associated credentials, abstracting the underlying storage mechanism (e.g., file system, database).
 */
public interface StateService {

    /**
     * Saves or updates a simplified API specification, associating it with a unique alias.
     *
     * @param alias The unique alias to be used as a key for storing and retrieving the specification.
     * @param spec  The {@link ApiSpecification} object to be persisted.
     */
    void saveSpecification(String alias, ApiSpecification spec);

    /**
     * Retrieves a previously saved API specification by its alias.
     *
     * @param alias The unique alias of the specification to retrieve.
     * @return The corresponding {@link ApiSpecification} object, or {@code null} if no
     *         specification is found for the given alias.
     */
    ApiSpecification getSpecification(String alias);

    /**
     * Saves or updates a credential (e.g., an API key or token) for a specific API,
     * associating it with the API's alias.
     *
     * @param alias The alias of the API to which the credential belongs.
     * @param token The credential string to be saved.
     */
    void saveCredential(String alias, String token);

    /**
     * Retrieves the credential for a given API alias.
     *
     * @param alias The alias of the API whose credential is to be retrieved.
     * @return The credential string, or {@code null} if no credential has been
     *         saved for the given alias.
     */
    String getCredential(String alias);
}