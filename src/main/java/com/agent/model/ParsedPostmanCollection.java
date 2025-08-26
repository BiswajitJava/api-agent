package com.agent.model;

import java.util.Optional;

/**
 * A wrapper record that holds the complete result of parsing a Postman collection.
 * <p>
 * It bundles the main {@link ApiSpecification} with an {@link Optional} containing
 * any {@link AuthDetails} that were discovered in the collection's 'auth' block.
 * This provides a clean and type-safe way for the parser to return multiple,
 * related pieces of data.
 *
 * @param spec The parsed ApiSpecification containing the server URLs and operations.
 * @param auth An Optional containing AuthDetails if authentication was found in the collection,
 *             otherwise an empty Optional.
 */
public record ParsedPostmanCollection(ApiSpecification spec, Optional<AuthDetails> auth) {
}