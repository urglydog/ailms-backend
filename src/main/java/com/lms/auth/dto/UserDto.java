package com.lms.auth.dto;

import com.lms.common.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class UserDto {
    public record UserRes(
            Long id,
            String email,
            String fullName,
            String avatarUrl,
            Role role,
            String authProvider,
            String preferredLanguage,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record UpdateUserReq(
            @NotBlank(message = "Họ tên không được để trống")
            String fullName,
            
            @NotNull(message = "Quyền (Role) không được để trống")
            Role role,
            
            @NotNull(message = "Trạng thái không được để trống")
            Boolean isActive
    ) {}
}
