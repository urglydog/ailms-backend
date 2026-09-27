package com.lms.chat.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.catalog.util.SlugGenerator;
import com.lms.chat.entity.TutorSecurityFlag;
import com.lms.chat.entity.TutorSecurityPattern;
import com.lms.chat.dto.TutorSecurityDto.FlagRes;
import com.lms.chat.repository.TutorSecurityFlagRepository;
import com.lms.chat.repository.TutorSecurityPatternRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lớp 2 phòng thủ (bổ sung, KHÔNG thay thế Lớp 1 — giới hạn tool đọc/ghi ở AI Worker) chống prompt
 * injection/jailbreak cho Socratic Tutor Agent — BR-TUTOR-SEC-06
 * (doc/feat/injection/DacTa_ChongPromptInjection_TutorAgent.md mục 3, bước [2] + mục 5).
 *
 * <p><b>Pre-check heuristic, KHÔNG chặn:</b> quét tin nhắn học viên với danh sách pattern cấu hình
 * trong DB ({@link TutorSecurityPattern}) TRƯỚC khi gọi AI Worker. Khớp pattern nào thì GHI LOG
 * ({@link TutorSecurityFlag}) để Admin xem lại, KHÔNG tự động khóa tài khoản hay chặn câu hỏi ở v1
 * (tránh false-positive chặn nhầm học viên hỏi hợp lệ — vd đoạn code dài chứa từ "system"/"xóa"
 * hoàn toàn vô hại). Câu hỏi vẫn được gửi tiếp cho AI Worker bình thường dù có khớp pattern.
 *
 * <p>Danh sách pattern nạp 1 lần lúc khởi động rồi làm mới định kỳ (không đọc DB mỗi tin nhắn) —
 * bảng gần như không đổi, refresh mỗi 5 phút là đủ để Admin thêm pattern mới mà không cần khởi
 * động lại backend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorSecurityService {

    private final TutorSecurityPatternRepository patternRepository;
    private final TutorSecurityFlagRepository flagRepository;

    private record CompiledPattern(Pattern pattern, String description) {}

    private volatile List<CompiledPattern> compiledPatterns = List.of();

    @PostConstruct
    void init() {
        refreshPatterns();
    }

    @Scheduled(fixedDelay = 300_000)
    void refreshPatterns() {
        List<TutorSecurityPattern> rows = patternRepository.findByEnabledTrue();
        compiledPatterns = rows.stream()
                .map(row -> {
                    try {
                        return new CompiledPattern(Pattern.compile(row.getPattern(), Pattern.CASE_INSENSITIVE), row.getDescription());
                    } catch (PatternSyntaxException e) {
                        log.error("Pattern bao mat Tutor Agent khong hop le (id={}, pattern={}), bo qua: {}",
                                row.getId(), row.getPattern(), e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Quét 1 tin nhắn học viên vừa gửi vào Tutor Agent, ghi log MỌI pattern khớp — gọi TRƯỚC khi
     * chuyển tiếp câu hỏi cho AI Worker (xem {@link TutorService#ask}). KHÔNG BAO GIỜ ném exception
     * ra ngoài: lỗi ở lớp phòng thủ bổ sung này không được phép làm hỏng luồng trả lời chính của
     * Tutor Agent (giống nguyên tắc {@code trySetAiGeneratedTitle}).
     */
    @Transactional
    public void screenMessage(User student, Course course, String rawMessage) {
        try {
            String normalized = SlugGenerator.stripAccentsLower(rawMessage);
            for (CompiledPattern cp : compiledPatterns) {
                if (cp.pattern().matcher(normalized).find()) {
                    TutorSecurityFlag flag = new TutorSecurityFlag();
                    flag.setStudent(student);
                    flag.setCourse(course);
                    flag.setMatchedPattern(cp.description());
                    flag.setMessageSnapshot(rawMessage);
                    flagRepository.save(flag);
                    log.warn("Tutor Agent security flag: student={}, course={}, pattern=\"{}\"",
                            student.getId(), course.getId(), cp.description());
                }
            }
        } catch (Exception e) {
            log.error("Loi khong xac dinh o lop pre-check heuristic Tutor Agent (khong anh huong cau tra loi chinh)", e);
        }
    }

    /** Danh sách flag cho Admin xem lại (BR-TUTOR-SEC-06 — "phải hiển thị được cho Admin theo
     * dõi"), mới nhất trước. */
    @Transactional(readOnly = true)
    public Page<FlagRes> getFlags(Pageable pageable) {
        return flagRepository.findAllBy(pageable).map(f -> new FlagRes(
                f.getId(),
                f.getStudent().getId(), f.getStudent().getFullName(), f.getStudent().getEmail(),
                f.getCourse().getId(), f.getCourse().getTitle(),
                f.getMatchedPattern(), f.getMessageSnapshot(), f.getCreatedAt()));
    }
}
