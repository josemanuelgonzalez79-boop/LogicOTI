package com.icap.logicoti.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TwoFactorSecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte FORMAT_VERSION = 1;

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorSecretCipher(
            @Value("${security.two-factor.encryption-key}") String configuredKey
    ) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException(
                    "Configure TWO_FACTOR_ENCRYPTION_KEY o JWT_SECRET antes de iniciar LogicOTI."
            );
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("LogicOTI/TOTP/AES-GCM/v1".getBytes(StandardCharsets.UTF_8));
            this.encryptionKey = new SecretKeySpec(
                    digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8)),
                    "AES"
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No fue posible preparar el cifrado de 2FA.", exception);
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(1 + iv.length + cipherText.length)
                            .put(FORMAT_VERSION)
                            .put(iv)
                            .put(cipherText)
                            .array()
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No fue posible proteger el secreto de 2FA.", exception);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(encryptedText));
            if (payload.get() != FORMAT_VERSION || payload.remaining() <= IV_BYTES) {
                throw new IllegalArgumentException("Formato de secreto 2FA no reconocido.");
            }
            byte[] iv = new byte[IV_BYTES];
            payload.get(iv);
            byte[] cipherText = new byte[payload.remaining()];
            payload.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | BufferUnderflowException exception) {
            throw new IllegalStateException(
                    "No fue posible recuperar el secreto de 2FA. Revise la clave de cifrado.",
                    exception
            );
        }
    }
}
