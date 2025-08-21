package com.agent.service.impl;

import com.agent.model.ApiSpecification;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateServiceImplTest {

    @Mock
    private StringEncryptor stringEncryptor;

    @InjectMocks
    private StateServiceImpl stateService; // We test the real implementation

    @BeforeEach
    void setUp() {
        // We can't test the file I/O part without a real file,
        // so we focus on the encryption/decryption logic which is the core enhancement.
        // File I/O would be better in an integration test.
    }

    @Test
    void saveCredential_shouldEncryptTokenBeforeStoring() {
        String alias = "my-api";
        String plainToken = "my-secret-token";
        String encryptedToken = "encrypted-token";

        when(stringEncryptor.encrypt(plainToken)).thenReturn(encryptedToken);

        stateService.saveCredential(alias, plainToken);

        // This is a bit tricky as the map is private. We verify via the getter.
        // We'll mock the decrypt to test the full loop.
        when(stringEncryptor.decrypt(encryptedToken)).thenReturn(plainToken);

        String retrievedToken = stateService.getCredential(alias);

        verify(stringEncryptor, times(1)).encrypt(plainToken);
        verify(stringEncryptor, times(1)).decrypt(encryptedToken);
        assertThat(retrievedToken).isEqualTo(plainToken);
    }

    @Test
    void getCredential_shouldReturnNullIfAliasNotFound() {
        String retrievedToken = stateService.getCredential("non-existent-api");
        assertThat(retrievedToken).isNull();
        verify(stringEncryptor, never()).decrypt(any());
    }
}