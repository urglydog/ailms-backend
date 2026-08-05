package com.lms.auth.service;

import com.lms.auth.dto.UserDto.UpdateUserReq;
import com.lms.auth.dto.UserDto.UserRes;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserRes> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToRes)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserRes getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return mapToRes(user);
    }
    
    @Transactional(readOnly = true)
    public UserRes getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return mapToRes(user);
    }

    @Transactional
    public UserRes updateUser(Long id, UpdateUserReq req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        user.setFullName(req.fullName());
        user.setRole(req.role());
        user.setIsActive(req.isActive());
        
        // Nếu user bị khóa (isActive = false), refresh token hiện tại của user sẽ không thể
        // dùng để xin cấp lại token mới nhờ cơ chế chặn ở AuthService.refreshToken
        
        return mapToRes(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        // Không xóa cứng để giữ toàn vẹn khóa ngoại (khóa học, hóa đơn, v.v)
        // Chỉ khóa tài khoản (Soft Delete / Deactivate)
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * UC05 - Change Password: Verify current password and update to new password
     * Only for users with LOCAL auth provider (those who have a password hash)
     * Google OAuth users cannot change password (passwordHash is NULL)
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (user.getPasswordHash() == null) {
            throw new RuntimeException("Tài khoản này sử dụng đăng nhập Google. Không thể đổi mật khẩu.");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Mật khẩu hiện tại không chính xác");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserRes mapToRes(User user) {
        return new UserRes(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getAuthProvider(),
                user.getPreferredLanguage(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
