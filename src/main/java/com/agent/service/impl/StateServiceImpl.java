package com.agent.service.impl;

import com.agent.model.ApiSpecification;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

/**
 * A file-based implementation of the {@link StateService} that persists the application's state
 * to a JSON file in the user's home directory.
 * <p>
 * This service manages an in-memory cache for API specifications and credentials, which is
 * synchronized with a persistent file on disk. To enhance security, all credentials are
 * encrypted using a {@link StringEncryptor} before being stored in memory or written to the
 * file. All file I/O operations are synchronized to ensure thread safety in a concurrent
 * environment.
 */
@Service
@Slf4j
public class StateServiceImpl implements StateService {

    private static final String DATA_DIRECTORY = System.getenv("API_AGENT_HOME") != null ? System.getenv("API_AGENT_HOME") : System.getProperty("user.home");
    private static final String STATE_FILE_PATH = DATA_DIRECTORY + "/.api-agent/state.json";
    private final File stateFile = new File(STATE_FILE_PATH);

    private Map<String, ApiSpecification> specifications = new ConcurrentHashMap<>();
    private Map<String, String> credentials = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringEncryptor encryptor;

    /**
     * Constructs the StateService with a Jasypt {@link StringEncryptor}.
     * The encryptor bean is automatically configured and provided by the Jasypt Spring Boot starter,
     * and is used for securing and unsecure credentials.
     *
     * @param encryptor The {@link StringEncryptor} bean used for credential encryption and decryption.
     */
    public StateServiceImpl(StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    /**
     * Initializes the service by loading the persistent state from the JSON file into memory.
     * This method is automatically invoked by Spring after the bean has been constructed,
     * ensuring the application starts with its previously saved state.
     */
    @PostConstruct
    public void init() {
        loadState();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation stores the API specification in the in-memory map and immediately
     * persists the updated state to the JSON file to ensure data durability.
     */
    @Override
    public void saveSpecification(String alias, ApiSpecification spec) {
        specifications.put(alias, spec);
        saveState();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation retrieves the API specification directly from the in-memory map,
     * providing fast access.
     */
    @Override
    public ApiSpecification getSpecification(String alias) {
        return specifications.get(alias);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first encrypts the provided token before storing it in the
     * in-memory map. It then persists the updated, encrypted state to the JSON file.
     */
    @Override
    public void saveCredential(String alias, String token) {
        log.info("Encrypting and saving credential for alias '{}'", alias);
        credentials.put(alias, encryptor.encrypt(token));
        saveState();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation retrieves the encrypted token from the in-memory map and
     * decrypts it on-the-fly. If decryption fails, which could occur due to a misconfigured
     * or changed secret key, an error is logged and this method returns {@code null}.
     */
    @Override
    public String getCredential(String alias) {
        String encryptedToken = credentials.get(alias);
        if (encryptedToken == null) {
            return null;
        }
        try {
            return encryptor.decrypt(encryptedToken);
        } catch (Exception e) {
            log.error("Could not decrypt credential for alias '{}'. The secret key may have changed or is incorrect.", alias);
            return null;
        }
    }

    /**
     * Saves the current in-memory state (specifications and encrypted credentials) to the
     * JSON file on disk. This method is synchronized to prevent concurrent write operations.
     * It ensures the parent directory exists before attempting to write the file.
     *
     * @throws RuntimeException if an I/O error occurs during directory creation or file writing.
     */
    private synchronized void saveState() {
        try {
            File parentDir = stateFile.getParentFile();
            // Check if the parent directory exists, and if not, attempt to create it.
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                // If directory creation fails, throw an exception with a clear message.
                throw new IOException("Failed to create parent directories at: " + parentDir.getAbsolutePath());
            }

            Map<String, Object> state = new HashMap<>();
            state.put("specifications", specifications);
            state.put("credentials", credentials); // Credentials remain encrypted
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
        } catch (IOException e) {
            log.error("CRITICAL: Failed to save application state to {}", STATE_FILE_PATH, e);
            throw new RuntimeException("Failed to save application state", e);
        }
    }

    /**
     * Loads the application state from the JSON file into the in-memory maps. This method is
     * synchronized to prevent conflicts during application startup. If the file does not exist,
     * the application starts with a fresh state. If the file is corrupted or cannot be parsed,
     * it is backed up, and the application starts fresh to prevent a crash loop.
     */
    private synchronized void loadState() {
        if (stateFile.exists() && stateFile.length() > 0) {
            try {
                TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};
                Map<String, Object> state = objectMapper.readValue(stateFile, typeRef);

                if (state.get("specifications") != null) {
                    TypeReference<ConcurrentHashMap<String, ApiSpecification>> specTypeRef = new TypeReference<>() {};
                    this.specifications = objectMapper.convertValue(state.get("specifications"), specTypeRef);
                }

                if (state.get("credentials") != null) {
                    TypeReference<ConcurrentHashMap<String, String>> credTypeRef = new TypeReference<>() {};
                    this.credentials = objectMapper.convertValue(state.get("credentials"), credTypeRef);
                }
                log.info("Successfully loaded state from {}", STATE_FILE_PATH);
            } catch (Exception e) {
                log.warn("Could not load or parse state file at {}. A backup will be created, and the application will start with a fresh state. Error: {}", STATE_FILE_PATH, e.getMessage());
                backupCorruptedStateFile();
                // Reset state to ensure clean startup after a corrupted file is found
                this.specifications = new ConcurrentHashMap<>();
                this.credentials = new ConcurrentHashMap<>();
            }
        } else {
            log.info("No state file found at {}, starting with a clean state.", STATE_FILE_PATH);
        }
    }

    /**
     * Backs up a state file that cannot be parsed by renaming it with a ".corrupted"
     * suffix and a timestamp. This prevents the application from failing on subsequent
     * startups and preserves the corrupted data for manual inspection.
     */
    private void backupCorruptedStateFile() {
        File backupFile = new File(STATE_FILE_PATH + ".corrupted." + System.currentTimeMillis());
        try {
            Files.move(stateFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Backed up corrupted state file to {}", backupFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("CRITICAL: Failed to back up corrupted state file from {} to {}", stateFile.getAbsolutePath(), backupFile.getAbsolutePath(), e);
        }
    }
}