package com.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameNormalizerTest {

    @Test
    void normalize_removesDiacriticsAndUppercases() {
        assertThat(NameNormalizer.normalize("Nguyễn Chí  Thiện")).isEqualTo("NGUYEN CHI THIEN");
        assertThat(NameNormalizer.normalize("Đặng Thị Ánh")).isEqualTo("DANG THI ANH");
        assertThat(NameNormalizer.normalize("  Trần-Văn.B ")).isEqualTo("TRAN VAN B");
        assertThat(NameNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void matches_ignoresDiacriticsCaseAndWordOrder() {
        assertThat(NameNormalizer.matches("NGUYEN CHI THIEN", "Nguyễn Chí Thiện")).isTrue();
        assertThat(NameNormalizer.matches("THIEN NGUYEN CHI", "Nguyễn Chí Thiện")).isTrue();
    }

    @Test
    void matches_rejectsDifferentOrMissingNames() {
        assertThat(NameNormalizer.matches("NGUYEN VAN A", "Nguyễn Chí Thiện")).isFalse();
        assertThat(NameNormalizer.matches("NGUYEN CHI", "Nguyễn Chí Thiện")).isFalse();
        assertThat(NameNormalizer.matches("", "Nguyễn Chí Thiện")).isFalse();
        assertThat(NameNormalizer.matches(null, null)).isFalse();
    }
}
