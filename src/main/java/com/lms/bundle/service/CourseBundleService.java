package com.lms.bundle.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.bundle.dto.CourseBundleDto;
import com.lms.bundle.entity.CourseBundle;
import com.lms.bundle.repository.CourseBundleRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseBundleService {

    private final CourseBundleRepository bundleRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional
    public CourseBundleDto.Res createBundle(String instructorEmail, CourseBundleDto.CreateReq req) {
        User instructor = userRepository.findByEmail(instructorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", instructorEmail));

        CourseBundle bundle = new CourseBundle();
        bundle.setInstructor(instructor);
        bundle.setTitle(req.title());
        bundle.setDescription(req.description());
        bundle.setDiscountPercent(req.discountPercent() != null ? req.discountPercent() : 0);
        
        if (bundle.getDiscountPercent() < 0 || bundle.getDiscountPercent() >= 100) {
            throw new BusinessRuleViolationException("Discount percent must be between 0 and 99");
        }

        List<Course> courses = validateAndLoadCourses(req.courseIds(), instructor.getId());
        bundle.setCourses(courses);

        bundle = bundleRepository.save(bundle);
        return mapToRes(bundle);
    }

    @Transactional
    public CourseBundleDto.Res updateBundle(Long bundleId, String instructorEmail, CourseBundleDto.UpdateReq req) {
        CourseBundle bundle = bundleRepository.findByIdWithCourses(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle", bundleId));

        if (!bundle.getInstructor().getEmail().equals(instructorEmail)) {
            throw new BusinessRuleViolationException("Bạn không có quyền sửa gói khóa học này");
        }

        bundle.setTitle(req.title());
        bundle.setDescription(req.description());
        bundle.setDiscountPercent(req.discountPercent() != null ? req.discountPercent() : bundle.getDiscountPercent());
        bundle.setIsActive(req.isActive() != null ? req.isActive() : bundle.getIsActive());

        if (req.courseIds() != null) {
            List<Course> courses = validateAndLoadCourses(req.courseIds(), bundle.getInstructor().getId());
            bundle.setCourses(courses);
        }

        return mapToRes(bundleRepository.save(bundle));
    }

    private List<Course> validateAndLoadCourses(List<Long> courseIds, Long instructorId) {
        if (courseIds == null || courseIds.size() < 2) {
            throw new BusinessRuleViolationException("Gói phải có ít nhất 2 khóa học");
        }
        
        List<Course> courses = courseRepository.findAllById(courseIds);
        if (courses.size() != courseIds.size()) {
            throw new BusinessRuleViolationException("Một số khóa học không tồn tại");
        }
        
        for (Course c : courses) {
            if (!c.getInstructor().getId().equals(instructorId)) {
                throw new BusinessRuleViolationException("Khóa học " + c.getTitle() + " không thuộc sở hữu của bạn");
            }
            if (c.getStatus() != CourseStatus.PUBLISHED) {
                throw new BusinessRuleViolationException("Khóa học " + c.getTitle() + " chưa được xuất bản (PUBLISHED)");
            }
            if (c.getPrice() == null || c.getPrice().compareTo(BigDecimal.ZERO) <= 0 || Boolean.TRUE.equals(c.getIsFree())) {
                throw new BusinessRuleViolationException("Khóa học " + c.getTitle() + " là khóa học miễn phí. Không được thêm khóa học miễn phí vào gói");
            }
        }
        return courses;
    }

    @Transactional(readOnly = true)
    public Page<CourseBundleDto.Res> getBundlesByInstructor(String email, Pageable pageable) {
        User instructor = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", email));
        
        return bundleRepository.findByInstructorId(instructor.getId(), pageable).map(this::mapToRes);
    }

    @Transactional(readOnly = true)
    public List<CourseBundleDto.Res> getActiveBundlesForCourse(Long courseId) {
        List<CourseBundle> bundles = bundleRepository.findActiveBundlesByCourseId(courseId);
        return bundles.stream().map(this::mapToRes).collect(Collectors.toList());
    }

    /** Gộp tra bundle cho nhiều courseId (giỏ hàng) trong 1 lần gọi — tránh N+1 request. */
    @Transactional(readOnly = true)
    public List<CourseBundleDto.Res> getActiveBundlesForCourses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        List<CourseBundle> bundles = bundleRepository.findActiveBundlesByCourseIds(courseIds);
        return bundles.stream().map(this::mapToRes).collect(Collectors.toList());
    }

    private CourseBundleDto.Res mapToRes(CourseBundle bundle) {
        BigDecimal originalPrice = BigDecimal.ZERO;
        List<CourseBundleDto.CourseItemRes> courseItems = bundle.getCourses().stream().map(c -> {
            return new CourseBundleDto.CourseItemRes(
                    c.getId(),
                    c.getTitle(),
                    c.getThumbnailUrl(),
                    c.getPrice()
            );
        }).collect(Collectors.toList());

        for (CourseBundleDto.CourseItemRes item : courseItems) {
            originalPrice = originalPrice.add(item.price());
        }

        BigDecimal discountAmount = originalPrice.multiply(new BigDecimal(bundle.getDiscountPercent())).divide(new BigDecimal(100));
        BigDecimal finalPrice = originalPrice.subtract(discountAmount);

        return new CourseBundleDto.Res(
                bundle.getId(),
                bundle.getTitle(),
                bundle.getDescription(),
                bundle.getDiscountPercent(),
                bundle.getIsActive(),
                bundle.getCreatedAt(),
                bundle.getUpdatedAt(),
                courseItems,
                originalPrice,
                discountAmount,
                finalPrice
        );
    }
}
