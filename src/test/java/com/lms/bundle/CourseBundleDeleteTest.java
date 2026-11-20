package com.lms.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.bundle.entity.CourseBundle;
import com.lms.bundle.repository.CourseBundleRepository;
import com.lms.bundle.service.CourseBundleService;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.payment.repository.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseBundleDeleteTest {

    @Mock private CourseBundleRepository bundleRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @InjectMocks private CourseBundleService service;

    private CourseBundle bundle;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setEmail("gv@gmail.com");
        bundle = new CourseBundle();
        bundle.setId(5L);
        bundle.setInstructor(owner);
        when(bundleRepository.findByIdWithCourses(5L)).thenReturn(Optional.of(bundle));
    }

    @Test
    void deleteBundle_neverPurchasedIsDeletedForGood() {
        when(paymentRepository.existsByBundle_Id(5L)).thenReturn(false);

        assertThat(service.deleteBundle(5L, "gv@gmail.com")).isTrue();

        verify(bundleRepository).delete(bundle);
    }

    @Test
    void deleteBundle_alreadyPurchasedIsArchivedNotDeleted() {
        when(paymentRepository.existsByBundle_Id(5L)).thenReturn(true);

        assertThat(service.deleteBundle(5L, "gv@gmail.com")).isFalse();

        assertThat(bundle.getIsActive()).isFalse();
        assertThat(bundle.getArchivedAt()).isNotNull();
        verify(bundleRepository, never()).delete(any(CourseBundle.class));
    }

    @Test
    void deleteBundle_onlyTheOwnerMay() {
        assertThatThrownBy(() -> service.deleteBundle(5L, "khac@gmail.com")).isInstanceOf(BusinessRuleViolationException.class);
        verify(bundleRepository, never()).delete(any(CourseBundle.class));
    }
}
