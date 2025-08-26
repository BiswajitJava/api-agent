package com.agent.config;

import com.agent.model.ParsedPostmanCollection;
import com.agent.service.api.PostmanParser;
import com.agent.service.api.StateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Objects;

/**
 * A powerful component that automatically discovers, learns, and configures APIs
 * from a directory of Postman collections on application startup.
 * <p>
 * It scans the directory specified by the `POSTMAN_COLLECTIONS_DIR` environment
 * variable. For each collection, it learns the API structure and then attempts
 * to configure its authentication by matching variables from the collection (e.g., `{{apiKey}}`)
 * with corresponding values in the environment (e.g., the `.env` file).
 * This provides a "zero-setup" experience for users.
 */
@Component
@Profile("!test") // Ensures this does not run during tests
public class AutoLearnRunner implements CommandLineRunner {

    // Injects the directory path from the .env file, with a sensible default.
    @Value("${POSTMAN_COLLECTIONS_DIR:/app/collections}")
    private String collectionsDirectoryPath;

    private final PostmanParser postmanParser;
    private final StateService stateService;

    public AutoLearnRunner(PostmanParser postmanParser, StateService stateService) {
        this.postmanParser = postmanParser;
        this.stateService = stateService;
    }

    @Override
    public void run(String... args) {
        System.out.println("\n--- Starting API Agent Auto-Setup ---");
        File collectionsDir = new File(collectionsDirectoryPath);

        if (!collectionsDir.exists() || !collectionsDir.isDirectory()) {
            System.out.println("Auto-learn directory not found at '" + collectionsDirectoryPath + "'. Skipping.");
            System.out.println("--- Auto-Setup Complete ---\n");
            return;
        }

        File[] collectionFiles = collectionsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        if (collectionFiles == null || collectionFiles.length == 0) {
            System.out.println("No Postman collection (.json) files found in '" + collectionsDirectoryPath + "'. Skipping.");
            System.out.println("--- Auto-Setup Complete ---\n");
            return;
        }

        for (File file : collectionFiles) {
            // Derive a clean alias from the filename, e.g., "My CRM API.postman_collection.json" -> "my-crm-api"
            String alias = file.getName()
                    .toLowerCase()
                    .replaceFirst("\\.postman_collection\\.json$", "")
                    .replaceAll("[^a-zA-Z0-9-]", "-");

            System.out.println("\nProcessing collection: " + file.getName() + " with alias: " + alias);

            // 1. AUTO-LEARN
            if (stateService.getSpecification(alias) == null) {
                try {
                    ParsedPostmanCollection parsed = postmanParser.parse(file);
                    stateService.saveSpecification(alias, parsed.spec());
                    System.out.println("  [LEARN] Successfully learned API '" + alias + "'.");

                    // 2. AUTO-AUTHENTICATE
                    parsed.auth().ifPresentOrElse(
                            authDetails -> {
                                System.out.println("  [AUTH] Found authentication details (type: " + authDetails.type() + ").");
                                String tokenValue = authDetails.token();

                                if (tokenValue.startsWith("{{") && tokenValue.endsWith("}}")) {
                                    String variableName = tokenValue.substring(2, tokenValue.length() - 2);
                                    String resolvedToken = System.getenv(variableName);

                                    if (resolvedToken != null && !resolvedToken.isBlank()) {
                                        stateService.saveCredential(alias, resolvedToken);
                                        System.out.println("  [AUTH] SUCCESS: Configured authentication using environment variable '" + variableName + "'.");
                                    } else {
                                        System.err.println("  [AUTH] FAILED: Collection needs variable '" + variableName + "', but it was not found in your .env file.");
                                    }
                                } else {
                                    stateService.saveCredential(alias, tokenValue);
                                    System.out.println("  [AUTH] SUCCESS: Configured authentication with a raw token from the collection.");
                                }
                            },
                            () -> System.out.println("  [AUTH] No authentication block found in collection.")
                    );
                } catch (Exception e) {
                    System.err.println("  [LEARN] FAILED: Could not process collection '" + file.getName() + "'. Error: " + e.getMessage());
                }
            } else {
                System.out.println("  [LEARN] SKIPPED: API '" + alias + "' is already learned.");
            }
        }
        System.out.println("\n--- Auto-Setup Complete. Ready for queries. ---\n");
    }
}