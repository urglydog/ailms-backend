package com.lms.chat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorDto.AskReq;
import com.lms.chat.dto.TutorDto.AskRes;
import com.lms.chat.dto.TutorDto.AttachmentReq;
import com.lms.chat.dto.TutorDto.AttachmentRes;
import com.lms.chat.dto.TutorDto.MessageRes;
import com.lms.chat.dto.TutorDto.SessionRes;
import com.lms.chat.entity.ChatMessage;
import com.lms.chat.entity.ChatMessageAttachment;
import com.lms.chat.entity.ChatSession;
import com.lms.chat.repository.ChatMessageAttachmentRepository;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.chat.repository.ChatSessionRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ExternalServiceException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * UC30 — Socratic AI Tutor. "HTTP đồng bộ" đúng nghĩa: gọi thẳng AI Worker (giống
 * {@link com.lms.chat.controller.DiscoveryController}), KHÔNG qua Redis/Celery như
 * pipeline lồng tiếng — vì Tutor cần trả lời ngay trong 1 request, không phải tác vụ
 * chạy nền dài hơi.
 *
 * <p>{@code be/} là nơi DUY NHẤT ghi {@link ChatMessage} — AI Worker chỉ tính toán câu
 * trả lời rồi trả về, không tự ghi MySQL (đúng nguyên tắc AI Worker không kết nối MySQL
 * trực tiếp).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorService {

    /** UC30 mở rộng — số tin nhắn GẦN NHẤT (cả USER lẫn AI) gửi kèm làm lịch sử hội thoại cho
     * Gemini, để trả lời được các câu hỏi nối tiếp kiểu "câu hỏi trên là gì?". Giới hạn để
     * không phình payload/tốn token vô hạn khi 1 phiên chat kéo dài rất nhiều lượt. */
    private static final int HISTORY_LIMIT = 10;

    /** UC30 mở rộng — trần số cuộc trò chuyện được ghim cùng lúc, tránh mục "đã ghim" phình to
     * mất hết ý nghĩa "nổi bật lên trên". */
    private static final int MAX_PINNED_SESSIONS = 5;

    /** UC30 mở rộng — phạm vi tệp đính kèm hỗ trợ: ảnh (mọi định dạng trình duyệt encode được),
     * văn bản/mã nguồn thô (Tika không phân biệt được .py/.js/.java... với .txt vì không có
     * magic number riêng cho mã nguồn — coi chung là text/plain, vẫn đúng ý "gửi code" của yêu
     * cầu), và PDF. KHÔNG gồm audio/video — Gemini xử lý được nhưng cần Files API (khác hẳn
     * inlineData base64 đang dùng ở đây) và giới hạn dung lượng lớn hơn nhiều, để dành mở rộng
     * sau nếu cần, tránh làm phình phạm vi tính năng này quá mức cần thiết.
     */
    private static final java.util.Set<String> ALLOWED_ATTACHMENT_EXACT_MIME = java.util.Set.of("text/plain", "application/pdf");

    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentSecurity enrollmentSecurity;
    private final TutorQuotaService tutorQuotaService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageAttachmentRepository chatMessageAttachmentRepository;
    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;
    private final Tika tika = new Tika();

    @Value("${lms.rules.max-tutor-attachments-per-turn}")
    private int maxAttachmentsPerTurn;

    @Value("${lms.rules.max-tutor-attachment-size-mb}")
    private long maxAttachmentSizeMb;

    @Transactional
    public AskRes ask(String email, Long lessonId, AskReq req) {
        User user = requireAccess(email, lessonId);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));

        // BR-TUTOR-04
        tutorQuotaService.consume(user.getId());

        ChatSession session = req.sessionId() != null
                // Kiem tra so huu — hoc vien A khong duoc gui sessionId cua hoc vien B de
                // "chen" cau hoi vao phien chat nguoi khac.
                ? loadOwnedSession(req.sessionId(), user.getId(), lessonId)
                : chatSessionRepository.findFirstByUser_IdAndLesson_IdOrderByCreatedAtDesc(user.getId(), lessonId)
                        .orElseGet(() -> {
                            ChatSession s = new ChatSession();
                            s.setUser(user);
                            s.setLesson(lesson);
                            return chatSessionRepository.save(s);
                        });

        // UC30 mở rộng — LẤY lịch sử TRƯỚC khi lưu tin của lượt hỏi này, không thì câu hỏi
        // hiện tại sẽ tự lẫn vào "lịch sử" gửi cho chính nó. Đồng thời dùng NGAY kết quả này
        // để biết đây có phải lượt hỏi ĐẦU TIÊN của phiên hay không (quyết định có tự đặt tên
        // bằng AI hay không, xem cuối hàm).
        List<ChatMessage> recentDesc = chatMessageRepository
                .findByChatSession_IdOrderByCreatedAtDesc(session.getId(), PageRequest.of(0, HISTORY_LIMIT));
        boolean isFirstTurn = recentDesc.isEmpty();
        List<ChatMessage> history = new ArrayList<>(recentDesc);
        Collections.reverse(history); // cu -> moi, dung thu tu hoi thoai that

        List<UploadedAttachment> uploaded = processAttachments(session.getId(), req.attachments());
        AiWorkerAskRes aiRes = callAiWorker(lessonId, req.question(), history, uploaded);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setChatSession(session);
        userMsg.setSender("USER");
        userMsg.setContent(req.question());
        chatMessageRepository.save(userMsg);

        // Dinh kem luon thuoc ve tin nhan HOC VIEN vua gui, khong bao gio gan vao tin AI.
        for (UploadedAttachment att : uploaded) {
            ChatMessageAttachment entity = new ChatMessageAttachment();
            entity.setChatMessage(userMsg);
            entity.setFileName(att.fileName());
            entity.setFileUrl(att.fileUrl());
            entity.setMimeType(att.mimeType());
            entity.setFileSize(att.fileSize());
            chatMessageAttachmentRepository.save(entity);
        }

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setChatSession(session);
        aiMsg.setSender("AI");
        aiMsg.setContent(aiRes.answer());
        aiMsg.setCitedTimestamps(toJsonArray(aiRes.citedTimestamps()));
        aiMsg.setTokenUsed(aiRes.tokenUsed());
        chatMessageRepository.save(aiMsg);

        // UC30 mở rộng — chỉ tự đặt tên đúng 1 LẦN, ngay sau lượt hỏi ĐẦU TIÊN của 1 phiên MỚI
        // (giống ChatGPT/Gemini) — không đặt lại ở các lượt sau, và tuyệt đối không ghi đè tên
        // học viên đã tự đổi thủ công.
        if (isFirstTurn && session.getTitle() == null) {
            trySetAiGeneratedTitle(session, req.question(), aiRes.answer());
        }

        return new AskRes(session.getId(), aiRes.answer(), aiRes.citedTimestamps(), aiRes.tokenUsed());
    }

    /**
     * UC30 mở rộng — danh sách "lịch sử trò chuyện" kiểu ChatGPT cho (học viên, bài học) này:
     * mục ĐÃ GHIM lên trước (mới ghim nhất trước), rồi tới mục còn lại sắp theo tin nhắn GẦN
     * NHẤT (không phải lúc TẠO phiên — sửa đúng lỗi thực tế: 1 phiên cũ vừa nhắn tiếp vẫn phải
     * nhảy lên đầu danh sách "chưa ghim", giống hành vi ChatGPT/Gemini).
     */
    @Transactional(readOnly = true)
    public List<SessionRes> listSessions(String email, Long lessonId) {
        User user = requireAccess(email, lessonId);
        List<ChatSession> sessions = chatSessionRepository.findByUser_IdAndLesson_IdOrderByCreatedAtDesc(user.getId(), lessonId);

        record Summary(ChatSession session, String title, LocalDateTime lastActivityAt) {}
        List<Summary> summaries = sessions.stream()
                .map(s -> {
                    List<ChatMessage> messages = chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(s.getId());
                    LocalDateTime lastActivityAt = messages.isEmpty()
                            ? s.getCreatedAt()
                            : messages.get(messages.size() - 1).getCreatedAt();
                    return new Summary(s, resolveTitle(s, messages), lastActivityAt);
                })
                .toList();

        // LUU Y: nullsLast() phai boc NGOAI reverseOrder(), khong duoc goi .reversed() sau cung
        // — .reversed() lat ca comparator (ke ca phan nullsLast), khien null bi day len DAU thay
        // vi cuoi. reverseOrder() ben trong moi la thu tu GIAM DAN (moi nhat truoc) that su.
        List<SessionRes> pinned = summaries.stream()
                .filter(sm -> Boolean.TRUE.equals(sm.session().getIsPinned()))
                .sorted(Comparator.comparing((Summary sm) -> sm.session().getPinnedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(sm -> new SessionRes(sm.session().getId(), sm.title(), sm.lastActivityAt(), true))
                .toList();
        List<SessionRes> unpinned = summaries.stream()
                .filter(sm -> !Boolean.TRUE.equals(sm.session().getIsPinned()))
                .sorted(Comparator.comparing(Summary::lastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(sm -> new SessionRes(sm.session().getId(), sm.title(), sm.lastActivityAt(), false))
                .toList();

        List<SessionRes> result = new ArrayList<>(pinned);
        result.addAll(unpinned);
        return result;
    }

    /** UC30 mở rộng — phục hồi lại TOÀN BỘ tin của 1 phiên cũ, cũ -> mới (đúng thứ tự hội thoại
     * thật) khi học viên bấm mở lại 1 cuộc trò chuyện trong danh sách lịch sử. */
    @Transactional(readOnly = true)
    public List<MessageRes> getMessages(String email, Long lessonId, Long sessionId) {
        User user = requireAccess(email, lessonId);
        ChatSession session = loadOwnedSession(sessionId, user.getId(), lessonId);
        List<ChatMessage> messages = chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(session.getId());

        // 1 truy van cho CA đoạn hội thoại thay vì 1 truy van/tin nhan (tranh N+1).
        Map<Long, List<AttachmentRes>> attachmentsByMessageId = chatMessageAttachmentRepository
                .findByChatMessage_IdIn(messages.stream().map(ChatMessage::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> a.getChatMessage().getId(),
                        java.util.stream.Collectors.mapping(
                                a -> new AttachmentRes(a.getId(), a.getFileName(), a.getFileUrl(), a.getMimeType()),
                                java.util.stream.Collectors.toList())));

        return messages.stream()
                .map(m -> new MessageRes(
                        m.getId(), m.getSender(), m.getContent(), fromJsonArray(m.getCitedTimestamps()),
                        attachmentsByMessageId.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    /**
     * UC30 mở rộng — "Cuộc trò chuyện mới": tạo hẳn 1 {@link ChatSession} RỖNG mới thay vì tái
     * sử dụng phiên gần nhất như {@link #ask} vẫn làm khi không truyền `sessionId`. FE nhận
     * `id` trả về rồi truyền TƯỜNG MINH làm `sessionId` cho lượt hỏi tiếp theo — đi thẳng vào
     * nhánh `loadOwnedSession` của {@code ask()}, không rơi vào nhánh "tái sử dụng gần nhất" nữa.
     */
    @Transactional
    public SessionRes startNewSession(String email, Long lessonId) {
        User user = requireAccess(email, lessonId);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setLesson(lesson);
        ChatSession saved = chatSessionRepository.save(session);
        return new SessionRes(saved.getId(), "Cuộc trò chuyện mới", saved.getCreatedAt(), false);
    }

    /** UC30 mở rộng — đổi tên thủ công, ghi đè VĨNH VIỄN — {@code ask()} không bao giờ tự đặt
     * lại tên AI cho phiên đã có {@code title} khác null. */
    @Transactional
    public void renameSession(String email, Long lessonId, Long sessionId, String newTitle) {
        User user = requireAccess(email, lessonId);
        ChatSession session = loadOwnedSession(sessionId, user.getId(), lessonId);
        session.setTitle(newTitle.trim());
        chatSessionRepository.save(session);
    }

    /** UC30 mở rộng — ghim/bỏ ghim; tối đa {@link #MAX_PINNED_SESSIONS} mục ghim cùng lúc để
     * mục "đã ghim" không phình to mất tác dụng nổi bật. */
    @Transactional
    public void pinSession(String email, Long lessonId, Long sessionId, boolean pinned) {
        User user = requireAccess(email, lessonId);
        ChatSession session = loadOwnedSession(sessionId, user.getId(), lessonId);
        if (pinned) {
            long currentlyPinned = chatSessionRepository.findByUser_IdAndLesson_IdOrderByCreatedAtDesc(user.getId(), lessonId)
                    .stream().filter(s -> Boolean.TRUE.equals(s.getIsPinned())).count();
            if (currentlyPinned >= MAX_PINNED_SESSIONS && !Boolean.TRUE.equals(session.getIsPinned())) {
                throw new BusinessRuleViolationException(
                        "Đã ghim tối đa " + MAX_PINNED_SESSIONS + " cuộc trò chuyện, bỏ ghim bớt trước khi ghim thêm");
            }
            session.setIsPinned(true);
            session.setPinnedAt(LocalDateTime.now());
        } else {
            session.setIsPinned(false);
            session.setPinnedAt(null);
        }
        chatSessionRepository.save(session);
    }

    /** UC30 mở rộng — xoá hẳn 1 cuộc trò chuyện. Phải xoá {@link ChatMessage} con trước vì FK
     * {@code fk_chat_messages_chat_session_id} không có {@code ON DELETE CASCADE}. */
    @Transactional
    public void deleteSession(String email, Long lessonId, Long sessionId) {
        User user = requireAccess(email, lessonId);
        ChatSession session = loadOwnedSession(sessionId, user.getId(), lessonId);
        chatMessageRepository.deleteAll(chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(session.getId()));
        chatSessionRepository.delete(session);
    }

    /**
     * UC30 mở rộng — kiểm định + tải tệp đính kèm lên B2 TRƯỚC khi gọi AI Worker (cần
     * {@code fileUrl} thật để lưu {@link ChatMessageAttachment} ngay sau khi có phản hồi).
     * MIME type luôn dò lại bằng magic number (Tika) — KHÔNG tin giá trị client khai qua tên
     * tệp/đuôi tệp, giống hệt nguyên tắc {@code LessonDocumentService} đang dùng cho tài liệu
     * bài học.
     */
    private List<UploadedAttachment> processAttachments(Long sessionId, List<AttachmentReq> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return List.of();
        }
        if (reqs.size() > maxAttachmentsPerTurn) {
            throw new BusinessRuleViolationException("Tối đa " + maxAttachmentsPerTurn + " tệp đính kèm mỗi lượt hỏi");
        }
        long maxBytes = maxAttachmentSizeMb * 1024 * 1024;

        List<UploadedAttachment> result = new ArrayList<>();
        for (AttachmentReq req : reqs) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(req.dataBase64());
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Tệp \"" + req.fileName() + "\" không đúng định dạng base64");
            }
            if (bytes.length == 0) {
                throw new InvalidRequestException("Tệp \"" + req.fileName() + "\" trống");
            }
            if (bytes.length > maxBytes) {
                throw new BusinessRuleViolationException(
                        "Tệp \"" + req.fileName() + "\" vượt quá " + maxAttachmentSizeMb + "MB");
            }
            String detectedMime = tika.detect(bytes, req.fileName());
            if (!(detectedMime.startsWith("image/") || ALLOWED_ATTACHMENT_EXACT_MIME.contains(detectedMime))) {
                throw new InvalidRequestException(
                        "Định dạng tệp \"" + req.fileName() + "\" không được hỗ trợ (chỉ nhận ảnh/văn bản/mã nguồn/PDF)");
            }

            String sanitized = req.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
            String key = "tutor-attachments/" + sessionId + "/" + UUID.randomUUID() + "-" + sanitized;
            String url = storageService.upload(key, new java.io.ByteArrayInputStream(bytes), bytes.length, detectedMime);

            result.add(new UploadedAttachment(req.fileName(), url, detectedMime, (long) bytes.length, req.dataBase64()));
        }
        return result;
    }

    /** Kết quả 1 tệp đã kiểm định + tải lên B2 — `dataBase64` giữ lại để gửi cho AI Worker phân
     * tích, KHÔNG lưu xuống DB (chỉ `fileUrl` mới được lưu, xem {@link ChatMessageAttachment}). */
    private record UploadedAttachment(String fileName, String fileUrl, String mimeType, long fileSize, String dataBase64) {}

    /** UC30 actor chỉ có Student, và chỉ hỏi được nội dung bài học ĐÃ sở hữu — không cho phép
     * ở bài Preview (giống LessonProgressService: Preview không có quyền đầy đủ, BR-ENROLL-02). */
    private User requireAccess(String email, Long lessonId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        if (!enrollmentSecurity.canAccessLesson(email, lessonId, false)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }
        return user;
    }

    /** Chặn học viên A xem/hỏi tiếp vào phiên chat của học viên B chỉ bằng cách đoán `sessionId`. */
    private ChatSession loadOwnedSession(Long sessionId, Long userId, Long lessonId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", sessionId));
        if (!session.getUser().getId().equals(userId) || !session.getLesson().getId().equals(lessonId)) {
            throw new AccessDeniedDomainException("Phiên trò chuyện này không thuộc về bạn");
        }
        return session;
    }

    private static String resolveTitle(ChatSession session, List<ChatMessage> messages) {
        if (session.getTitle() != null) {
            return session.getTitle();
        }
        return messages.stream()
                .filter(m -> "USER".equals(m.getSender()))
                .findFirst()
                .map(ChatMessage::getContent)
                .map(c -> c.length() > 60 ? c.substring(0, 60) + "…" : c)
                .orElse("Cuộc trò chuyện mới");
    }

    /**
     * UC30 mở rộng — gọi AI Worker sinh tiêu đề ngắn (giống ChatGPT/Gemini tự đặt tên cuộc trò
     * chuyện). Lỗi ở bước này KHÔNG được làm hỏng câu trả lời chính đã có — chỉ log cảnh báo,
     * phiên giữ nguyên `title = null` và tự rơi về rút gọn câu hỏi đầu (xem {@link #resolveTitle}).
     */
    private void trySetAiGeneratedTitle(ChatSession session, String question, String answer) {
        try {
            Map<String, Object> payload = Map.of("question", question, "answer", answer);
            AiWorkerTitleRes res = restTemplate.postForObject(
                    aiWorkerConfig.getBaseUrl() + "/api/v1/tutor/title", payload, AiWorkerTitleRes.class);
            if (res != null && res.title() != null && !res.title().isBlank()) {
                session.setTitle(res.title().trim());
                chatSessionRepository.save(session);
            }
        } catch (RestClientException exc) {
            log.warn("Khong tu dat ten duoc cuoc tro chuyen {} (khong anh huong cau tra loi chinh): {}",
                    session.getId(), exc.getMessage());
        }
    }

    private static List<Integer> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        String inner = trimmed.substring(1, trimmed.length() - 1).trim(); // bo [ ]
        if (inner.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(inner.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    private AiWorkerAskRes callAiWorker(Long lessonId, String question, List<ChatMessage> history, List<UploadedAttachment> attachments) {
        Map<String, Object> payload = Map.of(
                "lesson_id", lessonId,
                "question", question,
                "history", history.stream()
                        .map(m -> Map.of("sender", m.getSender(), "content", m.getContent()))
                        .toList(),
                "attachments", attachments.stream()
                        // Gui MIME type DA DO LAI (Tika), khong phai ten/duoi file client khai —
                        // Gemini can biet dung dinh dang thuc su cua du lieu inlineData.
                        .map(a -> Map.of("mime_type", a.mimeType(), "data_base64", a.dataBase64()))
                        .toList()
        );
        try {
            AiWorkerAskRes res = restTemplate.postForObject(
                    aiWorkerConfig.getBaseUrl() + "/api/v1/tutor/ask", payload, AiWorkerAskRes.class);
            if (res == null) {
                throw new ExternalServiceException("AI Worker không trả về kết quả.");
            }
            return res;
        } catch (RestClientException exc) {
            log.error("Goi AI Worker that bai cho Tutor (lesson {}): {}", lessonId, exc.getMessage());
            throw new ExternalServiceException("Gia sư AI hiện không khả dụng, vui lòng thử lại sau.");
        }
    }

    private static String toJsonArray(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        return sb.append(']').toString();
    }

    /** Khớp `TutorAskResponse` (Python, snake_case) của AI Worker. */
    private record AiWorkerAskRes(
            String answer,
            @JsonProperty("cited_timestamps") List<Integer> citedTimestamps,
            @JsonProperty("token_used") Integer tokenUsed
    ) {}

    /** Khớp response của {@code POST /api/v1/tutor/title} bên AI Worker. */
    private record AiWorkerTitleRes(String title) {}
}
