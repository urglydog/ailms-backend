-- ---------------------------------------------------------------------
-- Gia su AI (UC30): phien chat chuyen tu pham vi 1 BAI HOC sang pham vi 1 KHOA HOC —
-- 1 hoc vien + 1 khoa hoc dung CHUNG 1 danh sach lich su chat cho MOI bai hoc trong khoa
-- (truoc day moi bai hoc co danh sach rieng, gay loi "doi bai la mat lich su chat").
-- Bai hoc dang mo hien tai gio truyen theo TUNG luot hoi (xem TutorDto.AskReq.currentLessonId)
-- thay vi co dinh theo ChatSession.
-- ---------------------------------------------------------------------
ALTER TABLE chat_sessions ADD COLUMN course_id BIGINT NULL AFTER lesson_id;

UPDATE chat_sessions cs
JOIN lessons l  ON l.id = cs.lesson_id
JOIN chapters c ON c.id = l.chapter_id
SET cs.course_id = c.course_id;

ALTER TABLE chat_sessions MODIFY COLUMN course_id BIGINT NOT NULL;
ALTER TABLE chat_sessions DROP FOREIGN KEY fk_chat_sessions_lesson_id;
ALTER TABLE chat_sessions DROP COLUMN lesson_id;
ALTER TABLE chat_sessions ADD CONSTRAINT fk_chat_sessions_course_id FOREIGN KEY (course_id) REFERENCES courses (id);
CREATE INDEX idx_chat_sessions_user_course ON chat_sessions (user_id, course_id);

-- Moi cau tra loi AI ghi nho BAI HOC THAT SU duoc dung lam ngu canh cho cau tra loi do —
-- co the KHAC bai hoc hoc vien dang mo tren trinh duyet neu ho hoi ro ve 1 bai khac trong
-- khoa. FE dung gia tri nay de biet cac moc thoi gian trich dan (cited_timestamps) thuoc
-- video bai hoc nao ma tua dung, tranh tua nham vao video dang mo.
ALTER TABLE chat_messages ADD COLUMN context_lesson_id BIGINT NULL AFTER cited_timestamps;
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_context_lesson_id FOREIGN KEY (context_lesson_id) REFERENCES lessons (id);
