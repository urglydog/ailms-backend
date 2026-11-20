package com.lms.auth.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.AccountLockRegistry;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.certificate.service.CertificateService;
import com.lms.common.enums.Role;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.wishlist.repository.WishlistItemRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hiệu năng đợt 3: trang Quản lý người dùng tìm/lọc/sắp xếp/phân trang PHÍA SERVER — trước đây
 * tải toàn bộ người dùng về trình duyệt ({@code GET /api/v1/users}) rồi mới lọc.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminSearchTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StorageService storageService;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private CertificateService certificateService;
    @Mock private AccountLockRegistry accountLockRegistry;

    @InjectMocks private UserService userService;

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setEmail(name.toLowerCase() + "@lms.local");
        u.setRole(Role.STUDENT);
        return u;
    }

    @Test
    void searchUsers_mapsQueryFilterSortAndPage() {
        when(userRepository.searchForAdmin(any(), anyLong(), any(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(1L, "An"))));

        var page = userService.searchUsers("  An  ", "INSTRUCTOR", "fullName", "asc", 2, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchForAdmin(eq("%an%"), eq(-1L), eq(Role.INSTRUCTOR), eq(false), eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1); // giao diện đếm từ 1
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("fullName").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(page.getContent()).extracting("fullName").containsExactly("An");
        verify(userRepository, never()).findAll();
    }

    @Test
    void searchUsers_lockedAndSpamFilters_andUnknownSortFallsBackToNewest() {
        when(userRepository.searchForAdmin(any(), anyLong(), any(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        userService.searchUsers(null, "LOCKED", "password", "desc", 1, 20);
        userService.searchUsers("", "SPAM", null, null, 1, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchForAdmin(isNull(), eq(-1L), isNull(), eq(true), eq(false), pageable.capture());
        verify(userRepository).searchForAdmin(isNull(), eq(-1L), isNull(), eq(false), eq(true), any(Pageable.class));
        // Cột sắp xếp lạ (vd "password") bị bỏ qua — chỉ nhận danh sách cho phép.
        assertThat(pageable.getValue().getSort().getOrderFor("password")).isNull();
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void searchUsers_capsPageSize() {
        when(userRepository.searchForAdmin(any(), anyLong(), any(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        userService.searchUsers(null, "ALL", "id", "asc", 1, 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchForAdmin(any(), anyLong(), any(), anyBoolean(), anyBoolean(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void countUsers_groupsByRoleAndCountsLockedAndSpam() {
        when(userRepository.countGroupedByRole()).thenReturn(List.of(
                new Object[] {Role.STUDENT, 80L}, new Object[] {Role.INSTRUCTOR, 15L}, new Object[] {Role.ADMIN, 2L}));
        when(userRepository.countByIsActiveFalse()).thenReturn(3L);
        when(userRepository.countByIsSpamFlaggedTrue()).thenReturn(1L);

        Map<String, Long> counts = userService.countUsers();

        assertThat(counts).containsEntry("ALL", 97L).containsEntry("STUDENT", 80L).containsEntry("INSTRUCTOR", 15L)
                .containsEntry("ADMIN", 2L).containsEntry("LOCKED", 3L).containsEntry("SPAM", 1L);
    }
}
