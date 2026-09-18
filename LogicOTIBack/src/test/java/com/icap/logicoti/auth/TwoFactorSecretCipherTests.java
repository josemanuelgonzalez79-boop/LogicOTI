package com.icap.logicoti.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwoFactorSecretCipherTests {

    @Test
    void encryptsWithRandomIvAndDecryptsWithTheConfiguredKey() {
        TwoFactorSecretCipher cipher = new TwoFactorSecretCipher("clave-prueba-segura");

        String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String second = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(first).doesNotContain("JBSWY3DPEHPK3PXP");
    }

    @Test
    void refusesToDecryptWithAnotherKey() {
        String encrypted = new TwoFactorSecretCipher("clave-original").encrypt("SECRETO");

        assertThatThrownBy(() -> new TwoFactorSecretCipher("clave-distinta").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
