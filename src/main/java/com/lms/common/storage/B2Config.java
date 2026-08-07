package com.lms.common.storage;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Backblaze B2 tương thích S3 — {@code S3Client} chỉ cần trỏ {@code endpointOverride} vào
 * endpoint B2 ({@code s3.<region>.backblazeb2.com}) và bật path-style, không cần SDK riêng.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class B2Config {

    private static final Pattern REGION_PATTERN = Pattern.compile("^s3\\.([a-z0-9-]+)\\.backblazeb2\\.com$");

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .region(Region.of(extractRegion(props.getB2Endpoint())))
                .endpointOverride(URI.create("https://" + props.getB2Endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getB2KeyId(), props.getB2ApplicationKey())))
                .forcePathStyle(true)
                .build();
    }

    private static String extractRegion(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            // Cho phép app khởi động khi chưa cấu hình B2 (ví dụ chạy test) — region giả,
            // bean chỉ thực sự được gọi khi có request upload.
            return "us-east-1";
        }
        Matcher matcher = REGION_PATTERN.matcher(endpoint);
        return matcher.matches() ? matcher.group(1) : "us-east-1";
    }
}
