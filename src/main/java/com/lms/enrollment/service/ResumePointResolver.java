package com.lms.enrollment.service;

import java.util.List;

/**
 * Xác định điểm "Học tiếp" của một khóa học (trang Khóa học của tôi).
 *
 * <p>Quy tắc: lấy bản ghi tiến độ cập nhật gần nhất của học viên trong khóa.
 * <ul>
 *   <li>Chưa học bài nào (hoặc bài đó không còn trong khóa): bài đầu tiên, từ đầu.</li>
 *   <li>Bài gần nhất chưa xong: vào đúng bài đó, đúng vị trí đã lưu (BR-PROGRESS-03).</li>
 *   <li>Bài gần nhất đã xong: bài kế tiếp theo thứ tự chương/bài, từ đầu; nếu đó là bài cuối thì ở lại
 *       bài cuối, từ đầu (ôn lại).</li>
 * </ul>
 * Lớp thuần (không truy cập cơ sở dữ liệu) để kiểm thử dễ.
 */
public final class ResumePointResolver {

    private ResumePointResolver() {
    }

    /** Bản ghi tiến độ gần nhất của học viên trong khóa. */
    public record LatestProgress(Long lessonId, int lastPositionSec, boolean completed) {
    }

    /** Bài học và vị trí (giây) để mở khi bấm "Học tiếp"; {@code lessonId} null nếu khóa chưa có bài nào. */
    public record ResumePoint(Long lessonId, int positionSec) {
    }

    /**
     * @param orderedLessonIds id mọi bài của khóa theo đúng thứ tự chương rồi bài
     * @param latest           tiến độ cập nhật gần nhất; null nếu chưa học bài nào
     */
    public static ResumePoint resolve(List<Long> orderedLessonIds, LatestProgress latest) {
        if (orderedLessonIds.isEmpty()) {
            return new ResumePoint(null, 0);
        }
        Long first = orderedLessonIds.get(0);
        if (latest == null) {
            return new ResumePoint(first, 0);
        }
        int index = orderedLessonIds.indexOf(latest.lessonId());
        if (index < 0) {
            return new ResumePoint(first, 0);
        }
        if (!latest.completed()) {
            return new ResumePoint(latest.lessonId(), Math.max(0, latest.lastPositionSec()));
        }
        int nextIndex = Math.min(index + 1, orderedLessonIds.size() - 1);
        return new ResumePoint(orderedLessonIds.get(nextIndex), 0);
    }
}
