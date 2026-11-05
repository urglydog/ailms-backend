package com.lms.support.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.Spy;
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

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

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

        assertTrue(res.code().matches("SUP-\\d{6}-ACC-00123"), res.code());
        verify(emailService).sendSupportTicketReceivedEmail(
                eq("user@mail.com"), eq("user"), eq(res.code()), eq("Tài khoản & đăng nhập"), anyString(), anyString(), any());
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
        t.setCode("SUP-251105-ACC-00007");
        t.setEmail("user@mail.com");
        t.setCategory(SupportTicketCategory.ACCOUNT);
        t.setSubject("Không nhận được OTP");
        t.setMessage("m");
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(t));
        User admin = new User();
        admin.setFullName("Admin A");
        when(userRepository.findByEmail("admin@lms.local")).thenReturn(Optional.of(admin));
        when(attachmentRepository.findByTicket_IdOrderByIdAsc(any())).thenReturn(List.of());
        return t;
    }

    private static SupportDto.Section text(String title, String text) {
        return new SupportDto.Section(title, SupportDto.SectionType.TEXT, text, null);
    }

    private static SupportDto.Section list(String title, String... items) {
        return new SupportDto.Section(title, SupportDto.SectionType.LIST, null, java.util.Arrays.asList(items));
    }

    private static SupportDto.EmailContent content(String greeting, String intro, SupportDto.Section... sections) {
        return new SupportDto.EmailContent(greeting, intro, List.of(sections), null, null, null, null);
    }

    @Test
    void update_ResolvedWithoutContent_ShouldReject() {
        existingTicket();

        assertThrows(BusinessRuleViolationException.class,
                () -> service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(SupportTicketStatus.RESOLVED, null)));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void update_ContentWithOnlyEmptySections_ShouldBeTreatedAsNoReply() {
        existingTicket();

        service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(SupportTicketStatus.IN_PROGRESS,
                content(null, null, text("Tiêu đề", "   "), list("Các bước", "", null))));

        verify(emailService, never()).sendSupportTicketReplyEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_CustomSectionTitles_ShouldFillPlaceholdersCleanListAndSendEmail() {
        existingTicket();

        SupportDto.AdminRes res = service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(
                SupportTicketStatus.RESOLVED,
                content("Chào {name},", null,
                        text("Kết quả kiểm tra", "Đã xử lý {code}."),
                        list("Bạn hãy làm theo", "  Mở email \n kiểm tra Spam ", "", "Bấm nút đặt lại"))));

        org.mockito.ArgumentCaptor<EmailService.SupportReplyContent> captor =
                org.mockito.ArgumentCaptor.forClass(EmailService.SupportReplyContent.class);
        verify(emailService).sendSupportTicketReplyEmail(
                eq("user@mail.com"), eq("SUP-251105-ACC-00007"), eq("Tài khoản & đăng nhập"), eq("Không nhận được OTP"),
                eq("Đã xử lý"), eq("m"), captor.capture());
        EmailService.SupportReplyContent sent = captor.getValue();
        assertEquals("Chào user,", sent.greeting());
        assertEquals("Về yêu cầu hỗ trợ SUP-251105-ACC-00007, chúng tôi xin phản hồi như sau.", sent.intro());
        assertEquals("Kết quả kiểm tra", sent.sections().get(0).title());
        assertEquals("Đã xử lý SUP-251105-ACC-00007.", sent.sections().get(0).text());
        assertEquals("Bạn hãy làm theo", sent.sections().get(1).title());
        assertEquals(List.of("Mở email kiểm tra Spam", "Bấm nút đặt lại"), sent.sections().get(1).items());
        assertEquals("Admin A", sent.signatureName());
        assertEquals("Trân trọng,", sent.closing());
        assertEquals("Đội ngũ hỗ trợ LinguaLearn", sent.signatureTeam());

        // nội dung gốc (chưa thay placeholder) được lưu để Admin mở lại chỉnh sửa
        assertEquals("Chào {name},", res.adminContent().greeting());
        assertTrue(res.adminReply().contains("Kết quả kiểm tra:\nĐã xử lý SUP-251105-ACC-00007."));
    }

    @Test
    void update_IncludeOriginalFalse_ShouldOmitOriginalMessage() {
        existingTicket();
        SupportDto.EmailContent c = new SupportDto.EmailContent(null, null, List.of(text("A", "B")), null, null, null, false);

        service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(SupportTicketStatus.IN_PROGRESS, c));

        verify(emailService).sendSupportTicketReplyEmail(any(), any(), any(), any(), any(), isNull(), any());
    }

    @Test
    void update_StatusOnlyWithoutContent_ShouldNotSendEmail() {
        existingTicket();

        service.update("admin@lms.local", 7L, new SupportDto.UpdateReq(SupportTicketStatus.IN_PROGRESS, null));

        verify(emailService, never()).sendSupportTicketReplyEmail(any(), any(), any(), any(), any(), any(), any());
    }
}
