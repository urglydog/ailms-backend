package com.lms.live.repository;

import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findByInstructor_IdOrderByCreatedAtDesc(Long instructorId);

    Optional<LiveSession> findByRoomNameAndStatus(String roomName, LiveSessionStatus status);

    /** BR-LIVE-09 — quét định kỳ tìm phiên LIVE mà giảng viên mất kết nối quá lâu. */
    List<LiveSession> findByStatusAndInstructorDisconnectedAtIsNotNullAndInstructorDisconnectedAtLessThanEqual(
            LiveSessionStatus status, LocalDateTime threshold);

    List<LiveSession> findByCourse_IdAndStatusIn(Long courseId, List<LiveSessionStatus> statuses);
}
