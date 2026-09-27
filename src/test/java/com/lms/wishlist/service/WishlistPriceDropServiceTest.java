package com.lms.wishlist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.service.EmailService;
import com.lms.catalog.entity.Course;
import com.lms.common.service.NotificationService;
import com.lms.wishlist.entity.WishlistItem;
import com.lms.wishlist.repository.WishlistItemRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** (26/09/2026, tính năng mới) — báo giảm giá wishlist qua job nền Redis. */
@ExtendWith(MockitoExtension.class)
class WishlistPriceDropServiceTest {

    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;

    private WishlistPriceDropService service;

    private Course course;

    @BeforeEach
    void setUp() {
        service = new WishlistPriceDropService(wishlistItemRepository, notificationService, emailService,
                redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "queueKey", "lms:wishlist-price-drop:jobs");

        course = new Course();
        course.setId(10L);
        course.setTitle("Lập trình Python");
        course.setSlug("lap-trinh-python");
    }

    @Test
    void enqueuePriceDrop_pushesJsonPayloadToRedisQueue() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        service.enqueuePriceDrop(course, new BigDecimal("500000"), new BigDecimal("350000"));

        verify(listOperations).leftPush(eq("lms:wishlist-price-drop:jobs"), contains("\"courseId\":10"));
    }

    @Test
    void processJob_notifiesAndEmailsEveryWishlister_andUpdatesPriceSnapshot() {
        User student1 = new User();
        student1.setId(1L);
        student1.setEmail("student1@lms.local");
        User student2 = new User();
        student2.setId(2L);
        student2.setEmail("student2@lms.local");

        WishlistItem item1 = new WishlistItem();
        item1.setUser(student1);
        item1.setCourse(course);
        item1.setPriceAtAdd(new BigDecimal("500000"));
        WishlistItem item2 = new WishlistItem();
        item2.setUser(student2);
        item2.setCourse(course);
        item2.setPriceAtAdd(new BigDecimal("500000"));

        when(wishlistItemRepository.findByCourse_Id(10L)).thenReturn(List.of(item1, item2));

        var job = new WishlistPriceDropService.PriceDropJob(10L, "Lập trình Python", "lap-trinh-python",
                new BigDecimal("500000"), new BigDecimal("350000"));
        service.processJob(job);

        verify(notificationService).notify(eq(1L), eq("WISHLIST_PRICE_DROP"), anyString(), anyString(), eq("/courses/lap-trinh-python"));
        verify(notificationService).notify(eq(2L), eq("WISHLIST_PRICE_DROP"), anyString(), anyString(), eq("/courses/lap-trinh-python"));
        verify(emailService).sendPriceDropEmail("student1@lms.local", "Lập trình Python", "lap-trinh-python",
                new BigDecimal("500000"), new BigDecimal("350000"));
        verify(emailService).sendPriceDropEmail("student2@lms.local", "Lập trình Python", "lap-trinh-python",
                new BigDecimal("500000"), new BigDecimal("350000"));
        verify(wishlistItemRepository, times(2)).save(any(WishlistItem.class));
    }

    /** 1 user gửi thông báo lỗi không được chặn xử lý các user còn lại trong cùng batch. */
    @Test
    void processJob_oneNotifyFails_stillProcessesRemainingUsers() {
        User student1 = new User();
        student1.setId(1L);
        student1.setEmail("student1@lms.local");
        User student2 = new User();
        student2.setId(2L);
        student2.setEmail("student2@lms.local");

        WishlistItem item1 = new WishlistItem();
        item1.setUser(student1);
        item1.setCourse(course);
        WishlistItem item2 = new WishlistItem();
        item2.setUser(student2);
        item2.setCourse(course);

        when(wishlistItemRepository.findByCourse_Id(10L)).thenReturn(List.of(item1, item2));
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(notificationService).notify(eq(1L), anyString(), anyString(), anyString(), any());

        var job = new WishlistPriceDropService.PriceDropJob(10L, "Lập trình Python", "lap-trinh-python",
                new BigDecimal("500000"), new BigDecimal("350000"));
        service.processJob(job);

        verify(emailService).sendPriceDropEmail(eq("student1@lms.local"), anyString(), anyString(), any(), any());
        verify(emailService).sendPriceDropEmail(eq("student2@lms.local"), anyString(), anyString(), any(), any());
        verify(notificationService).notify(eq(2L), eq("WISHLIST_PRICE_DROP"), anyString(), anyString(), any());
    }
}
