package com.lms.common.storage;

import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class B2StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(B2StorageService.class);

    private final S3Client s3Client;
    private final StorageProperties props;

    @Override
    public String upload(String key, InputStream content, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.getB2BucketName())
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
        return "https://" + props.getB2BucketName() + "." + props.getB2Endpoint() + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getB2BucketName())
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("Khong the xoa object '{}' tren B2: {}", key, e.getMessage());
        }
    }
}
