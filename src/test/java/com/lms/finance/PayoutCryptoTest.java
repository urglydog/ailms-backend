package com.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.finance.service.PayoutCrypto;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PayoutCryptoTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String KEY_B = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    @Test
    void encryptThenDecrypt_returnsOriginal_andCiphertextHidesThePlainNumber() {
        PayoutCrypto crypto = new PayoutCrypto(KEY_A);
        String blob = crypto.encrypt("0123456789");

        assertThat(blob).startsWith("v1:").doesNotContain("0123456789");
        assertThat(crypto.decrypt(blob)).isEqualTo("0123456789");
    }

    @Test
    void encrypt_usesFreshIvEveryTime() {
        PayoutCrypto crypto = new PayoutCrypto(KEY_A);
        assertThat(crypto.encrypt("0123456789")).isNotEqualTo(crypto.encrypt("0123456789"));
    }

    @Test
    void decrypt_withWrongKey_orTamperedData_failsWithoutLeakingContent() {
        String blob = new PayoutCrypto(KEY_A).encrypt("0123456789");

        assertThatThrownBy(() -> new PayoutCrypto(KEY_B).decrypt(blob))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("0123456789");

        byte[] raw = Base64.getDecoder().decode(blob.substring(3));
        raw[raw.length - 1] ^= 1; // lật 1 bit
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> new PayoutCrypto(KEY_A).decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PayoutCrypto(KEY_A).decrypt("không-phải-định-dạng")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hash_isDeterministicPerKey_differentPerInput_andNotThePlainNumber() {
        PayoutCrypto crypto = new PayoutCrypto(KEY_A);
        assertThat(crypto.hash("0123456789")).isEqualTo(crypto.hash("0123456789")).hasSize(64);
        assertThat(crypto.hash("0123456789")).isNotEqualTo(crypto.hash("0123456788"));
        assertThat(new PayoutCrypto(KEY_B).hash("0123456789")).isNotEqualTo(crypto.hash("0123456789"));
    }

    @Test
    void missingOrInvalidKey_meansNotConfigured_andNeverThrowsAtConstruction() {
        for (String bad : new String[]{"", "   ", null, "không-phải-base64!!", Base64.getEncoder().encodeToString("tooshort".getBytes())}) {
            PayoutCrypto crypto = new PayoutCrypto(bad);
            assertThat(crypto.isConfigured()).isFalse();
            assertThatThrownBy(crypto::requireConfigured).isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> crypto.encrypt("x")).isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Test
    void mask_showsOnlyLastFourDigits() {
        assertThat(PayoutCrypto.mask("6789")).isEqualTo("•••• 6789");
    }
}
