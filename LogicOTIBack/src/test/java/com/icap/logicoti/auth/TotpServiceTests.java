package com.icap.logicoti.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTests {

    private final TotpService service = new TotpService();

    @Test
    void matchesTheRfc6238Sha1TestVectorUsingSixDigits() {
        String secret = service.encodeBase32(
                "12345678901234567890".getBytes(StandardCharsets.US_ASCII)
        );

        assertThat(service.generateCode(secret, 1)).isEqualTo("287082");
        assertThat(service.findMatchingTimeStep(
                secret,
                "287082",
                Instant.ofEpochSecond(59)
        )).isEqualTo(1L);
    }

    @Test
    void rejectsCodesThatDoNotHaveExactlySixDigits() {
        String secret = service.generateSecret();

        assertThat(service.findMatchingTimeStep(secret, "12345", Instant.now())).isNull();
        assertThat(service.findMatchingTimeStep(secret, "ABCDEF", Instant.now())).isNull();
    }
}
