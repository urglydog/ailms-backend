package com.lms.common.storage;

import java.io.InputStream;

/**
 * Trừu tượng lưu trữ file (Giai đoạn 4 — UC34, UC35). Implementation duy nhất hiện tại là
 * {@link B2StorageService} (Backblaze B2), nhưng tách interface để service không phụ thuộc
 * trực tiếp AWS SDK.
 */
public interface StorageService {

    /**
     * Upload và trả về URL công khai của object. Bucket B2 phải ở chế độ Public trên dashboard
     * Backblaze — chưa có lớp CDN/signed-URL ở giai đoạn dev này.
     */
    String upload(String key, InputStream content, long contentLength, String contentType);

    /** Best-effort — lỗi khi xoá không nên chặn thao tác nghiệp vụ chính (ví dụ xoá bản ghi DB). */
    void delete(String key);

    /**
     * Trích object key từ URL công khai do {@link #upload} trả về (dạng
     * {@code https://{bucket}.{endpoint}/{key}}) — dùng khi cần xoá lại object chỉ từ URL đã lưu
     * trong DB (video/tài liệu/ảnh bìa), không cần lưu key riêng.
     */
    static String extractKeyFromUrl(String url) {
        int hostEnd = url.indexOf('/', "https://".length());
        return hostEnd >= 0 ? url.substring(hostEnd + 1) : url;
    }
}
