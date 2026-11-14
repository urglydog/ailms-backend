package com.lms.material.repository.projection;

/** Số thẻ flashcard đến hạn ôn của một học viên TRONG MỘT KHÓA HỌC (đếm ngay trong DB cho job nhắc ôn). */
public record FlashcardDueCount(Long userId, Long courseId, String courseTitle, Long count) {
}
