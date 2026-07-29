package com.lms.catalog.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller TẠM THỜI cho Giai đoạn 2 (Khóa học).
 * Cung cấp dummy data để không bị lỗi 500 khi test trên Postman ở Giai đoạn 1.
 */
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    /**
     * Lấy danh sách khóa học (API Public)
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCourses() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "id", 1,
                        "title", "Tiếng Anh Giao Tiếp Cơ Bản",
                        "description", "Khóa học dummy để test UI",
                        "price", 500000,
                        "instructorId", 2
                )
        ));
    }

    /**
     * Giảng viên tạo khóa học mới
     */
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createCourse(@RequestBody Map<String, Object> payload) {
        // Giả lập lưu vào DB
        payload.put("id", 999);
        payload.put("status", "DRAFT");
        payload.put("message", "Đã tạo khóa học thành công (Đây là API Mock của Giai đoạn 2)");
        return ResponseEntity.ok(payload);
    }
}
