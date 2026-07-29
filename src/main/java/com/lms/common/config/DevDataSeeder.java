package com.lms.common.config;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tạo tài khoản mẫu cho môi trường phát triển.
 *
 * <p>Vì sao không seed người dùng bằng SQL: mật khẩu phải được băm bằng
 * {@link PasswordEncoder} thật (Bcrypt cost 12 theo BR-AUTH-01). Nhúng sẵn một chuỗi
 * hash vào file SQL là hash không kiểm chứng được và dễ sai cost — nên phần này chạy
 * bằng Java để hash được sinh đúng ngay lúc khởi động.
 *
 * <p>Chỉ hoạt động ở profile {@code dev}. Idempotent: đã có dữ liệu thì bỏ qua, nên
 * chạy lại {@code docker compose up} nhiều lần không sinh trùng.
 *
 * <p><b>Cảnh báo:</b> mật khẩu ở đây là mật khẩu yếu dùng để test. Không bao giờ bật
 * profile {@code dev} ở môi trường thật.
 */
@Configuration
@Profile("dev")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Mật khẩu chung cho mọi tài khoản mẫu — chỉ dùng khi phát triển. */
    private static final String DEV_PASSWORD = "Password123!";

    @Bean
    public ApplicationRunner seedDevUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                log.info("Bo qua seed nguoi dung: da co {} tai khoan trong CSDL", userRepository.count());
                return;
            }

            userRepository.save(buildUser("admin@lms.local", "Quan Tri Vien", Role.ADMIN, passwordEncoder));
            userRepository.save(buildUser("instructor@lms.local", "Tran Thanh Ha", Role.INSTRUCTOR, passwordEncoder));
            userRepository.save(buildUser("student1@lms.local", "Nguyen Van A", Role.STUDENT, passwordEncoder));
            userRepository.save(buildUser("student2@lms.local", "Le Thi B", Role.STUDENT, passwordEncoder));

            log.info("Da seed 4 tai khoan dev (admin/instructor/student1/student2@lms.local), mat khau: {}",
                    DEV_PASSWORD);
        };
    }

    private User buildUser(String email, String fullName, Role role, PasswordEncoder encoder) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setPasswordHash(encoder.encode(DEV_PASSWORD));
        user.setAuthProvider("LOCAL");
        user.setPreferredLanguage("vi-VN");
        user.setIsActive(true);
        return user;
    }
}
