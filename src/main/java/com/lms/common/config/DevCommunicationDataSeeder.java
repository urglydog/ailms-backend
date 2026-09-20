package com.lms.common.config;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.communication.entity.Announcement;
import com.lms.communication.entity.Conversation;
import com.lms.communication.entity.Message;
import com.lms.communication.repository.AnnouncementRepository;
import com.lms.communication.repository.ConversationRepository;
import com.lms.communication.repository.MessageRepository;
import com.lms.community.entity.LessonChat;
import com.lms.community.repository.LessonChatRepository;
import com.lms.common.service.NotificationService;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/**
 * Dữ liệu mẫu cho Q&amp;A (Hỏi đáp bài học) và Tin nhắn giữa Giảng viên/Học viên — môi trường DEV
 * (20/09/2026, theo yêu cầu — cần dữ liệu thật để test redesign Q&amp;A kiểu comment Facebook).
 *
 * <p>{@code @Order(3)}: chạy SAU {@link DevDataSeeder} (User, {@code @Order(1)}) và
 * {@link DevCourseDataSeeder} (Course/Lesson/Enrollment, {@code @Order(2)}) — cần cả 2 tồn tại
 * trước. KHÔNG gate theo tổng số bản ghi (CSDL dev thật đã có sẵn dữ liệu Hỏi đáp/Tin nhắn do cả
 * nhóm tự test qua UI từ trước, không phải do seeder nào tạo ra) — thay vào đó tự tra 1 câu hỏi/
 * hội thoại có nội dung ĐÁNH DẤU cố định ({@link #SEED_MARKER}) để biết ĐÃ seed hay chưa, an toàn
 * khi backend restart nhiều lần.
 */
@Configuration
@Profile("dev")
public class DevCommunicationDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevCommunicationDataSeeder.class);
    private static final String SEED_MARKER = "[Dữ liệu mẫu]";

    @Bean
    @Order(3)
    public ApplicationRunner seedDevCommunicationData(
            UserRepository userRepository,
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            EnrollmentRepository enrollmentRepository,
            LessonChatRepository chatRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AnnouncementRepository announcementRepository,
            NotificationService notificationService) {
        return args -> {
            Optional<User> instructorOpt = userRepository.findByEmail("instructor@lms.local");
            Optional<User> student1Opt = userRepository.findByEmail("student1@lms.local");
            Optional<User> student2Opt = userRepository.findByEmail("student2@lms.local");
            if (instructorOpt.isEmpty() || student1Opt.isEmpty() || student2Opt.isEmpty()) {
                log.info("Bo qua seed Hoi dap/Tin nhan: chua co du 3 tai khoan mau (DevDataSeeder chua chay).");
                return;
            }
            User instructor = instructorOpt.get();
            User student1 = student1Opt.get();
            User student2 = student2Opt.get();

            Course course = findCourseOwnedByInstructor(courseRepository, enrollmentRepository, student1.getEmail(), instructor.getEmail());
            if (course == null) {
                log.info("Bo qua seed Hoi dap/Tin nhan: student1 chua ghi danh khoa nao cua instructor@lms.local.");
                return;
            }
            Lesson lesson = lessonRepository
                    .findFirstByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(course.getId())
                    .orElse(null);
            if (lesson == null) {
                log.info("Bo qua seed Hoi dap: khoa \"{}\" chua co bai hoc nao.", course.getTitle());
                return;
            }

            boolean alreadySeeded = chatRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                    .anyMatch(c -> c.getContent().startsWith(SEED_MARKER));
            if (!alreadySeeded) {
                seedQaThread(chatRepository, lesson, instructor, student1, student2);
            }

            boolean conv1Seeded = seedConversation(conversationRepository, messageRepository, instructor, student1,
                    "Chào thầy/cô, em có thắc mắc về nội dung bài giảng đầu tiên ạ.",
                    "Chào em, em cứ hỏi thoải mái nhé, thầy/cô sẵn sàng hỗ trợ.",
                    "Dạ em cảm ơn thầy/cô!");
            if (conv1Seeded) {
                // Notification thật cho tin nhắn CUỐI (giảng viên → student1) — cùng đúng nội
                // dung `MessageService.sendMessage` sẽ tạo, để test lỗi hiển thị nội dung thông
                // báo (xem yêu cầu "phần thông báo không hiển thị nội dung").
                notificationService.notify(student1.getId(), "NEW_MESSAGE",
                        "Tin nhắn mới từ " + instructor.getFullName(),
                        "Dạ em cảm ơn thầy/cô!", "/messages");
            }
            boolean conv2Seeded = seedConversation(conversationRepository, messageRepository, instructor, student2,
                    "Khóa học này có hỗ trợ học viên sau khi hoàn thành không ạ?",
                    "Có em nhé, em có thể nhắn tin cho thầy/cô bất cứ lúc nào kể cả sau khi học xong.",
                    null);
            if (conv2Seeded) {
                notificationService.notify(student2.getId(), "NEW_MESSAGE",
                        "Tin nhắn mới từ " + instructor.getFullName(),
                        "Có em nhé, em có thể nhắn tin cho thầy/cô bất cứ lúc nào kể cả sau khi học xong.",
                        "/messages");
            }

            Announcement announcement = seedAnnouncement(announcementRepository, course);
            if (announcement != null) {
                String linkUrl = "/courses/" + course.getSlug();
                if (enrollmentRepository.existsByUser_IdAndCourse_Id(student1.getId(), course.getId())) {
                    notificationService.notify(student1.getId(), "ANNOUNCEMENT",
                            "[" + course.getTitle() + "] " + announcement.getTitle(), announcement.getContent(), linkUrl);
                }
                if (enrollmentRepository.existsByUser_IdAndCourse_Id(student2.getId(), course.getId())) {
                    notificationService.notify(student2.getId(), "ANNOUNCEMENT",
                            "[" + course.getTitle() + "] " + announcement.getTitle(), announcement.getContent(), linkUrl);
                }
            }

            log.info("Da seed du lieu mau Hoi dap + Tin nhan + Thong bao cho khoa \"{}\".", course.getTitle());
        };
    }

    /** Khóa ĐẦU TIÊN của `instructorEmail` mà `studentEmail` đã ghi danh. (20/09/2026, sửa lỗi)
     * — trước đây duyệt qua {@code Enrollment::getCourse} rồi đọc {@code Course.getInstructor()}
     * là quan hệ LAZY: đến lúc Java stream chạy tới, phiên Hibernate đã đóng (ApplicationRunner
     * không nằm trong transaction) → {@code LazyInitializationException}, sập cả app lúc khởi
     * động. Giờ lọc theo Course CỦA ĐÚNG giảng viên đó trước (query có sẵn), rồi chỉ kiểm tồn
     * tại ghi danh theo ID — không đụng object LAZY nào cả. */
    private Course findCourseOwnedByInstructor(
            CourseRepository courseRepository, EnrollmentRepository enrollmentRepository,
            String studentEmail, String instructorEmail) {
        List<Course> instructorCourses = courseRepository
                .findByInstructor_Email(instructorEmail, org.springframework.data.domain.Pageable.unpaged())
                .getContent();
        return instructorCourses.stream()
                .filter(c -> enrollmentRepository.existsByUser_EmailAndCourse_Id(studentEmail, c.getId()))
                .findFirst()
                .orElse(null);
    }

    /** 1 câu hỏi + nhiều câu trả lời xen kẽ (giảng viên trả lời 2 lần + học viên khác cùng tham
     * gia) — đủ dữ liệu để test hiển thị "câu trả lời đầu tiên của giảng viên" ghim đầu, các câu
     * còn lại mới nhất lên trên, và nút "Xem thêm" phân trang. */
    private void seedQaThread(
            LessonChatRepository chatRepository, Lesson lesson, User instructor, User student1, User student2) {
        LessonChat question = saveChat(chatRepository, lesson, student1,
                SEED_MARKER + " Thầy/cô ơi, phần này em xem video vẫn chưa hiểu rõ lắm ạ, có tài liệu thêm không?",
                null);
        saveChat(chatRepository, lesson, instructor,
                "Em xem thêm phần \"Tài nguyên\" của bài học nhé, thầy/cô có đính kèm slide chi tiết hơn.",
                question.getId());
        saveChat(chatRepository, lesson, student2,
                "Em cũng thắc mắc chỗ này, cảm ơn thầy/cô đã giải thích!",
                question.getId());
        saveChat(chatRepository, lesson, student1,
                "Dạ em tìm thấy rồi ạ, em cảm ơn thầy/cô nhiều!",
                question.getId());
        saveChat(chatRepository, lesson, instructor,
                "Không có gì, có gì chưa rõ cứ hỏi tiếp thầy/cô nhé.",
                question.getId());
        saveChat(chatRepository, lesson, student2,
                "Thầy/cô cho em hỏi thêm là bài tập nộp ở đâu ạ?",
                question.getId());
    }

    private LessonChat saveChat(LessonChatRepository chatRepository, Lesson lesson, User user, String content, String parentId) {
        LessonChat chat = new LessonChat();
        chat.setLesson(lesson);
        chat.setUser(user);
        chat.setUserName(user.getFullName());
        chat.setContent(content);
        if (parentId != null) {
            chatRepository.findById(parentId).ifPresent(chat::setParent);
        }
        return chatRepository.save(chat);
    }

    /** Tìm lại hội thoại đã có (do cả nhóm tự test qua UI) hoặc tạo mới — chỉ thêm tin nhắn MẪU
     * nếu hội thoại này CHƯA có tin nhắn nào mang {@link #SEED_MARKER}, để chạy lại an toàn. */
    /** @return {@code true} nếu VỪA seed tin nhắn mới (chưa từng seed trước đó cho hội thoại này). */
    private boolean seedConversation(
            ConversationRepository conversationRepository, MessageRepository messageRepository,
            User instructor, User student, String... contents) {
        Conversation conversation = conversationRepository
                .findByStudent_IdAndInstructor_Id(student.getId(), instructor.getId())
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setStudent(student);
                    c.setInstructor(instructor);
                    return conversationRepository.save(c);
                });

        boolean alreadySeeded = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversation.getId())
                .stream().anyMatch(m -> m.getContent().startsWith(SEED_MARKER));
        if (alreadySeeded) {
            return false;
        }

        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null) {
                continue;
            }
            User sender = i % 2 == 0 ? student : instructor;
            Message message = new Message();
            message.setConversation(conversation);
            message.setSender(sender);
            message.setContent((i == 0 ? SEED_MARKER + " " : "") + contents[i]);
            message.setIsRead(true);
            messageRepository.save(message);
        }
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        return true;
    }

    /** @return Announcement VỪA seed, hoặc {@code null} nếu khóa này đã có announcement mẫu rồi. */
    private Announcement seedAnnouncement(AnnouncementRepository announcementRepository, Course course) {
        boolean alreadySeeded = announcementRepository.findByCourse_IdOrderByCreatedAtDesc(course.getId()).stream()
                .anyMatch(a -> a.getTitle().startsWith(SEED_MARKER));
        if (alreadySeeded) {
            return null;
        }
        Announcement announcement = new Announcement();
        announcement.setCourse(course);
        announcement.setTitle(SEED_MARKER + " Lịch nghỉ lễ tuần sau");
        announcement.setContent("Chào cả lớp, tuần sau nghỉ lễ nên bài giảng mới sẽ được đăng chậm hơn thường lệ, mong các em thông cảm.");
        return announcementRepository.save(announcement);
    }
}
