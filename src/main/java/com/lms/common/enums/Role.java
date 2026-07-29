package com.lms.common.enums;

/**
 * Vai trò người dùng. Guest KHÔNG phải role — đó là trạng thái chưa xác thực.
 * Spring Security dùng tiền tố ROLE_ khi kiểm quyền: hasRole('STUDENT') -> ROLE_STUDENT.
 *
 * <p>Dùng bởi: User</p>
 */
public enum Role {
    STUDENT,
    INSTRUCTOR,
    ADMIN
}
