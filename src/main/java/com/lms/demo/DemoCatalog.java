package com.lms.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/** Cấu trúc file {@code demo/courses.json} (danh mục khóa học demo lấy từ playlist YouTube). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoCatalog(Map<String, String> categories, List<Instructor> instructors, List<Course> courses) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Instructor(String fullName, String email, String headline, String bio, String avatarUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Course(
            String slug, String title, String description, String thumbnailUrl, String level, long price,
            String category, String status, int ageMonths, int ageDays, String lang, Instructor instructor,
            List<Chapter> chapters) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chapter(String title, List<Lesson> lessons) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lesson(String title, String description, String youtubeId, int durationSec, boolean isPreview) {}
}
