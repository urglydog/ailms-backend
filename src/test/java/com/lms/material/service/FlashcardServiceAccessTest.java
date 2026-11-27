package com.lms.material.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.dto.FlashcardDto;
import com.lms.material.entity.Flashcard;
import com.lms.material.entity.FlashcardDeck;
import com.lms.material.entity.FlashcardReview;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.repository.FlashcardRepository;
import com.lms.material.repository.FlashcardReviewRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm quyền khi ôn flashcard: chỉ giảng viên của khoá, hoặc học viên CÒN ghi danh (và là chủ bộ thẻ cá nhân / bộ thẻ chính thức) mới xem thẻ
 * và ghi tiến độ ôn được. Học viên đã bị thu hồi ghi danh (hoàn tiền) hoặc người lạ biết mã thẻ đều bị từ chối.
 */
@ExtendWith(MockitoExtension.class)
class FlashcardServiceAccessTest {

    @Mock private FlashcardRepository flashcardRepository;
    @Mock private FlashcardDeckRepository flashcardDeckRepository;
    @Mock private FlashcardReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    @InjectMocks private FlashcardService service;

    private User instructor;
    private User student;
    private User otherStudent;
    private Course course;
    private MaterialGeneration generation;
    private FlashcardDeck deck;
    private Flashcard card;

    private static User user(long id, String email) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        return u;
    }

    @BeforeEach
    void setUp() {
        instructor = user(1L, "gv@x.vn");
        student = user(2L, "hv@x.vn");
        otherStudent = user(3L, "khac@x.vn");

        course = new Course();
        ReflectionTestUtils.setField(course, "id", 10L);
        course.setInstructor(instructor);

        generation = new MaterialGeneration();
        generation.setCourse(course);
        generation.setUser(student); // bộ thẻ cá nhân của `student`

        deck = new FlashcardDeck();
        ReflectionTestUtils.setField(deck, "id", 100L);
        deck.setMaterialGeneration(generation);
        deck.setIsOfficial(false);

        card = new Flashcard();
        ReflectionTestUtils.setField(card, "id", 1000L);
        card.setFlashcardDeck(deck);
    }

    @Test
    void reviewCard_revokedStudent_isDenied() {
        when(userRepository.findByEmail("hv@x.vn")).thenReturn(Optional.of(student));
        when(flashcardRepository.findById(1000L)).thenReturn(Optional.of(card));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(2L, 10L)).thenReturn(false);

        assertThrows(AccessDeniedDomainException.class,
                () -> service.reviewCard("hv@x.vn", 1000L, new FlashcardDto.ReviewReq(3)));
        verify(reviewRepository, never()).save(any(FlashcardReview.class));
    }

    @Test
    void reviewCard_enrolledOwner_savesReview() {
        when(userRepository.findByEmail("hv@x.vn")).thenReturn(Optional.of(student));
        when(flashcardRepository.findById(1000L)).thenReturn(Optional.of(card));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(2L, 10L)).thenReturn(true);
        when(reviewRepository.findByUser_IdAndFlashcard_Id(2L, 1000L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(FlashcardReview.class))).thenAnswer(inv -> inv.getArgument(0));

        FlashcardDto.ReviewRes res = service.reviewCard("hv@x.vn", 1000L, new FlashcardDto.ReviewReq(3));

        assertEquals(1000L, res.flashcardId());
        verify(reviewRepository).save(any(FlashcardReview.class));
    }

    @Test
    void deckStudy_enrolledStudentCannotReadSomeoneElsesPersonalDeck() {
        when(userRepository.findByEmail("khac@x.vn")).thenReturn(Optional.of(otherStudent));
        when(flashcardDeckRepository.findById(100L)).thenReturn(Optional.of(deck));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(3L, 10L)).thenReturn(true);

        assertThrows(AccessDeniedDomainException.class, () -> service.getDeckCardsWithReview("khac@x.vn", 100L));
        verify(flashcardRepository, never()).findByFlashcardDeck_Id(any());
    }

    @Test
    void deckStudy_enrolledStudentCanReadOfficialDeck() {
        deck.setIsOfficial(true);
        when(userRepository.findByEmail("khac@x.vn")).thenReturn(Optional.of(otherStudent));
        when(flashcardDeckRepository.findById(100L)).thenReturn(Optional.of(deck));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(3L, 10L)).thenReturn(true);
        when(flashcardRepository.findByFlashcardDeck_Id(100L)).thenReturn(List.of(card));
        when(reviewRepository.findByUser_IdAndFlashcard_FlashcardDeck_Id(3L, 100L)).thenReturn(List.of());

        assertEquals(1, service.getDeckCardsWithReview("khac@x.vn", 100L).size());
    }

    @Test
    void deckStudy_instructorOfCourseIsAllowedWithoutEnrollment() {
        when(userRepository.findByEmail("gv@x.vn")).thenReturn(Optional.of(instructor));
        when(flashcardDeckRepository.findById(100L)).thenReturn(Optional.of(deck));
        when(flashcardRepository.findByFlashcardDeck_Id(100L)).thenReturn(List.of());
        when(reviewRepository.findByUser_IdAndFlashcard_FlashcardDeck_Id(1L, 100L)).thenReturn(List.of());

        assertEquals(0, service.getDeckCardsWithReview("gv@x.vn", 100L).size());
        verify(enrollmentRepository, never()).existsByUser_IdAndCourse_Id(any(), any());
    }
}
