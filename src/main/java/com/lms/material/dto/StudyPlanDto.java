package com.lms.material.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlanDto {
    private Long courseId;
    private LocalDate targetDate;
    private Integer hoursPerWeek;
    private String planData; // JSON string
}
