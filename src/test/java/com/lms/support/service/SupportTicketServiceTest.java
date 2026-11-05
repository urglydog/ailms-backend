package com.lms.support.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.Role;
import com.lms.common.enums.SupportTicketCategory;
import com.lms.common.enums.SupportTicketStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.support.dto.SupportDto;
import com.lms.support.entity.SupportTicket;
import com.lms.support.repository.SupportTicketAttachmentRepository;
import com.lms.support.repository.SupportTicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportTicketServiceTest {

    @Mock private SupportTicketRepository ticketRepository;
    @Mock private SupportTicketAttachmentRepository attachmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private StorageService storageService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private SupportTicketService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxFiles", 3);
        ReflectionTestUtils.setField(service, "maxSizeMb", 5L);
        ReflectionTestUtils.setField(service, "ipLimitPerHour", 3);
        ReflectionTestUtils.setField(service, "emailLimitPerDay", 5);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", 123L);
            }
            return t;
        });
    }

    private SupportDto.CreateReq req() {
        return new SupportDto.CreateReq(" User@Mail.com ", SupportTicketCategory.ACCOUNT, "Không nhận được OTP", "Tôi không nhận được mã OTP quên mật khẩu.");
    }

    @Test
    void create_ShouldSaveTicketNotifyAdminsAndSendConfirmationEmail() {
        User admin = new User();
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        SupportDto.CreateRes res = service.create("1.2.3.4", req(), null);

        assertEquals("TKT-000123", res.code());
        verify(emailService).sendSupportTicketReceivedEmail(
                eq("user@mail.com"), eq("user"), eq("TKT-000123"), eq("Tài khoản & đăng nhập"), anyString(), anyString(), any());
        verify(notificationService).notify(eq(1L), eq("SUPPORT_TICKET"), anyString(), anyString(), eq("/admin/support"));
    }

    @Test
    void create_IpLimitExceeded_ShouldRejectAndNotSave() {
        when(valueOps.increment("support:ticket:ip:1.2.3.4")).thenReturn(4L);

        assertThrows(BusinessRuleViolationException.class, () -> service.create("1.2.3.4", req(), null));

        verify(ticketRepository, never()).save(any());
        verify(emailService, never()).sendSupportTicketReceivedEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void create_EmailLimitExceeded_ShouldRejectAndNotSendEmail() {
        when(valueOps.increment("support:ticket:email:user@mail.com")).thenReturn(6L);

        assertThrows(BusinessRuleViolationException.class, () -> service.create("1.2.3.4", req(), null));

        verify(emailService, never()).sendSupportTicketReceivedEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void create_TooManyFiles_ShouldRejectBeforeConsumingQuota() {
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[]{1}),
                new MockMultipartFile("files", "b.png", "image/png", new byte[]{1}),
                new MockMultipartFile("files", "c.png", "image/png", new byte[]{1}),
                new MockMultipartFile("files", "d.png", "image/png", new byte[]{1}));

        assertThrows(BusinessRuleViolationException.class, () -> service.create("1.2.3.4", req(), files));

        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void create_NonImageFile_ShouldRejectBeforeConsumingQuota() {
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                new MockMultipartFile("files", "x.png", "image/png", "not really an image, plain text".getBytes()));

        assertThrows(com.lms.common.exception.InvalidRequestException.class, () -> service.create("1.2.3.4", req(), files));

        verify(valueOps, never()).increment(anyString());
        verify(ticketRepository, never()).save(any());
    }

    private SupportTicket existingTicket() {
        SupportTicket t = new SupportTicket();
        ReflectionTestUtils.setField(t, "id", 7L);
        t.setEmail("user@mail.com");
        t.setCategory(SupportTicketCategory.ACCOUNT);
        t.setSubject("s");
        t.setMessage("m");
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(t));
        when(userRepository.findByEmail("admin@lms.local")).thenReturn(Optional.of(new User()));
        when(attachmentRepository.findByTicket_IdOrderByIdAsc(any())).thenReturn(List.of());
        return t;
    }

    @Test
    void update_ResolvedWithoutReply_ShouldReject() {
        existingTicket();

        assertThrows(BusinessRuleViolationException.class,
                () -> service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(SupportTicketStatus.RESOLVED, "  ", null)));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void update_WithReplyAndSteps_ShouldCleanStepsAndSendReplyEmail() {
        existingTicket();
        User admin = new User();
        admin.setFullName("Admin A");
        when(userRepository.findByEmail("admin@lms.local")).thenReturn(Optional.of(admin));

        SupportDto.AdminRes res = service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(
                SupportTicketStatus.RESOLVED, "Đã gửi link đặt lại mật khẩu.",
                java.util.Arrays.asList("  Mở email \n kiểm tra Spam ", "", null, "Bấm nút đặt lại")));

        assertEquals(List.of("Mở email kiểm tra Spam", "Bấm nút đặt lại"), res.adminSteps());
        verify(emailService).sendSupportTicketReplyEmail(
                eq("user@mail.com"), eq("user"), eq("TKT-000007"), eq("Tài khoản & đăng nhập"), eq("s"), eq("Đã xử lý"),
                eq("Đã gửi link đặt lại mật khẩu."), eq(List.of("Mở email kiểm tra Spam", "Bấm nút đặt lại")), eq("Admin A"), eq("m"));
    }

    @Test
    void update_StepsWithoutReply_ShouldReject() {
        existingTicket();

        assertThrows(BusinessRuleViolationException.class, () -> service.update("admin@lms.local", 7L,
                new SupportDto.UpdateReq(SupportTicketStatus.IN_PROGRESS, " ", List.of("Bước 1"))));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void update_StatusOnlyWithoutReply_ShouldNotSendEmail() {
        existingTicket();

        service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(SupportTicketStatus.IN_PROGRESS, null, null));

        verify(emailService, never()).sendSupportTicketReplyEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString());
    }
}
