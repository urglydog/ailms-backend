package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.AttachmentSide;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.InstructorDecision;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.PaymentDisputeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class InstructorDisputeServiceTest {

    private static final String OWNER = "gv@lms.local";

    @Mock private PaymentDisputeRepository disputeRepository;
    @Mock private UserRepository userRepository;
    @Mock private DisputeService disputeService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private InstructorDisputeService service;

    private PaymentDispute dispute;
    private User admin;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setId(3L);
        instructor.setEmail(OWNER);
        Course course = new Course();
        course.setId(10L);
        course.setInstructor(instructor);
        Payment payment = new Payment();
        payment.setTxnRef("txn-100");
        payment.setCourse(course);
        dispute = new PaymentDispute();
        dispute.setId(500L);
        dispute.setPayment(payment);
        dispute.setStatus(DisputeStatus.OPEN);

        admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);

        lenient().when(disputeRepository.findById(500L)).thenReturn(Optional.of(dispute));
        lenient().when(disputeRepository.save(any(PaymentDispute.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
    }

    private DisputeDto.InstructorRespondReq respondReq(InstructorDecision decision) {
        return new DisputeDto.InstructorRespondReq(decision, "  Học viên đã học hết chương 3  ");
    }

    @Test
    void respond_agree_savesTrimmedResponseAttachesEvidenceAndNotifiesAdmins() {
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "a.png", "image/png", new byte[]{1}));

        service.respond(OWNER, 500L, respondReq(InstructorDecision.AGREE_REFUND), files);

        assertThat(dispute.getInstructorDecision()).isEqualTo(InstructorDecision.AGREE_REFUND);
        assertThat(dispute.getInstructorResponse()).isEqualTo("Học viên đã học hết chương 3");
        assertThat(dispute.getInstructorRespondedAt()).isNotNull();
        verify(disputeService).attachEvidence(dispute, files, AttachmentSide.INSTRUCTOR);
        verify(notificationService).notify(eq(1L), eq("DISPUTE_INSTRUCTOR_RESPONDED"), anyString(), anyString(), anyString());
    }

    @Test
    void respond_byAnotherInstructor_isForbidden() {
        assertThatThrownBy(() -> service.respond("khac@lms.local", 500L, respondReq(InstructorDecision.OBJECT), null))
                .isInstanceOf(AccessDeniedDomainException.class);

        assertThat(dispute.getInstructorDecision()).isNull();
        verify(disputeService, never()).attachEvidence(any(), any(), any());
    }

    @Test
    void respond_onlyAgreeOrObjectAreValidDecisions() {
        assertThatThrownBy(() -> service.respond(OWNER, 500L, respondReq(InstructorDecision.NO_RESPONSE), null))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(disputeRepository, never()).save(any(PaymentDispute.class));
    }

    @Test
    void respond_twice_isRejected() {
        service.respond(OWNER, 500L, respondReq(InstructorDecision.OBJECT), null);

        assertThatThrownBy(() -> service.respond(OWNER, 500L, respondReq(InstructorDecision.AGREE_REFUND), null))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(dispute.getInstructorDecision()).isEqualTo(InstructorDecision.OBJECT);
    }

    @Test
    void respond_afterAdminAlreadyResolved_isRejected() {
        dispute.setStatus(DisputeStatus.RESOLVED_REFUNDED);

        assertThatThrownBy(() -> service.respond(OWNER, 500L, respondReq(InstructorDecision.OBJECT), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void respond_lateAfterJobMarkedNoResponse_isStillAcceptedWhileAdminHasNotDecided() {
        dispute.setInstructorDecision(InstructorDecision.NO_RESPONSE);

        service.respond(OWNER, 500L, respondReq(InstructorDecision.OBJECT), null);

        assertThat(dispute.getInstructorDecision()).isEqualTo(InstructorDecision.OBJECT);
    }

    @Test
    void list_filtersByStatusAndCourse_andOnlyMapsOwnedDisputes() {
        PaymentDispute resolved = new PaymentDispute();
        resolved.setId(501L);
        resolved.setStatus(DisputeStatus.RESOLVED_REJECTED);
        resolved.setPayment(dispute.getPayment());
        when(disputeRepository.findByPayment_Course_Instructor_EmailOrderByCreatedAtDesc(OWNER))
                .thenReturn(List.of(dispute, resolved));
        DisputeDto.InstructorRes stub = new DisputeDto.InstructorRes(500L, "txn-100", 10L, "c", "n", null, null, null,
                "r", null, null, null, null, DisputeStatus.OPEN, null, List.of(), List.of(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        when(disputeService.toInstructorRes(dispute)).thenReturn(stub);

        assertThat(service.list(OWNER, DisputeStatus.OPEN, null)).containsExactly(stub);
        assertThat(service.list(OWNER, DisputeStatus.OPEN, 999L)).isEmpty();
        verify(disputeService, times(1)).toInstructorRes(any(PaymentDispute.class));
    }
}
