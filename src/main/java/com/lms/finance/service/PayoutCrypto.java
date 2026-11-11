package com.lms.finance.service;

import com.lms.common.exception.BusinessRuleViolationException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mã hóa số tài khoản ngân hàng của giảng viên (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn C). AES-256-GCM với
 * IV ngẫu nhiên cho mỗi lần mã hóa; định dạng lưu {@code v1:base64(iv||ciphertext+tag)} (tiền tố phiên bản để sau này
 * xoay khóa). {@link #hash} dùng HMAC-SHA256 với khóa dẫn xuất riêng để dò trùng số tài khoản mà không giải mã được.
 *
 * <p>Khóa gốc là 32 byte base64 ở biến môi trường {@code LMS_PAYOUT_ENCRYPTION_KEY} (thuộc tính
 * {@code lms.security.payout-encryption-key}). Thiếu hoặc sai khóa thì {@link #isConfigured()} = false và tính năng tài
 * khoản nhận tiền báo lỗi rõ ràng — KHÔNG làm backend vỡ khi khởi động. MẤT KHÓA = MẤT SỐ TÀI KHOẢN đã lưu: phải sao lưu khóa.
 */
@Slf4j
@Component
public class PayoutCrypto {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] HASH_KEY_LABEL = "payout-account-hash-v1".getBytes(StandardCharsets.UTF_8);

    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();

    public PayoutCrypto(@Value("${lms.security.payout-encryption-key:}") String base64Key) {
        byte[] key = null;
        if (base64Key != null && !base64Key.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
                if (decoded.length == 32) {
                    key = decoded;
                } else {
                    log.error("LMS_PAYOUT_ENCRYPTION_KEY phải là 32 byte (base64) nhưng có {} byte — tính năng tài khoản nhận tiền bị tắt", decoded.length);
                }
            } catch (IllegalArgumentException e) {
                log.error("LMS_PAYOUT_ENCRYPTION_KEY không phải base64 hợp lệ — tính năng tài khoản nhận tiền bị tắt");
            }
        } else {
            log.warn("Chưa cấu hình LMS_PAYOUT_ENCRYPTION_KEY — tính năng tài khoản nhận tiền/rút tiền chưa dùng được");
        }
        this.masterKey = key;
    }

    public boolean isConfigured() {
        return masterKey != null;
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessRuleViolationException("PAYOUT_NOT_CONFIGURED",
                    "Tính năng tài khoản nhận tiền chưa được cấu hình khóa mã hóa trên máy chủ. Vui lòng liên hệ quản trị viên.");
        }
    }

    public String encrypt(String plain) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hóa được dữ liệu tài khoản", e);
        }
    }

    public String decrypt(String blob) {
        requireConfigured();
        if (blob == null || !blob.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Định dạng dữ liệu mã hóa không hợp lệ");
        }
        try {
            byte[] in = Base64.getDecoder().decode(blob.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(in, 0, IV_BYTES);
            byte[] ct = java.util.Arrays.copyOfRange(in, IV_BYTES, in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            // Không đưa nội dung lỗi/dữ liệu vào message: có thể là khóa sai hoặc dữ liệu bị sửa.
            throw new IllegalStateException("Không giải mã được dữ liệu tài khoản (sai khóa hoặc dữ liệu bị sửa)");
        }
    }

    /** HMAC-SHA256 (hex) của số tài khoản — dùng dò trùng, không giải ngược được. */
    public String hash(String plain) {
        requireConfigured();
        try {
            Mac keyMac = Mac.getInstance("HmacSHA256");
            keyMac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            byte[] hashKey = keyMac.doFinal(HASH_KEY_LABEL);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không băm được số tài khoản", e);
        }
    }

    public static String mask(String last4) {
        return "•••• " + last4;
    }
}
