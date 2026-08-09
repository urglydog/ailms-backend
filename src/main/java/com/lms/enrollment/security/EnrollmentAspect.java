package com.lms.enrollment.security;

import com.lms.common.annotation.RequireEnrollment;
import com.lms.common.exception.AccessDeniedDomainException;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class EnrollmentAspect {

    private final EnrollmentSecurity enrollmentSecurity;

    @Before("@annotation(requireEnrollment)")
    public void checkEnrollment(JoinPoint joinPoint, RequireEnrollment requireEnrollment) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            throw new AccessDeniedDomainException("Yêu cầu đăng nhập.");
        }
        
        String email = auth.getName();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        
        Long lessonId = null;
        Long courseId = null;
        
        for (int i = 0; i < parameterNames.length; i++) {
            if ("lessonId".equals(parameterNames[i]) && args[i] instanceof Long) {
                lessonId = (Long) args[i];
            } else if ("courseId".equals(parameterNames[i]) && args[i] instanceof Long) {
                courseId = (Long) args[i];
            }
        }
        
        if (lessonId != null) {
            boolean canAccess = enrollmentSecurity.canAccessLesson(email, lessonId, requireEnrollment.allowPreview());
            if (!canAccess) {
                throw new AccessDeniedDomainException("BR-ENROLL-02: Bạn chưa sở hữu bài học này hoặc bài học không cho phép preview.");
            }
        } else if (courseId != null) {
            boolean canAccess = enrollmentSecurity.canAccessCourse(email, courseId);
            if (!canAccess) {
                throw new AccessDeniedDomainException("BR-ENROLL-02: Bạn chưa sở hữu khóa học này.");
            }
        } else {
            throw new IllegalStateException("@RequireEnrollment yêu cầu tham số 'lessonId' hoặc 'courseId' trong method signature.");
        }
    }
}
