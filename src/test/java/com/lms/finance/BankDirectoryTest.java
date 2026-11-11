package com.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BankDirectoryTest {

    @Test
    void codesAndBinsAreUnique_andBinsAreSixDigits() {
        var codes = new HashSet<String>();
        var bins = new HashSet<String>();
        BankDirectory.all().forEach(b -> {
            assertThat(codes.add(b.code())).as("mã trùng: " + b.code()).isTrue();
            assertThat(bins.add(b.bin())).as("BIN trùng: " + b.bin()).isTrue();
            assertThat(b.bin()).matches("[0-9]{6}");
            assertThat(b.name()).isNotBlank();
        });
    }

    @Test
    void find_isCaseInsensitiveAndNullSafe() {
        assertThat(BankDirectory.find("vcb")).isPresent();
        assertThat(BankDirectory.find(" VCB ")).isPresent();
        assertThat(BankDirectory.find("khong-co")).isEmpty();
        assertThat(BankDirectory.find(null)).isEmpty();
    }
}
