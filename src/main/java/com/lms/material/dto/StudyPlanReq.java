package com.lms.material.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyPlanReq {
    @NotNull(message = "Ngày kết thúc không được để trống")
    @Future(message = "Ngày kết thúc phải ở tương lai")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    @NotNull(message = "Số giờ học mỗi tuần không được để trống")
    @Min(value = 1, message = "Số giờ học mỗi tuần tối thiểu là 1")
    // BUG THẬT (03/10/2026) — trước đây chỉ cap 168 ở thuộc tính `max` của <input> phía FE,
    // gọi API trực tiếp (Postman/script) vẫn gửi được số tùy ý (vd 999999), bỏ qua validate.
    @Max(value = 168, message = "Số giờ học mỗi tuần không thể vượt quá 168 (số giờ của 1 tuần)")
    private Integer hoursPerWeek;
}
