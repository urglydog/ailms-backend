package com.lms.material.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.enrollment.entity.LessonProgress;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.material.dto.StudyPlanDto;
import com.lms.material.dto.StudyPlanReq;
import com.lms.material.entity.StudentStudyPlan;
import com.lms.material.repository.StudentStudyPlanRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentStudyPlanService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StudentStudyPlanRepository studyPlanRepository;
    private final ChapterRepository chapterRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LessonRepository lessonRepository;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public StudyPlanDto getPlan(Long userId, Long courseId) {
        return studyPlanRepository.findByUserIdAndCourseId(userId, courseId)
                .map(plan -> StudyPlanDto.builder()
                        .courseId(plan.getCourseId())
                        .targetDate(plan.getTargetDate())
                        .hoursPerWeek(plan.getHoursPerWeek())
                        .planData(plan.getPlanData())
                        .build())
                .orElse(null);
    }

    @Transactional
    public StudyPlanDto generatePlan(Long userId, Long courseId, StudyPlanReq req) {
        // BUG THẬT (03/10/2026) — trước đây dùng LocalDate.now() (JVM default zone) ở đây
        // nhưng reschedulePlan() lại dùng VN_ZONE tường minh — 2 khái niệm "hôm nay" khác nhau
        // trong CÙNG 1 service. Thống nhất về VN_ZONE (ai-worker cũng đã đổi theo, xem study_plan.py).
        LocalDate today = LocalDate.now(VN_ZONE);

        // Validation 1: Target date must be > now + 1
        if (!req.getTargetDate().isAfter(today.plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải cách hôm nay ít nhất 2 ngày");
        }

        List<Chapter> chapters = chapterRepository.findChaptersWithLessonsByCourseId(courseId);
        List<LessonInfo> uncompletedLessons = new ArrayList<>();
        long totalRemainingSec = 0;

        for (Chapter c : chapters) {
            for (Lesson l : c.getLessons()) {
                Optional<LessonProgress> lp = lessonProgressRepository.findByUser_IdAndLesson_Id(userId, l.getId());
                if (lp.isEmpty() || !lp.get().getIsCompleted()) {
                    int duration = (l.getDurationSec() == null || l.getDurationSec() == 0) ? 600 : l.getDurationSec();
                    uncompletedLessons.add(new LessonInfo(l.getId(), l.getTitle(), duration, c.getTitle()));
                    totalRemainingSec += duration;
                }
            }
        }

        // Validation 2: Completed 100%
        if (uncompletedLessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã hoàn thành tất cả các bài giảng trong khóa học này!");
        }

        // Validation 4: Infeasible
        long weeks = ChronoUnit.WEEKS.between(today, req.getTargetDate());
        if (weeks == 0) weeks = 1; // At least 1 week for calculation if < 7 days
        long maxCommittedSec = weeks * req.getHoursPerWeek() * 3600L;
        if (totalRemainingSec > maxCommittedSec) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian cam kết không đủ để hoàn thành khóa học trước ngày bạn chọn. Vui lòng tăng số giờ học hoặc lùi ngày kết thúc.");
        }

        // Call AI Worker
        AiWorkerStudyPlanReq aiReq = new AiWorkerStudyPlanReq(req.getTargetDate().toString(), req.getHoursPerWeek(), uncompletedLessons);
        String url = aiWorkerConfig.getBaseUrl() + "/api/v1/study-plan/generate";
        
        try {
            // Assume AI worker returns the JSON string format inside 'plan_data'
            Map<String, Object> aiResponse = restTemplate.postForObject(url, aiReq, Map.class);
            if (aiResponse == null || !aiResponse.containsKey("plan_data")) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI Worker trả về dữ liệu rỗng");
            }

            String planDataJson = objectMapper.writeValueAsString(aiResponse.get("plan_data"));

            StudentStudyPlan plan = studyPlanRepository.findByUserIdAndCourseId(userId, courseId)
                    .orElseGet(() -> StudentStudyPlan.builder()
                            .userId(userId)
                            .courseId(courseId)
                            .build());
            
            plan.setTargetDate(req.getTargetDate());
            plan.setHoursPerWeek(req.getHoursPerWeek());
            plan.setPlanData(planDataJson);
            studyPlanRepository.save(plan);

            return StudyPlanDto.builder()
                    .courseId(courseId)
                    .targetDate(req.getTargetDate())
                    .hoursPerWeek(req.getHoursPerWeek())
                    .planData(planDataJson)
                    .build();
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi xử lý JSON lộ trình", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi gọi AI Worker sinh lộ trình", e);
        }
    }

    @Transactional
    public void deletePlan(Long userId, Long courseId) {
        studyPlanRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // RESCHEDULE — Thuật toán thuần (0 API cost), phiên bản v6 (23 lỗ hổng đã vá)
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public StudyPlanDto reschedulePlan(Long userId, Long courseId, LocalDate newTargetDate) {

        // ── BƯỚC 1: LOAD + PARSE ──
        StudentStudyPlan plan = studyPlanRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chưa có lộ trình để cập nhật. Hãy tạo lộ trình trước."));

        List<PlanDay> allDays;
        try {
            allDays = objectMapper.readValue(plan.getPlanData(), new TypeReference<List<PlanDay>>() {});
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu lộ trình bị hỏng, vui lòng xóa và tạo lại.");
        }

        // ── BƯỚC 2: XÁC ĐỊNH "HÔM NAY" (timezone VN) ──
        LocalDate today = LocalDate.now(VN_ZONE);

        // ── BƯỚC 3: THU THẬP TẤT CẢ bài từ planData (GIỮ THỨ TỰ) ──
        // Dùng List + seen Set, KHÔNG dùng HashMap (giữ thứ tự chapter→lesson gốc)
        List<PlanLesson> allPlanLessons = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (PlanDay day : allDays) {
            if (day.getLessons() == null) continue;
            for (PlanLesson lesson : day.getLessons()) {
                if (seen.add(lesson.getLesson_id())) {
                    allPlanLessons.add(lesson);
                }
            }
        }

        if (allPlanLessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lộ trình không có bài học nào.");
        }

        // ── BƯỚC 4: PHÂN LOẠI completed vs uncompleted ──
        Set<Long> allPlanLessonIds = allPlanLessons.stream()
                .map(PlanLesson::getLesson_id).collect(Collectors.toSet());

        Set<Long> completedIds = lessonProgressRepository
                .findCompletedLessonIdsByUserIdAndLessonIdIn(userId, allPlanLessonIds);

        List<PlanLesson> uncompletedLessons = allPlanLessons.stream()
                .filter(l -> !completedIds.contains(l.getLesson_id()))
                .collect(Collectors.toList());

        if (uncompletedLessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bạn đã hoàn thành tất cả các bài trong lộ trình!");
        }

        // ── BƯỚC 5: XÂY DỰNG pastDays + GOM BÀI COMPLETED ──
        List<PlanDay> pastDays = new ArrayList<>();
        Set<Long> pastKeptLessonIds = new HashSet<>();

        for (PlanDay day : allDays) {
            if (day.getDate() == null || day.getLessons() == null) continue;
            LocalDate dayDate = LocalDate.parse(day.getDate());

            if (dayDate.isBefore(today)) {
                // Ngày QUÁ KHỨ: chỉ giữ bài ĐÃ hoàn thành (tránh duplicate)
                List<PlanLesson> cleanedLessons = day.getLessons().stream()
                        .filter(l -> completedIds.contains(l.getLesson_id()))
                        .collect(Collectors.toList());
                if (!cleanedLessons.isEmpty()) {
                    PlanDay cleanedDay = new PlanDay();
                    cleanedDay.setDate(day.getDate());
                    cleanedDay.setLessons(cleanedLessons);
                    cleanedDay.setObjective(day.getObjective());
                    pastDays.add(cleanedDay);
                    cleanedLessons.forEach(l -> pastKeptLessonIds.add(l.getLesson_id()));
                }
            }
            // day.date >= today → bỏ qua (tính lại ở Bước 8)
        }

        // GOM TẤT CẢ bài completed CHƯA nằm trong pastDays
        // → gồm bài xếp hôm nay + bài tương lai học vượt
        List<PlanLesson> todayCompletedLessons = allPlanLessons.stream()
                .filter(l -> completedIds.contains(l.getLesson_id())
                        && !pastKeptLessonIds.contains(l.getLesson_id()))
                .collect(Collectors.toList());

        // ── BƯỚC 6: XÁC ĐỊNH effectiveTargetDate ──
        LocalDate effectiveTargetDate = (newTargetDate != null) ? newTargetDate : plan.getTargetDate();

        // Dùng isBefore (dấu <), cho phép targetDate == today (availableDays = 1)
        if (effectiveTargetDate.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hạn chót đã qua. Vui lòng chọn ngày mới.");
        }

        long availableDays = ChronoUnit.DAYS.between(today, effectiveTargetDate) + 1; // inclusive

        // ── BƯỚC 7: PRE-QUERY CHAPTER TITLES (tránh N+1) ──
        Set<Long> lessonIdsForLookup = uncompletedLessons.stream()
                .map(PlanLesson::getLesson_id).collect(Collectors.toSet());

        // Dùng forEach thay Collectors.toMap → tránh NPE khi row[1] null
        Map<Long, String> lessonChapterMap = new HashMap<>();
        List<Object[]> chapterRows = lessonRepository.findChapterTitlesByLessonIds(lessonIdsForLookup);
        for (Object[] row : chapterRows) {
            lessonChapterMap.put((Long) row[0], row[1] != null ? (String) row[1] : "");
        }

        // ── BƯỚC 8: BIN-PACKING VỚI ADAPTIVE BUDGET ──
        long totalRemainingSec = uncompletedLessons.stream()
                .mapToLong(l -> l.getDuration_minutes() * 60L).sum();
        long baseBudgetSec = (plan.getHoursPerWeek() * 3600L) / 7;
        // Ép double TRƯỚC khi chia → tránh integer division cắt cụt
        long dailyBudgetSec = Math.max(
                baseBudgetSec,
                (long) Math.ceil((double) totalRemainingSec / availableDays)
        );

        // Khởi tạo ngày TODAY với bài đã hoàn thành (tránh bốc hơi)
        List<PlanDay> newDays = new ArrayList<>();
        List<PlanLesson> currentLessons = new ArrayList<>(todayCompletedLessons);
        long currentTotalSec = todayCompletedLessons.stream()
                .mapToLong(l -> l.getDuration_minutes() * 60L).sum();
        int dayIndex = 0;

        for (PlanLesson lesson : uncompletedLessons) {
            long lessonSec = lesson.getDuration_minutes() * 60L;

            // Chuyển sang ngày mới nếu vượt budget VÀ còn ngày trống
            if (!currentLessons.isEmpty()
                    && (currentTotalSec + lessonSec > dailyBudgetSec)
                    && (dayIndex + 1 < availableDays)) {

                PlanDay day = new PlanDay();
                day.setDate(today.plusDays(dayIndex).toString());
                day.setLessons(new ArrayList<>(currentLessons));
                newDays.add(day);

                dayIndex++;
                currentLessons = new ArrayList<>();
                currentTotalSec = 0;
            }

            currentLessons.add(lesson);
            currentTotalSec += lessonSec;
        }

        // Đóng ngày cuối
        if (!currentLessons.isEmpty()) {
            PlanDay day = new PlanDay();
            day.setDate(today.plusDays(dayIndex).toString());
            day.setLessons(new ArrayList<>(currentLessons));
            newDays.add(day);
        }

        // ── BƯỚC 9: SINH OBJECTIVE ──
        for (PlanDay day : newDays) {
            List<String> chapterTitles = day.getLessons().stream()
                    .filter(l -> !completedIds.contains(l.getLesson_id()))
                    .map(l -> lessonChapterMap.getOrDefault(l.getLesson_id(), ""))
                    .filter(t -> !t.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (chapterTitles.isEmpty()) {
                day.setObjective("Ôn tập và củng cố");
            } else if (chapterTitles.size() == 1) {
                day.setObjective("Tiếp tục " + chapterTitles.get(0));
            } else {
                day.setObjective("Học " + String.join(" và ", chapterTitles));
            }
        }

        // ── BƯỚC 10: GỘP + LƯU ──
        List<PlanDay> finalPlanDays = new ArrayList<>(pastDays);
        finalPlanDays.addAll(newDays);

        try {
            plan.setPlanData(objectMapper.writeValueAsString(finalPlanDays));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi serialize lộ trình mới");
        }

        if (newTargetDate != null) {
            plan.setTargetDate(newTargetDate);
        }
        studyPlanRepository.save(plan);

        return StudyPlanDto.builder()
                .courseId(courseId)
                .targetDate(plan.getTargetDate())
                .hoursPerWeek(plan.getHoursPerWeek())
                .planData(plan.getPlanData())
                .build();
    }

    // ─── Inner DTOs ────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonInfo {
        private Long id;
        private String title;
        private Integer durationSec;
        private String chapterTitle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiWorkerStudyPlanReq {
        private String targetDate;
        private Integer hoursPerWeek;
        private List<LessonInfo> remainingLessons;
    }

    /** JSON shape khớp với planData đã lưu trong DB. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanDay {
        private String date;
        private List<PlanLesson> lessons;
        private String objective;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanLesson {
        private Long lesson_id;
        private String title;
        private int duration_minutes;
    }
}
