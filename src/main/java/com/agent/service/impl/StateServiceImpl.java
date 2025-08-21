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

@Service
@Slf4j
public class StateServiceImpl implements StateService {

    private static final String STATE_FILE_PATH = System.getProperty("user.home") + "/.api-agent/state.json";
    private final File stateFile = new File(STATE_FILE_PATH);

    private Map<String, ApiSpecification> specifications = new ConcurrentHashMap<>();
    private Map<String, String> credentials = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringEncryptor encryptor;

    // The StringEncryptor bean is automatically provided by the Jasypt starter
    public StateServiceImpl(StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @PostConstruct
    public void init() {
        loadState();
    }

    @Override
    public void saveSpecification(String alias, ApiSpecification spec) {
        specifications.put(alias, spec);
        saveState();
    }

    @Override
    public ApiSpecification getSpecification(String alias) {
        return specifications.get(alias);
    }

    @Override
    public void saveCredential(String alias, String token) {
        // --- NEW: Encrypt the token before saving ---
        log.info("Encrypting and saving credential for alias '{}'", alias);
        credentials.put(alias, encryptor.encrypt(token));
        saveState();
    }

    @Override
    public String getCredential(String alias) {
        String encryptedToken = credentials.get(alias);
        if (encryptedToken == null) {
            return null;
        }
        try {
            // --- NEW: Decrypt the token on retrieval ---
            return encryptor.decrypt(encryptedToken);
        } catch (Exception e) {
            log.error("Could not decrypt credential for alias '{}'. The secret key may have changed.", alias);
            return null;
        }
    }

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

    private void backupCorruptedStateFile() {
        File backupFile = new File(STATE_FILE_PATH + ".corrupted." + System.currentTimeMillis());
        if (stateFile.renameTo(backupFile)) {
            log.info("Backed up corrupted state file to {}", backupFile.getAbsolutePath());
        }
    }
}