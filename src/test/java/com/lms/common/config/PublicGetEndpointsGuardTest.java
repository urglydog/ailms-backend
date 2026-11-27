package com.lms.common.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chốt chặn (26/11/2026): {@code SecurityConfig.PUBLIC_GET_ENDPOINTS} dùng ký tự đại diện (vd {@code /api/v1/lessons/**})
 * nên MỌI endpoint GET mới đặt dưới các đường đó sẽ tự động CÔNG KHAI nếu quên {@code @PreAuthorize}. Đã từng làm lộ
 * lịch sử chat bài học và gây lỗi 500 cho khách. Test này buộc mỗi endpoint GET nằm trong vùng công khai phải
 * hoặc có {@code @PreAuthorize}, hoặc được liệt kê CÓ CHỦ Ý trong {@link #INTENTIONALLY_PUBLIC}.
 */
class PublicGetEndpointsGuardTest {

    /** Dạng "TênLớp#tênPhươngThức" — chỉ thêm vào đây sau khi đã xác nhận endpoint thật sự công khai theo nghiệp vụ. */
    private static final Set<String> INTENTIONALLY_PUBLIC = Set.of(
            // danh mục / khoá học / đánh giá / gói combo / nổi bật — trang chủ & trang khoá học cho khách
            "CategoryController#getAll",
            "CoursePublicController#search",
            "CoursePublicController#getBySlug",
            "CoursePublicController#getLessonPlayer", // chỉ bài Preview, service kiểm isPreview
            "CourseReviewController#listForCourse",
            "CourseBundleController#getBundlesForCourse",
            "CourseBundleController#getBundlesForCourses",
            "FeaturedCoursesController#featured",
            // live công khai (BR-LIVE-01) và danh mục giọng đọc
            "LiveViewController#listForCourse",
            "LiveViewController#listPublicFeed",
            "LiveViewController#view",
            "LiveLanguageTrackController#listActive",
            "VoiceOptionController#getVoiceOptions",
            // xác minh chứng chỉ, hoá đơn theo token, bảng xếp hạng, banner hệ thống, hồ sơ công khai
            "CertificateController#verify",
            "PublicInvoiceController#open",
            "RankingController#getLeaderboard",
            "SystemAnnouncementController#getActiveBanner",
            "UserController#getPublicProfile"
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Test
    void everyGetEndpointInsidePublicZone_isProtectedOrExplicitlyAllowed() throws Exception {
        Field f = SecurityConfig.class.getDeclaredField("PUBLIC_GET_ENDPOINTS");
        f.setAccessible(true);
        String[] publicPatterns = (String[]) f.get(null);

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> violations = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.lms")) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            boolean classGuarded = AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class);
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String basePath = base != null && base.value().length > 0 ? base.value()[0] : "";
            for (Method m : controller.getDeclaredMethods()) {
                GetMapping get = AnnotatedElementUtils.findMergedAnnotation(m, GetMapping.class);
                if (get == null) continue;
                String sub = get.value().length > 0 ? get.value()[0] : (get.path().length > 0 ? get.path()[0] : "");
                // {id:\d+} -> 1, {x} -> 1 để so khớp mẫu đường dẫn
                String path = (basePath + sub).replaceAll("\\{[^}]*\\}", "1");
                boolean publicZone = Arrays.stream(publicPatterns).anyMatch(p -> MATCHER.match(p, path));
                if (!publicZone) continue;
                boolean guarded = classGuarded || AnnotatedElementUtils.hasAnnotation(m, PreAuthorize.class);
                String id = controller.getSimpleName() + "#" + m.getName();
                if (!guarded && !INTENTIONALLY_PUBLIC.contains(id)) {
                    violations.add(id + "  (GET " + basePath + sub + ")");
                }
            }
        }
        assertThat(new TreeSet<>(violations))
                .as("Endpoint GET nằm trong vùng công khai nhưng thiếu @PreAuthorize và chưa được liệt kê có chủ ý")
                .isEmpty();
    }
}
