package com.agent.service.impl;

import com.agent.model.ApiSpecification;
import com.agent.service.api.StateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

/**
 * A file-based implementation of the {@link StateService} that persists application state
 * to a JSON file in the user's home directory.
 * <p>
 * This service manages an in-memory cache for API specifications and credentials, which
 * is synchronized with the file on disk. To enhance security, all credentials are
 * encrypted using a {@link StringEncryptor} before being stored in memory or written to the file.
 * The methods for reading and writing to the file are synchronized to ensure thread safety.
 */
@Service
@Slf4j
public class StateServiceImpl implements StateService {

    private static final String STATE_FILE_PATH = System.getProperty("user.home") + "/.api-agent/state.json";
    private final File stateFile = new File(STATE_FILE_PATH);

    private Map<String, ApiSpecification> specifications = new ConcurrentHashMap<>();
    private Map<String, String> credentials = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringEncryptor encryptor;

    /**
     * Constructs the StateService with a Jasypt {@link StringEncryptor}.
     * The encryptor bean is automatically configured and provided by the Jasypt Spring Boot starter.
     *
     * @param encryptor The encryptor used for securing and unsecure credentials.
     */
    public StateServiceImpl(StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    /**
     * Initializes the service by loading the persistent state from the JSON file into memory.
     * This method is automatically called by Spring after the bean has been constructed.
     */
    @PostConstruct
    public void init() {
        loadState();
    }

    /**
     * {@inheritDoc}
     * This implementation stores the specification in the in-memory map and immediately
     * persists the updated state to the JSON file.
     */
    @Override
    public void saveSpecification(String alias, ApiSpecification spec) {
        specifications.put(alias, spec);
        saveState();
    }

    /**
     * {@inheritDoc}
     * This implementation retrieves the specification directly from the in-memory map.
     */
    @Override
    public ApiSpecification getSpecification(String alias) {
        return specifications.get(alias);
    }

    /**
     * {@inheritDoc}
     * This implementation first encrypts the provided token before storing it in the
     * in-memory map and then persists the updated, encrypted state to the JSON file.
     */
    @Override
    public void saveCredential(String alias, String token) {
        log.info("Encrypting and saving credential for alias '{}'", alias);
        credentials.put(alias, encryptor.encrypt(token));
        saveState();
    }

    /**
     * {@inheritDoc}
     * This implementation retrieves the encrypted token from the in-memory map and
     * decrypts it on-the-fly. If decryption fails (e.g., due to a misconfigured or
     * changed secret key), an error is logged and null is returned.
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
            log.error("Could not decrypt credential for alias '{}'. The secret key may have changed.", alias);
            return null;
        }
    }

    /**
     * A synchronized method to save the current in-memory state to the JSON file on disk.
     * The state includes the map of specifications and the map of encrypted credentials.
     *
     * @throws RuntimeException if an I/O error occurs during file writing.
     */
    private synchronized void saveState() {
        try {
            stateFile.getParentFile().mkdirs();
            Map<String, Object> state = new HashMap<>();
            state.put("specifications", specifications);
            state.put("credentials", credentials); // The credentials map now holds encrypted values
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
        } catch (IOException e) {
            log.error("CRITICAL: Failed to save application state to {}", STATE_FILE_PATH, e);
            throw new RuntimeException("Failed to save application state", e);
        }
    }

    /**
     * A synchronized method to load the application state from the JSON file on disk.
     * If the file does not exist, the application starts with a fresh state. If the file is
     * corrupted or cannot be parsed, it is backed up, and the application starts fresh.
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
                log.warn("Could not load or parse state file at {}. A backup will be created, and the application will start fresh. Error: {}", STATE_FILE_PATH, e.getMessage());
                backupCorruptedStateFile();
                this.specifications = new ConcurrentHashMap<>();
                this.credentials = new ConcurrentHashMap<>();
            }
        } else {
            log.info("No state file found at {}, starting with a clean state.", STATE_FILE_PATH);
        }
    }

    /**
     * A helper method that backs up the state file by renaming it with a ".corrupted" suffix
     * and a timestamp. This is called when the state file cannot be parsed on startup.
     */
    private void backupCorruptedStateFile() {
        File backupFile = new File(STATE_FILE_PATH + ".corrupted." + System.currentTimeMillis());
        if (stateFile.renameTo(backupFile)) {
            log.info("Backed up corrupted state file to {}", backupFile.getAbsolutePath());
        }
    }
}