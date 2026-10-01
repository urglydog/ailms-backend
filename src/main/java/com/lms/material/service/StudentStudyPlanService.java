package com.lms.material.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentStudyPlanService {

    private final StudentStudyPlanRepository studyPlanRepository;
    private final ChapterRepository chapterRepository;
    private final LessonProgressRepository lessonProgressRepository;
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
        // Validation 1: Target date must be > now + 1
        if (!req.getTargetDate().isAfter(LocalDate.now().plusDays(1))) {
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
        long weeks = ChronoUnit.WEEKS.between(LocalDate.now(), req.getTargetDate());
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
}
