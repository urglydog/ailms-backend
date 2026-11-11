package com.lms.finance.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Token xác nhận qua email cho thao tác tiền (11/11/2026, Giai đoạn C): xác nhận tài khoản nhận tiền mới, xác nhận yêu
 * cầu rút. Token ngẫu nhiên 256 bit gửi trong email; Redis chỉ lưu SHA-256 của token (lộ Redis cũng không dùng được),
 * gắn với loại + id + người dùng, TTL 30 phút, dùng MỘT LẦN (GETDEL nguyên tử). Redis lỗi thì ném lỗi — thao tác tiền
 * từ chối chứ không bỏ qua bước xác nhận.
 */
@Service
@RequiredArgsConstructor
public class FinanceConfirmationService {

    public static final String TYPE_ACCOUNT = "ACCOUNT";
    public static final String TYPE_PAYOUT = "PAYOUT";
    static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "finance:confirm:";

    public record Confirmation(String type, Long entityId, Long userId) {}

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    /** Tạo token (trả về dạng rõ để gửi email; chỉ băm của nó được lưu). */
    public String issue(String type, Long entityId, Long userId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set(KEY_PREFIX + sha256(token), type + ":" + entityId + ":" + userId, TTL);
        return token;
    }

    /** Tiêu thụ token: trả nội dung nếu hợp lệ rồi xóa ngay; token sai/hết hạn/đã dùng → empty. */
    public Optional<Confirmation> consume(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        String value = redis.opsForValue().getAndDelete(KEY_PREFIX + sha256(token.trim()));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Confirmation(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
