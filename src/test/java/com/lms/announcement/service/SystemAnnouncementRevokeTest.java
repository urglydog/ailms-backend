package com.lms.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.announcement.dto.SystemAnnouncementDto.UpdateReq;
import com.lms.announcement.entity.SystemAnnouncement;
import com.lms.announcement.repository.SystemAnnouncementRepository;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.AnnouncementAudience;
import com.lms.common.enums.AnnouncementSeverity;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.NotificationService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class SystemAnnouncementRevokeTest {

    @Mock private SystemAnnouncementRepository announcementRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private StringRedisTemplate redisTemplate;

    private SystemAnnouncementService service;
    private SystemAnnouncement announcement;

    @BeforeEach
    void setUp() {
        service = new SystemAnnouncementService(announcementRepository, userRepository, notificationService, redisTemplate, new ObjectMapper());
        User admin = new User();
        admin.setFullName("Admin");
        announcement = new SystemAnnouncement();
        announcement.setId(12L);
        announcement.setTitle("Bảo trì");
        announcement.setContent("Nội dung cũ");
        announcement.setSeverity(AnnouncementSeverity.HIGH);
        announcement.setAudience(AnnouncementAudience.ALL);
        announcement.setCreatedByAdmin(admin);
        org.mockito.Mockito.lenient().when(announcementRepository.findById(12L)).thenReturn(Optional.of(announcement));
        org.mockito.Mockito.lenient().when(announcementRepository.save(any(SystemAnnouncement.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void update_changesTheAnnouncementAndEveryDeliveredNotification() {
        var res = service.update(12L, new UpdateReq("  Bảo trì lúc 22h ", " Nội dung mới ", LocalDateTime.of(2026, 12, 1, 0, 0)));

        assertThat(res.title()).isEqualTo("Bảo trì lúc 22h");
        verify(notificationService).updateBySource("ANN:12", "Bảo trì lúc 22h", "Nội dung mới");
    }

    @Test
    void revoke_hidesDeliveredNotificationsKeepsTheRecordAndIsIdempotent() {
        service.revoke(12L);
        LocalDateTime firstRevoke = announcement.getRevokedAt();
        service.revoke(12L);

        assertThat(firstRevoke).isNotNull();
        assertThat(announcement.getRevokedAt()).isEqualTo(firstRevoke);
        verify(notificationService, org.mockito.Mockito.times(1)).hideBySource("ANN:12");
    }

    @Test
    void update_afterRevokeIsRejected() {
        announcement.setRevokedAt(LocalDateTime.now());

        assertThatThrownBy(() -> service.update(12L, new UpdateReq("A", "B", null))).isInstanceOf(InvalidRequestException.class);
        verify(notificationService, never()).updateBySource(any(), any(), any());
    }

    @Test
    void dispatch_skipsARevokedAnnouncement() {
        announcement.setRevokedAt(LocalDateTime.now());

        service.dispatch(12L);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
        verify(userRepository, never()).findIdsForAudience(any(), any());
    }

    @Test
    void sourceRef_usesTheAnnouncementId() {
        assertThat(SystemAnnouncementService.sourceRef(7L)).isEqualTo("ANN:7");
        org.mockito.Mockito.verify(announcementRepository, never()).delete(any(SystemAnnouncement.class));
    }

    @Test
    void restore_showsTheNotificationsAgainAndIsIdempotent() {
        announcement.setRevokedAt(LocalDateTime.now());

        service.restore(12L);
        service.restore(12L);

        assertThat(announcement.getRevokedAt()).isNull();
        verify(notificationService, org.mockito.Mockito.times(1)).unhideBySource("ANN:12");
    }

    @Test
    void deleteRevoked_removesRecordAndDeliveredCopies_butOnlyAfterRevoking() {
        assertThatThrownBy(() -> service.deleteRevoked(12L)).isInstanceOf(InvalidRequestException.class);
        verify(announcementRepository, never()).delete(any(SystemAnnouncement.class));

        announcement.setRevokedAt(LocalDateTime.now());
        service.deleteRevoked(12L);

        verify(notificationService).deleteBySource("ANN:12");
        verify(announcementRepository).delete(announcement);
    }
}
