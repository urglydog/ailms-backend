-- Chuyển đổi cột next_review_at từ DATE sang DATETIME để hỗ trợ SRS theo phút/giờ (LocalDateTime)
ALTER TABLE flashcard_reviews
MODIFY COLUMN next_review_at DATETIME(6);
