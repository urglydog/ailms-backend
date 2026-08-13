package com.lms.chat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorDto.AskReq;
import com.lms.chat.dto.TutorDto.AskRes;
import com.lms.chat.entity.ChatMessage;
import com.lms.chat.entity.ChatSession;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.chat.repository.ChatSessionRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ExternalServiceException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentSecurity enrollmentSecurity;
    private final TutorQuotaService tutorQuotaService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;

    @Transactional
    public AskRes ask(String email, Long lessonId, AskReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));

        // UC30 actor chỉ có Student, và chỉ hỏi được nội dung bài học ĐÃ sở hữu — không
        // cho phép ở bài Preview (giống LessonProgressService: Preview không có quyền
        // đầy đủ, BR-ENROLL-02).
        if (!enrollmentSecurity.canAccessLesson(email, lessonId, false)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }

        // BR-TUTOR-04
        tutorQuotaService.consume(user.getId());

        ChatSession session = req.sessionId() != null
                ? chatSessionRepository.findById(req.sessionId())
                        .orElseThrow(() -> new ResourceNotFoundException("ChatSession", req.sessionId()))
                : chatSessionRepository.findFirstByUser_IdAndLesson_IdOrderByCreatedAtDesc(user.getId(), lessonId)
                        .orElseGet(() -> {
                            ChatSession s = new ChatSession();
                            s.setUser(user);
                            s.setLesson(lesson);
                            return chatSessionRepository.save(s);
                        });

        AiWorkerAskRes aiRes = callAiWorker(lessonId, req.question());

        ChatMessage userMsg = new ChatMessage();
        userMsg.setChatSession(session);
        userMsg.setSender("USER");
        userMsg.setContent(req.question());
        chatMessageRepository.save(userMsg);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setChatSession(session);
        aiMsg.setSender("AI");
        aiMsg.setContent(aiRes.answer());
        aiMsg.setCitedTimestamps(toJsonArray(aiRes.citedTimestamps()));
        aiMsg.setTokenUsed(aiRes.tokenUsed());
        chatMessageRepository.save(aiMsg);

        return new AskRes(session.getId(), aiRes.answer(), aiRes.citedTimestamps(), aiRes.tokenUsed());
    }

    private AiWorkerAskRes callAiWorker(Long lessonId, String question) {
        Map<String, Object> payload = Map.of(
                "lesson_id", lessonId,
                "question", question
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
}
