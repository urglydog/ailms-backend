package com.lms.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import lombok.Getter;

/**
 * Cấu hình giao tiếp với AI Worker (nội bộ Docker).
 *
 * <p>AI Worker là internal service — FE KHÔNG gọi trực tiếp vào đây.
 * Backend đóng vai proxy: xác thực JWT, kiểm quota, rồi forward request.
 */
@Configuration
@Getter
public class AiWorkerConfig {

    @Value("${ai.worker.base-url:http://ai-api:8000}")
    private String baseUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
