package com.lms.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình Backblaze B2 (tương thích S3) — Giai đoạn 4, UC34/UC35.
 *
 * <p>{@code endpoint} dạng {@code s3.us-east-005.backblazeb2.com} (không có {@code https://}).
 */
@ConfigurationProperties(prefix = "lms.storage")
public class StorageProperties {

    private String b2BucketName;
    private String b2KeyId;
    private String b2ApplicationKey;
    private String b2Endpoint;

    public String getB2BucketName() {
        return b2BucketName;
    }

    public void setB2BucketName(String b2BucketName) {
        this.b2BucketName = b2BucketName;
    }

    public String getB2KeyId() {
        return b2KeyId;
    }

    public void setB2KeyId(String b2KeyId) {
        this.b2KeyId = b2KeyId;
    }

    public String getB2ApplicationKey() {
        return b2ApplicationKey;
    }

    public void setB2ApplicationKey(String b2ApplicationKey) {
        this.b2ApplicationKey = b2ApplicationKey;
    }

    public String getB2Endpoint() {
        return b2Endpoint;
    }

    public void setB2Endpoint(String b2Endpoint) {
        this.b2Endpoint = b2Endpoint;
    }
}
