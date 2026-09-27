package com.lms.announcement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.announcement.dto.SystemAnnouncementDto.BannerRes;
import com.lms.announcement.dto.SystemAnnouncementDto.CreateReq;
import com.lms.announcement.dto.SystemAnnouncementDto.Res;
import com.lms.announcement.entity.SystemAnnouncement;
import com.lms.announcement.repository.SystemAnnouncementRepository;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.AnnouncementAudience;
import com.lms.common.enums.AnnouncementDispatchStatus;
import com.lms.common.enums.AnnouncementSeverity;
import com.lms.common.enums.Role;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** (26/09/2026, tính năng mới) — thông báo hệ thống từ Admin, phát tán qua job nền Redis. */
@ExtendWith(MockitoExtension.class)
class SystemAnnouncementServiceTest {

    private static final String ADMIN_EMAIL = "admin@lms.local";

    @Mock private SystemAnnouncementRepository announcementRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;

    private SystemAnnouncementService service;

    private User admin;

    @BeforeEach
    void setUp() {
        service = new SystemAnnouncementService(announcementRepository, userRepository, notificationService,
                redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "queueKey", "lms:admin-broadcast:jobs");

        admin = new User();
        admin.setId(99L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setFullName("Quan Tri Vien");
    }

    @Test
    void create_audienceAll_savesAndEnqueuesJob() {
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(announcementRepository.save(any(SystemAnnouncement.class))).thenAnswer(inv -> {
            SystemAnnouncement a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        CreateReq req = new CreateReq("Bảo trì hệ thống", "Hệ thống bảo trì lúc 2h sáng.",
                AnnouncementSeverity.HIGH, AnnouncementAudience.ALL, null, null);
        Res res = service.create(ADMIN_EMAIL, req);

        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.severity()).isEqualTo(AnnouncementSeverity.HIGH);
        verify(listOperations).leftPush(eq("lms:admin-broadcast:jobs"), contains("\"announcementId\":1"));
    }

    @Test
    void create_specificUserWithoutTargetId_throwsInvalidRequest() {
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        CreateReq req = new CreateReq("Riêng bạn", "Nội dung", AnnouncementSeverity.LOW,
                AnnouncementAudience.SPECIFIC_USER, null, null);

        assertThatThrownBy(() -> service.create(ADMIN_EMAIL, req)).isInstanceOf(InvalidRequestException.class);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void create_allAudienceWithTargetId_throwsInvalidRequest() {
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        CreateReq req = new CreateReq("Sai tham so", "Nội dung", AnnouncementSeverity.LOW,
                AnnouncementAudience.ALL, 5L, null);

        assertThatThrownBy(() -> service.create(ADMIN_EMAIL, req)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void create_specificUser_targetNotFound_throwsResourceNotFound() {
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userRepository.existsById(404L)).thenReturn(false);

        CreateReq req = new CreateReq("Riêng bạn", "Nội dung", AnnouncementSeverity.LOW,
                AnnouncementAudience.SPECIFIC_USER, 404L, null);

        assertThatThrownBy(() -> service.create(ADMIN_EMAIL, req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dispatch_audienceInstructor_notifiesOnlyInstructorsAndMarksDone() {
        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setId(1L);
        announcement.setTitle("Cập nhật chính sách");
        announcement.setContent("Nội dung");
        announcement.setSeverity(AnnouncementSeverity.MEDIUM);
        announcement.setAudience(AnnouncementAudience.INSTRUCTOR);
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));

        User instructor1 = new User();
        instructor1.setId(10L);
        User instructor2 = new User();
        instructor2.setId(11L);
        when(userRepository.findByRole(Role.INSTRUCTOR)).thenReturn(List.of(instructor1, instructor2));

        service.dispatch(1L);

        verify(notificationService).notify(eq(10L), eq("SYSTEM_MEDIUM"), eq("Cập nhật chính sách"), eq("Nội dung"), isNull());
        verify(notificationService).notify(eq(11L), eq("SYSTEM_MEDIUM"), eq("Cập nhật chính sách"), eq("Nội dung"), isNull());
        assertThat(announcement.getDispatchStatus()).isEqualTo(AnnouncementDispatchStatus.DONE);
        assertThat(announcement.getSentCount()).isEqualTo(2);
        assertThat(announcement.getTotalRecipients()).isEqualTo(2);
    }

    @Test
    void dispatch_audienceSpecificUser_notifiesOnlyThatUser() {
        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setId(2L);
        announcement.setTitle("Riêng bạn");
        announcement.setContent("Nội dung");
        announcement.setSeverity(AnnouncementSeverity.LOW);
        announcement.setAudience(AnnouncementAudience.SPECIFIC_USER);
        announcement.setTargetUserId(7L);
        when(announcementRepository.findById(2L)).thenReturn(Optional.of(announcement));

        User target = new User();
        target.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));

        service.dispatch(2L);

        verify(notificationService, times(1)).notify(eq(7L), anyString(), anyString(), anyString(), any());
        assertThat(announcement.getSentCount()).isEqualTo(1);
    }

    @Test
    void dispatch_announcementNotFound_doesNothing() {
        when(announcementRepository.findById(999L)).thenReturn(Optional.empty());

        service.dispatch(999L);

        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void getActiveBanner_noneActive_returnsNull() {
        when(announcementRepository.findLatestActiveHighSeverity(any(LocalDateTime.class))).thenReturn(Optional.empty());

        BannerRes banner = service.getActiveBanner();

        assertThat(banner).isNull();
    }
}
