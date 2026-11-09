package com.lms.common.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Giới hạn request/phút theo IP (hiệu năng đợt 4). Cấu hình qua {@code app.rate-limit.*}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    /** Đăng nhập, đăng ký, OTP, quên mật khẩu — chống dò mật khẩu / spam email. */
    private int authPerMinute = 20;
    /** Tìm kiếm khóa học công khai. */
    private int searchPerMinute = 120;
    /** Mọi API còn lại. */
    private int apiPerMinute = 600;
}
