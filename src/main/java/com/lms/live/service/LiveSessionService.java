package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.live.dto.LiveSessionDto.CreateReq;
import com.lms.live.dto.LiveSessionDto.Res;
import com.lms.live.dto.LiveSessionDto.StartRes;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveSessionRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC50 — vòng đời phiên Live Classroom, phía giảng viên (F11.1). Endpoint GET cho người XEM
 * (Student/Guest/Admin, kiểm quyền BR-LIVE-01) thuộc {@code LiveViewController} của F11.2,
 * không nằm ở service này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LiveKitConfig liveKitConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final RoomServiceClient roomServiceClient;

    /** Định danh participant LiveKit của giảng viên — webhook (BR-LIVE-09) dựa vào tiền tố này để nhận diện. */
    public static String instructorIdentity(Long instructorId) {
        return "instructor-" + instructorId;
    }

    @Transactional
    public Res create(String instructorEmail, CreateReq req) {
        User instructor = requireUser(instructorEmail);
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));
        requireOwnership(instructor, course);

        LiveSession session = new LiveSession();
        session.setInstructor(instructor);
        session.setCourse(course);
        session.setTitle(req.title());
        session.setVisibility(req.visibility());
        session.setSourceLanguage(resolveSourceLanguage(req.sourceLanguage(), instructor));
        session.setScheduledAt(req.scheduledAt());
        session.setRoomName("live-" + UUID.randomUUID());
        session.setStatus(LiveSessionStatus.SCHEDULED);

        return toRes(liveSessionRepository.save(session));
    }

    /**
     * BUG THẬT (31/08/2026): thiếu {@code @Transactional} ở đây khiến {@code toRes()} gọi
     * {@code session.getCourse()} (LAZY) SAU KHI Hibernate session đã đóng —
     * {@code open-in-view: false} không tự mở lại session cho vòng lặp map ngoài transaction,
     * ném {@code LazyInitializationException} → FE nhận lỗi 500 "Đã xảy ra lỗi không mong đợi".
     * Toàn bộ method có truy cập quan hệ LAZY của entity đều phải có {@code @Transactional}.
     */
    @Transactional(readOnly = true)
    public List<Res> listMine(String instructorEmail) {
        User instructor = requireUser(instructorEmail);
        return liveSessionRepository.findByInstructor_IdOrderByCreatedAtDesc(instructor.getId())
                .stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public Res getOwned(String instructorEmail, Long sessionId) {
        User instructor = requireUser(instructorEmail);
        LiveSession session = requireSession(sessionId);
        requireOwnership(instructor, session.getCourse());
        return toRes(session);
    }

    /**
     * BR-LIVE-03/04 — cấp token {@code canPublish=true} cho đúng giảng viên sở hữu.
     *
     * <p>Gọi lại khi phiên ĐÃ {@code LIVE} (giảng viên tải lại trang, hoặc rớt mạng rồi vào lại
     * trong vòng 60s ân hạn) là hợp lệ — cấp token mới, KHÔNG đổi {@code startedAt}, chỉ xoá
     * {@code instructorDisconnectedAt} phòng khi webhook chưa kịp báo {@code participant_joined}
     * (BR-LIVE-09) để {@code LiveSessionCronJob} không kết thúc oan phiên vừa được vào lại.
     */
    @Transactional
    public StartRes start(String instructorEmail, Long sessionId) {
        User instructor = requireUser(instructorEmail);
        LiveSession session = requireSession(sessionId);
        requireOwnership(instructor, session.getCourse());

        if (session.getStatus() == LiveSessionStatus.SCHEDULED) {
            session.setStatus(LiveSessionStatus.LIVE);
            session.setStartedAt(LocalDateTime.now());
        } else if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new ConflictException(
                    "Phiên đã kết thúc (" + session.getStatus() + "), không bắt đầu lại được nữa");
        }
        session.setInstructorDisconnectedAt(null);
        liveSessionRepository.save(session);

        return issueInstructorToken(session, instructor);
    }

    private StartRes issueInstructorToken(LiveSession session, User instructor) {
        String identity = instructorIdentity(instructor.getId());
        AccessToken token = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
        token.setIdentity(identity);
        token.setName(instructor.getFullName());
        // F11.4 — canPublishData: giảng viên gửi chat + phát lệnh ẩn tin nhắn (BR-LIVE-10) qua
        // đúng kênh Data Messages của LiveKit, không cần entity/endpoint riêng (BR-LIVE-12).
        token.addGrants(new RoomJoin(true), new RoomName(session.getRoomName()),
                new CanPublish(true), new CanSubscribe(true), new CanPublishData(true));
        return new StartRes(token.toJwt(), liveKitConfig.getServerUrl(), session.getRoomName(), identity);
    }

    /** UC50 — giảng viên chủ động kết thúc. BR-LIVE-09 (tự động kết thúc) nằm ở {@link LiveSessionCronJob}. */
    @Transactional
    public Res end(String instructorEmail, Long sessionId) {
        User instructor = requireUser(instructorEmail);
        LiveSession session = requireSession(sessionId);
        requireOwnership(instructor, session.getCourse());

        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new ConflictException(
                    "Chỉ kết thúc được phiên đang LIVE (hiện tại: " + session.getStatus() + ")");
        }

        session.setStatus(LiveSessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        session.setInstructorDisconnectedAt(null);
        liveSessionRepository.save(session);

        eventPublisher.publishEvent(new LiveSessionEndedEvent(session.getId()));
        deleteRoomBestEffort(session.getRoomName());
        return toRes(session);
    }

    /**
     * Đóng hẳn phòng phía LiveKit — bắt buộc mọi trình duyệt đang kết nối (giảng viên, học viên,
     * Translation Agent) ngắt kết nối NGAY, không đợi client tự nhận ra phiên đã kết thúc rồi tự
     * rời. Chống đúng rủi ro thực tế: trình duyệt "treo" (đóng máy không tắt tab, mất mạng giữa
     * chừng...) vẫn giữ kết nối WebRTC tới LiveKit Cloud, phát sinh phí dù phiên đã ENDED trong DB
     * từ lâu. Best-effort — DB đã ghi ENDED rồi (nguồn sự thật), lỗi ở bước này chỉ log lại chứ
     * không làm hỏng kết quả kết thúc phiên.
     */
    private void deleteRoomBestEffort(String roomName) {
        try {
            roomServiceClient.deleteRoom(roomName).execute();
        } catch (Exception e) {
            log.warn("Khong dong duoc phong LiveKit {} (khong anh huong trang thai DB): {}",
                    roomName, e.getMessage());
        }
    }

    private String resolveSourceLanguage(String requested, User instructor) {
        return (requested == null || requested.isBlank()) ? instructor.getPreferredLanguage() : requested;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private LiveSession requireSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession", sessionId));
    }

    /** BR-ROLE-01 — kiểm ownership ở tầng service, không chỉ dựa vào {@code @PreAuthorize}. */
    private void requireOwnership(User instructor, Course course) {
        if (!course.getInstructor().getId().equals(instructor.getId())) {
            throw new AccessDeniedDomainException("Bạn không sở hữu khóa học này");
        }
    }

    private Res toRes(LiveSession s) {
        return new Res(
                s.getId(), s.getTitle(), s.getVisibility(), s.getStatus(), s.getRoomName(),
                s.getSourceLanguage(), s.getScheduledAt(), s.getStartedAt(), s.getEndedAt(),
                s.getCourse().getId(), s.getCourse().getTitle());
    }
}
