package com.icap.logicoti.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
public class TotpService {

    static final long TIME_STEP_SECONDS = 30;
    private static final int SECRET_BYTES = 20;
    private static final int CODE_MODULUS = 1_000_000;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return encodeBase32(secret);
    }

    public Long findMatchingTimeStep(
            String base32Secret,
            String requestedCode,
            Instant instant
    ) {
        String code = requestedCode == null
                ? ""
                : requestedCode.trim();

        if (!code.matches("\\d{6}")) {
            return null;
        }

        long currentStep = instant.getEpochSecond() / TIME_STEP_SECONDS;

        // RFC 6238 recomienda una ventana pequeña. Se admite un paso antes
        // y después para tolerar unos segundos de desfase de reloj.
        for (long candidate = currentStep - 1; candidate <= currentStep + 1; candidate++) {
            if (constantTimeEquals(code, generateCode(base32Secret, candidate))) {
                return candidate;
            }
        }

        return null;
    }

    String generateCode(String base32Secret, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % CODE_MODULUS);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No fue posible validar el código temporal.", exception);
        }
    }

    String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;

            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 31));
        }

        return result.toString();
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (char character : normalized.toCharArray()) {
            int decoded = BASE32_ALPHABET.indexOf(character);
            if (decoded < 0) {
                throw new IllegalArgumentException("El secreto TOTP no tiene un formato válido.");
            }

            buffer = (buffer << 5) | decoded;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }

        return output;
    }

    private boolean constantTimeEquals(String first, String second) {
        int difference = first.length() ^ second.length();
        int length = Math.min(first.length(), second.length());
        for (int index = 0; index < length; index++) {
            difference |= first.charAt(index) ^ second.charAt(index);
        }
        return difference == 0;
    }
}
