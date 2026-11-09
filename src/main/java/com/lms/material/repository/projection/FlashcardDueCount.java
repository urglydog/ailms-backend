package com.lms.material.repository.projection;

/** Số thẻ flashcard đến hạn ôn của một học viên (đếm ngay trong DB cho job nhắc ôn). */
public record FlashcardDueCount(String email, Long count) {
}
