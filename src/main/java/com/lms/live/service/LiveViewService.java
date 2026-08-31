package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.common.config.LiveKitConfig;
import com.lms.live.dto.LiveViewDto.DetailRes;
import com.lms.live.dto.LiveViewDto.SummaryRes;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import com.lms.live.repository.LiveSessionRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC51 — xem phiên Live theo đúng phân quyền BR-LIVE-01. Chỉ ĐỌC, không đổi trạng thái
 * {@link LiveSession} nào — trái với {@code LiveSessionService} (F11.1, phía giảng viên).
 *
 * <p>{@code viewerEmail = null} nghĩa là Guest (chưa đăng nhập) — hợp lệ, KHÔNG phải lỗi:
 * {@code /api/v1/live-sessions/*}{@code /view} và {@code /api/v1/courses/*}{@code /live-sessions}
 * đều là endpoint public (xem {@code SecurityConfig}), Guest gọi được để xem phiên {@code PUBLIC}.
 */
@Service
@RequiredArgsConstructor
public class LiveViewService {

    private static final List<LiveSessionStatus> LISTABLE_STATUSES =
            List.of(LiveSessionStatus.SCHEDULED, LiveSessionStatus.LIVE);

    private final LiveSessionRepository liveSessionRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LiveKitConfig liveKitConfig;

    @Transactional(readOnly = true)
    public List<SummaryRes> listForCourse(String viewerEmail, Long courseId) {
        User viewer = resolveViewer(viewerEmail);
        return liveSessionRepository.findByCourse_IdAndStatusIn(courseId, LISTABLE_STATUSES).stream()
                .filter(session -> canView(viewer, session))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public DetailRes view(String viewerEmail, Long sessionId) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession", sessionId));
        User viewer = resolveViewer(viewerEmail);

        if (!canView(viewer, session)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-LIVE-01)");
        }

        return toDetail(session, viewer);
    }

    /** Package-private (không {@code private}) có chủ đích — {@code LiveLanguageTrackService}
     * (F11.3) tái dùng nguyên logic này thay vì chép lại, cùng gói {@code com.lms.live.service}
     * nên không cần mở public rộng hơn mức cần thiết. */
    User resolveViewer(String email) {
        return email == null ? null : userRepository.findByEmail(email).orElse(null);
    }

    /** BR-LIVE-01. {@code PUBLIC} luôn xem được (kể cả Guest); {@code COURSE_ONLY} cần ghi danh
     * hoặc là chủ sở hữu/Admin — Guest KHÔNG bao giờ qua được nhánh này (khác BR-LIVE-02, vốn chỉ
     * chặn HÀNH ĐỘNG tương tác chứ không chặn xem). */
    boolean canView(User viewer, LiveSession session) {
        if (session.getVisibility() == LiveVisibility.PUBLIC) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (viewer.getRole() == Role.ADMIN) {
            return true;
        }
        if (session.getInstructor().getId().equals(viewer.getId())) {
            return true;
        }
        return enrollmentRepository.existsByUser_IdAndCourse_Id(viewer.getId(), session.getCourse().getId());
    }

    private DetailRes toDetail(LiveSession session, User viewer) {
        String viewerToken = null;
        String serverUrl = null;
        String roomName = null;
        if (session.getStatus() == LiveSessionStatus.LIVE) {
            viewerToken = issueViewerToken(session, viewer);
            serverUrl = liveKitConfig.getServerUrl();
            roomName = session.getRoomName();
        }
        return new DetailRes(
                session.getId(), session.getTitle(), session.getVisibility(), session.getStatus(),
                session.getSourceLanguage(), session.getScheduledAt(), session.getStartedAt(), session.getEndedAt(),
                session.getCourse().getId(), session.getCourse().getTitle(),
                viewerToken, serverUrl, roomName);
    }

    /**
     * {@code canPublish=false} — người xem chỉ subscribe, không phát media (khác token giảng
     * viên ở F11.1). {@code canPublishData} CHÍNH LÀ cơ chế chặn Guest gửi chat live (F11.4,
     * BR-LIVE-02, BR-LIVE-12) — Guest ({@code viewer == null}) không có quyền này nên
     * {@code room.localParticipant.publishData(...)}/{@code sendChatMessage(...)} phía LiveKit
     * tự chặn ở tầng server, không chỉ dựa vào FE ẩn nút gửi.
     */
    private String issueViewerToken(LiveSession session, User viewer) {
        String identity = viewer != null ? "student-" + viewer.getId() : "guest-" + UUID.randomUUID();
        AccessToken token = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
        token.setIdentity(identity);
        token.setName(viewer != null ? viewer.getFullName() : "Khách");
        token.addGrants(new RoomJoin(true), new RoomName(session.getRoomName()),
                new CanPublish(false), new CanSubscribe(true), new CanPublishData(viewer != null));
        return token.toJwt();
    }

    private SummaryRes toSummary(LiveSession session) {
        return new SummaryRes(session.getId(), session.getTitle(), session.getStatus(),
                session.getScheduledAt(), session.getStartedAt());
    }
}
