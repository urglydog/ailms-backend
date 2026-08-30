package com.lms.chat.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorDto.AskReq;
import com.lms.chat.dto.TutorDto.AskRes;
import com.lms.chat.dto.TutorDto.AttachmentReq;
import com.lms.chat.entity.ChatMessage;
import com.lms.chat.entity.ChatMessageAttachment;
import com.lms.chat.entity.ChatSession;
import com.lms.chat.repository.ChatMessageAttachmentRepository;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.chat.repository.ChatSessionRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC30 mở rộng — nhớ hội thoại, lịch sử trò chuyện kiểu ChatGPT (ghim/đổi tên/xoá/tự đặt tên
 * bằng AI), và sửa đúng lỗi thực tế: sắp xếp lịch sử phải theo tin nhắn GẦN NHẤT của phiên
 * (không phải lúc TẠO phiên) — 1 phiên cũ vừa nhắn tiếp phải nhảy lên đầu, giống ChatGPT/Gemini.
 */
@ExtendWith(MockitoExtension.class)
class TutorServiceTest {

    private static final String EMAIL = "student@lms.local";
    private static final Long LESSON_ID = 21L;

    @Mock private UserRepository userRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private EnrollmentSecurity enrollmentSecurity;
    @Mock private TutorQuotaService tutorQuotaService;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatMessageAttachmentRepository chatMessageAttachmentRepository;
    @Mock private StorageService storageService;
    @Mock private org.springframework.web.client.RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;

    @InjectMocks
    private TutorService tutorService;

    private User user;
    private Lesson lesson;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        lesson = new Lesson();
        lesson.setId(LESSON_ID);

        session = new ChatSession();
        session.setId(500L);
        session.setUser(user);
        session.setLesson(lesson);

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(enrollmentSecurity.canAccessLesson(EMAIL, LESSON_ID, false)).thenReturn(true);
        // Chi ask()/startNewSession() can lesson va tim-phien-gan-nhat — cac test khac khong
        // dung toi, danh dau lenient de khong bi Mockito strict-stubbing bao "unnecessary".
        lenient().when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson));
        lenient().when(chatSessionRepository.findFirstByUser_IdAndLesson_IdOrderByCreatedAtDesc(1L, LESSON_ID))
                .thenReturn(Optional.of(session));
        lenient().when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai-api:8000");
        // Luot hoi DAU TIEN cua 1 phien (history rong) se kich hoat goi them AI Worker tu dat
        // ten (xem trySetAiGeneratedTitle) — mac dinh cho tra ve null (nhu AI Worker khong ket
        // noi duoc) de cac test KHONG lien quan tinh nang dat ten khong bi anh huong.
        lenient().when(restTemplate.postForObject(contains("/tutor/title"), any(), any())).thenReturn(null);
        lenient().when(chatMessageAttachmentRepository.findByChatMessage_IdIn(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(tutorService, "maxAttachmentsPerTurn", 3);
        ReflectionTestUtils.setField(tutorService, "maxAttachmentSizeMb", 8L);
    }

    private ChatMessage message(String sender, String content) {
        ChatMessage m = new ChatMessage();
        m.setChatSession(session);
        m.setSender(sender);
        m.setContent(content);
        return m;
    }

    @SuppressWarnings("unchecked")
    @Test
    void ask_sendsHistoryToAiWorkerInChronologicalOrder() {
        // Repository tra ve DESC (moi nhat truoc) - dung dieu nay de xac nhan service TU DAO
        // lai dung thu tu hoi thoai that (cu -> moi) truoc khi gui di.
        ChatMessage older = message("USER", "Unity AI Assistant co free khong?");
        ChatMessage newer = message("AI", "Video khong de cap, minh da tim tren mang: co ban mien phi.");
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(List.of(newer, older)); // DESC: moi nhat truoc, KHONG rong -> khong phai luot dau

        Object fakeAskResponse = newFakeAiWorkerAskRes("Cau hoi truoc la ve Unity AI Assistant.", List.of(65), 42);
        when(restTemplate.postForObject(contains("/tutor/ask"), any(), any())).thenReturn(fakeAskResponse);

        AskRes result = tutorService.ask(EMAIL, LESSON_ID, new AskReq("Cau hoi tren la gi?", null, List.of()));

        assertThat(result.sessionId()).isEqualTo(500L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForObject(contains("/tutor/ask"), payloadCaptor.capture(), any());
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();

        List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history");
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("sender", "USER").containsEntry("content", "Unity AI Assistant co free khong?");
        assertThat(history.get(1)).containsEntry("sender", "AI").containsEntry("content", "Video khong de cap, minh da tim tren mang: co ban mien phi.");
        assertThat(payload.get("question")).isEqualTo("Cau hoi tren la gi?");
        // Khong phai luot dau (da co lich su) -> KHONG duoc goi AI Worker de dat ten.
        verify(restTemplate, never()).postForObject(contains("/tutor/title"), any(), any());
    }

    @Test
    void ask_fetchesHistoryBeforeSavingCurrentTurn_soCurrentQuestionNeverLeaksIntoOwnHistory() {
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(List.of());
        Object fakeAskResponse = newFakeAiWorkerAskRes("Tra loi.", List.of(), 10);
        when(restTemplate.postForObject(contains("/tutor/ask"), any(), any())).thenReturn(fakeAskResponse);

        tutorService.ask(EMAIL, LESSON_ID, new AskReq("Cau hoi dau tien", null, List.of()));

        // findByChatSession_...Desc phai duoc goi TRUOC khi save() tin nhan cua luot nay —
        // xac nhan gian tiep qua viec history rong (chua co gi de lay) du session da ton tai.
        verify(chatMessageRepository).findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class));
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void ask_firstTurnOfNewSession_triggersAiGeneratedTitle() {
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(List.of()); // rong -> day la luot hoi DAU TIEN
        when(restTemplate.postForObject(contains("/tutor/ask"), any(), any()))
                .thenReturn(newFakeAiWorkerAskRes("Tra loi dau tien.", List.of(), 10));
        when(restTemplate.postForObject(contains("/tutor/title"), any(), any()))
                .thenReturn(newFakeAiWorkerTitleRes("Hoi ve Unity AI Assistant"));

        tutorService.ask(EMAIL, LESSON_ID, new AskReq("Unity AI Assistant co free khong?", null, List.of()));

        assertThat(session.getTitle()).isEqualTo("Hoi ve Unity AI Assistant");
    }

    @Test
    void ask_aiWorkerTitleCallFails_doesNotBreakTheMainAnswer() {
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(List.of());
        when(restTemplate.postForObject(contains("/tutor/ask"), any(), any()))
                .thenReturn(newFakeAiWorkerAskRes("Tra loi dau tien.", List.of(), 10));
        when(restTemplate.postForObject(contains("/tutor/title"), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

        AskRes result = tutorService.ask(EMAIL, LESSON_ID, new AskReq("Cau hoi", null, List.of()));

        assertThat(result.answer()).isEqualTo("Tra loi dau tien.");
        assertThat(session.getTitle()).isNull(); // roi ve rut gon cau hoi dau khi liet ke sau nay
    }

    // ── UC30 mở rộng — đính kèm tệp (ảnh/văn bản/PDF) ──────────────────

    /** Chữ ký byte thật của PNG — Tika chỉ cần phần đầu này để nhận diện đúng "image/png". */
    private static final byte[] PNG_MAGIC_BYTES =
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void ask_withValidImageAttachment_uploadsToB2AndLinksAttachmentToUserMessage() {
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(List.of());
        when(restTemplate.postForObject(contains("/tutor/ask"), any(), any()))
                .thenReturn(newFakeAiWorkerAskRes("Day la mo ta anh.", List.of(), 20));
        when(storageService.upload(anyString(), any(), anyLong(), eq("image/png")))
                .thenReturn("https://b2.example.com/tutor-attachments/500/fake.png");

        String base64Png = Base64.getEncoder().encodeToString(PNG_MAGIC_BYTES);
        AskReq req = new AskReq("Anh nay ve gi?", null, List.of(new AttachmentReq("screenshot.png", base64Png)));

        tutorService.ask(EMAIL, LESSON_ID, req);

        ArgumentCaptor<ChatMessageAttachment> attCaptor = ArgumentCaptor.forClass(ChatMessageAttachment.class);
        verify(chatMessageAttachmentRepository).save(attCaptor.capture());
        assertThat(attCaptor.getValue().getFileName()).isEqualTo("screenshot.png");
        assertThat(attCaptor.getValue().getFileUrl()).isEqualTo("https://b2.example.com/tutor-attachments/500/fake.png");
        assertThat(attCaptor.getValue().getMimeType()).isEqualTo("image/png");
        // Dinh kem luon gan vao tin nhan HOC VIEN, khong bao gio gan vao tin AI.
        assertThat(attCaptor.getValue().getChatMessage().getSender()).isEqualTo("USER");
    }

    @SuppressWarnings("unchecked")
    @Test
    void ask_forwardsDetectedMimeTypeAndBase64ToAiWorker() {
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(List.of());
        when(restTemplate.postForObject(contains("/tutor/ask"), any(), any()))
                .thenReturn(newFakeAiWorkerAskRes("OK.", List.of(), 5));
        when(storageService.upload(anyString(), any(), anyLong(), eq("image/png")))
                .thenReturn("https://b2.example.com/x.png");
        String base64Png = Base64.getEncoder().encodeToString(PNG_MAGIC_BYTES);

        tutorService.ask(EMAIL, LESSON_ID, new AskReq("Anh nay?", null, List.of(new AttachmentReq("x.png", base64Png))));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForObject(contains("/tutor/ask"), payloadCaptor.capture(), any());
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        List<Map<String, String>> attachments = (List<Map<String, String>>) payload.get("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0)).containsEntry("mime_type", "image/png").containsEntry("data_base64", base64Png);
    }

    @Test
    void ask_withTooManyAttachments_throwsWithoutCallingAiWorker() {
        List<AttachmentReq> fourFiles = List.of(
                new AttachmentReq("a.txt", Base64.getEncoder().encodeToString("hello".getBytes())),
                new AttachmentReq("b.txt", Base64.getEncoder().encodeToString("hello".getBytes())),
                new AttachmentReq("c.txt", Base64.getEncoder().encodeToString("hello".getBytes())),
                new AttachmentReq("d.txt", Base64.getEncoder().encodeToString("hello".getBytes())));

        assertThatThrownBy(() -> tutorService.ask(EMAIL, LESSON_ID, new AskReq("Xem giup", null, fourFiles)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(restTemplate, never()).postForObject(contains("/tutor/ask"), any(), any());
    }

    @Test
    void ask_withOversizedAttachment_throws() {
        ReflectionTestUtils.setField(tutorService, "maxAttachmentSizeMb", 0L); // ep gioi han ve 0 de de test
        AttachmentReq big = new AttachmentReq("big.txt", Base64.getEncoder().encodeToString("some bytes".getBytes()));

        assertThatThrownBy(() -> tutorService.ask(EMAIL, LESSON_ID, new AskReq("Xem giup", null, List.of(big))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void ask_withUnsupportedFileType_throwsInvalidRequest() {
        byte[] zipMagic = new byte[] {0x50, 0x4B, 0x03, 0x04};
        AttachmentReq zip = new AttachmentReq("data.zip", Base64.getEncoder().encodeToString(zipMagic));

        assertThatThrownBy(() -> tutorService.ask(EMAIL, LESSON_ID, new AskReq("Xem giup", null, List.of(zip))))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ── UC30 mở rộng — lịch sử trò chuyện kiểu ChatGPT ─────────────────

    @Test
    void listSessions_sortsByLastMessageTime_notSessionCreationTime() {
        // Mo phong dung tinh huong nguoi dung bao loi: session "cu" (tao truoc) vua duoc nhan
        // tin MOI hon session "moi" (tao sau nhung khong ai nhan gi them) -> session cu phai
        // nhay len DAU danh sach.
        ChatSession olderSession = new ChatSession();
        olderSession.setId(501L);
        olderSession.setUser(user);
        olderSession.setLesson(lesson);
        when(chatSessionRepository.findByUser_IdAndLesson_IdOrderByCreatedAtDesc(1L, LESSON_ID))
                .thenReturn(List.of(session, olderSession)); // session(500) "moi tao" dung truoc theo createdAt

        ChatMessage veryRecentMsg = message("AI", "Vua tra loi xong");
        veryRecentMsg.setCreatedAt(LocalDateTime.now());
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(500L)).thenReturn(List.of());
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(501L))
                .thenReturn(List.of(message("USER", "Cau hoi cu"), veryRecentMsg));

        var result = tutorService.listSessions(EMAIL, LESSON_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(501L); // vua nhan tin -> len dau, du tao truoc
        assertThat(result.get(1).id()).isEqualTo(500L);
    }

    @Test
    void listSessions_pinnedSessionsAlwaysComeFirst() {
        ChatSession pinnedButOlderActivity = new ChatSession();
        pinnedButOlderActivity.setId(501L);
        pinnedButOlderActivity.setUser(user);
        pinnedButOlderActivity.setLesson(lesson);
        pinnedButOlderActivity.setIsPinned(true);
        pinnedButOlderActivity.setPinnedAt(LocalDateTime.now().minusDays(1));

        when(chatSessionRepository.findByUser_IdAndLesson_IdOrderByCreatedAtDesc(1L, LESSON_ID))
                .thenReturn(List.of(session, pinnedButOlderActivity));
        ChatMessage veryRecentMsg = message("AI", "Vua tra loi xong");
        veryRecentMsg.setCreatedAt(LocalDateTime.now());
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(500L)).thenReturn(List.of(veryRecentMsg));
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(501L)).thenReturn(List.of());

        var result = tutorService.listSessions(EMAIL, LESSON_ID);

        // 500 vua nhan tin (moi hon) nhung 501 DA GHIM -> 501 van phai dung truoc.
        assertThat(result.get(0).id()).isEqualTo(501L);
        assertThat(result.get(0).isPinned()).isTrue();
        assertThat(result.get(1).id()).isEqualTo(500L);
    }

    @Test
    void getMessages_returnsChronologicalMessagesWithParsedTimestamps() {
        ChatMessage userMsg = message("USER", "Cau hoi");
        ChatMessage aiMsg = message("AI", "Tra loi [01:05]");
        aiMsg.setCitedTimestamps("[65]");
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(500L)).thenReturn(List.of(userMsg, aiMsg));

        var result = tutorService.getMessages(EMAIL, LESSON_ID, 500L);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).citedTimestamps()).containsExactly(65);
    }

    @Test
    void getMessages_throwsWhenSessionBelongsToAnotherUser() {
        User otherUser = new User();
        otherUser.setId(999L);
        ChatSession othersSession = new ChatSession();
        othersSession.setId(777L);
        othersSession.setUser(otherUser);
        othersSession.setLesson(lesson);
        when(chatSessionRepository.findById(777L)).thenReturn(Optional.of(othersSession));

        assertThatThrownBy(() -> tutorService.getMessages(EMAIL, LESSON_ID, 777L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void startNewSession_createsFreshEmptySession() {
        ChatSession fresh = new ChatSession();
        fresh.setId(600L);
        fresh.setUser(user);
        fresh.setLesson(lesson);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(fresh);

        var result = tutorService.startNewSession(EMAIL, LESSON_ID);

        assertThat(result.id()).isEqualTo(600L);
        assertThat(result.title()).isEqualTo("Cuộc trò chuyện mới");
        // KHONG duoc tim/tai su dung phien gan nhat — day la diem khac biet voi ask().
        verify(chatSessionRepository, never()).findFirstByUser_IdAndLesson_IdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void renameSession_overwritesTitle() {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(session));

        tutorService.renameSession(EMAIL, LESSON_ID, 500L, "  Ten moi  ");

        assertThat(session.getTitle()).isEqualTo("Ten moi");
    }

    @Test
    void pinSession_setsPinnedAtTimestamp() {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(session));
        when(chatSessionRepository.findByUser_IdAndLesson_IdOrderByCreatedAtDesc(1L, LESSON_ID)).thenReturn(List.of(session));

        tutorService.pinSession(EMAIL, LESSON_ID, 500L, true);

        assertThat(session.getIsPinned()).isTrue();
        assertThat(session.getPinnedAt()).isNotNull();
    }

    @Test
    void pinSession_throwsWhenAlreadyAtMaxPinnedCap() {
        List<ChatSession> fivePinned = java.util.stream.IntStream.range(0, 5).mapToObj(i -> {
            ChatSession s = new ChatSession();
            s.setId(700L + i);
            s.setUser(user);
            s.setLesson(lesson);
            s.setIsPinned(true);
            return s;
        }).collect(java.util.stream.Collectors.toList());
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(session)); // session (500) chua ghim
        when(chatSessionRepository.findByUser_IdAndLesson_IdOrderByCreatedAtDesc(1L, LESSON_ID)).thenReturn(fivePinned);

        assertThatThrownBy(() -> tutorService.pinSession(EMAIL, LESSON_ID, 500L, true))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void pinSession_unpinningNeverBlockedByCap() {
        session.setIsPinned(true);
        session.setPinnedAt(LocalDateTime.now());
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(session));

        tutorService.pinSession(EMAIL, LESSON_ID, 500L, false);

        assertThat(session.getIsPinned()).isFalse();
        assertThat(session.getPinnedAt()).isNull();
        verify(chatSessionRepository, never()).findByUser_IdAndLesson_IdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void deleteSession_deletesMessagesBeforeSession() {
        ChatMessage m1 = message("USER", "a");
        ChatMessage m2 = message("AI", "b");
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(500L)).thenReturn(List.of(m1, m2));

        tutorService.deleteSession(EMAIL, LESSON_ID, 500L);

        var inOrder = org.mockito.Mockito.inOrder(chatMessageRepository, chatSessionRepository);
        inOrder.verify(chatMessageRepository).deleteAll(List.of(m1, m2));
        inOrder.verify(chatSessionRepository).delete(session);
    }

    /** {@code TutorService.AiWorkerAskRes}/{@code AiWorkerTitleRes} là record private lồng
     * trong class — dùng reflection dựng instance thay vì đổi visibility chỉ để phục vụ test. */
    private Object newFakeAiWorkerAskRes(String answer, List<Integer> citedTimestamps, Integer tokenUsed) {
        try {
            Class<?> recordClass = Class.forName("com.lms.chat.service.TutorService$AiWorkerAskRes");
            var ctor = recordClass.getDeclaredConstructor(String.class, List.class, Integer.class);
            ctor.setAccessible(true);
            return ctor.newInstance(answer, citedTimestamps, tokenUsed);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Object newFakeAiWorkerTitleRes(String title) {
        try {
            Class<?> recordClass = Class.forName("com.lms.chat.service.TutorService$AiWorkerTitleRes");
            var ctor = recordClass.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(title);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
