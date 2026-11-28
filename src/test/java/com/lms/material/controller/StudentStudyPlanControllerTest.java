package com.lms.material.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.dto.StudyPlanDto;
import com.lms.material.dto.StudyPlanReq;
import com.lms.material.service.StudentStudyPlanService;
import java.security.Principal;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lộ trình học AI: chỉ người còn ghi danh khoá mới xem/tạo/dời được (kể cả tài khoản role INSTRUCTOR đang học khoá của người khác). */
@ExtendWith(MockitoExtension.class)
class StudentStudyPlanControllerTest {

    @Mock private StudentStudyPlanService studyPlanService;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    @InjectMocks private StudentStudyPlanController controller;

    private final Principal principal = () -> "hv@x.vn";

    @BeforeEach
    void setUp() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 5L);
        user.setEmail("hv@x.vn");
        when(userRepository.findByEmail("hv@x.vn")).thenReturn(Optional.of(user));
    }

    @Test
    void generatePlan_notEnrolled_isDenied() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(5L, 92L)).thenReturn(false);

        assertThrows(AccessDeniedDomainException.class, () -> controller.generatePlan(92L, mock(StudyPlanReq.class), principal));
        verify(studyPlanService, never()).generatePlan(any(), any(), any());
    }

    @Test
    void generatePlan_enrolled_callsService() {
        StudyPlanReq req = mock(StudyPlanReq.class);
        StudyPlanDto plan = mock(StudyPlanDto.class);
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(5L, 92L)).thenReturn(true);
        when(studyPlanService.generatePlan(5L, 92L, req)).thenReturn(plan);

        assertEquals(plan, controller.generatePlan(92L, req, principal).getBody());
    }

    @Test
    void getPlan_notEnrolled_isDenied() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(5L, 92L)).thenReturn(false);

        assertThrows(AccessDeniedDomainException.class, () -> controller.getPlan(92L, principal));
        verify(studyPlanService, never()).getPlan(any(), any());
    }
}
