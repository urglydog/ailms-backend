package com.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động Core Web Service của AI-Powered LMS.
 *
 * <p>{@code @EnableJpaAuditing} làm cho {@code @CreatedDate}/{@code @LastModifiedDate}
 * trong {@link com.lms.common.entity.BaseEntity} tự điền — thiếu annotation này thì
 * {@code created_at} sẽ null và vi phạm ràng buộc NOT NULL của schema.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class LmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmsApplication.class, args);
    }
}
