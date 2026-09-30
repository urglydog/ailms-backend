package com.lms.material.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyPlanReq {
    @NotNull(message = "Ngày kết thúc không được để trống")
    @Future(message = "Ngày kết thúc phải ở tương lai")
    private LocalDate targetDate;

    @NotNull(message = "Số giờ học mỗi tuần không được để trống")
    @Min(value = 1, message = "Số giờ học mỗi tuần tối thiểu là 1")
    private Integer hoursPerWeek;
}
