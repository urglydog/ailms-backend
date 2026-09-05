package com.lms.common.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.common.exception.InvalidRequestException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Trích videoId + kiểm nguồn YouTube còn công khai (UC34, BR-CHUNK-01) qua YouTube Data API v3
 * chính thức — {@code videos.list?part=contentDetails,status}.
 *
 * <p>BUG THẬT (05/09/2026): bản trước gọi thẳng {@code yt-dlp} (scraping, không phải API chính
 * thức) như 1 tiến trình con để lấy thời lượng — hoạt động bình thường ở máy dev nhưng SAU KHI
 * DEPLOY lên VPS thì lỗi "Video YouTube không tồn tại hoặc không công khai" với MỌI link, kể cả
 * link chắc chắn public. Xác nhận thật bằng cách SSH vào server chạy trực tiếp lệnh, thấy:
 * {@code ERROR: [youtube] <id>: Sign in to confirm you're not a bot} — YouTube chặn theo dải IP
 * datacenter/VPS, không liên quan gì tới link sai hay thiếu {@code yt-dlp}. Đây là kiểu lỗi
 * "cat-and-mouse" liên tục tái diễn với mọi công cụ scraping YouTube chạy trên cloud, nên chuyển
 * hẳn sang kênh Google CHO PHÉP chính thức (Data API v3, cần API key free tier) thay vì tiếp tục
 * vá bằng flag/cookie cho {@code yt-dlp} — ổn định lâu dài hơn cho đúng nhu cầu ở đây (chỉ cần
 * biết video có tồn tại/công khai không + thời lượng bao nhiêu, KHÔNG cần tải nội dung video).
 *
 * <p>Lưu ý: việc TẢI nội dung video thật cho pipeline lồng tiếng (ai-worker, UC19) vẫn dùng
 * {@code yt-dlp} — Data API v3 không cấp URL media trực tiếp, không thay thế được việc tải. Bước
 * đó vẫn có thể gặp lại lỗi bot-detection tương tự trên cùng VPS, là vấn đề KHÁC, chưa xử lý ở đây.
 */
@Service
public class YoutubeMetadataService {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private static final String VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public YoutubeMetadataService(RestTemplate restTemplate, @Value("${youtube.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    public String extractVideoId(String url) {
        Matcher matcher = URL_PATTERN.matcher(url == null ? "" : url);
        if (!matcher.find()) {
            throw new InvalidRequestException("URL YouTube không hợp lệ");
        }
        return matcher.group(1);
    }

    /** Gọi Data API v3 thật — video không tồn tại HOẶC không công khai đều trả về {@code items}
     * RỖNG (Google không phân biệt 2 trường hợp này khi gọi bằng API key thường, không phải OAuth
     * của chính chủ video) — khớp nguyên vẹn thông điệp lỗi gộp chung đã có từ trước, không cần
     * đổi hành vi phía {@code LessonService}/FE. */
    public int fetchDurationSec(String videoId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new InvalidRequestException(
                    "Chưa cấu hình YOUTUBE_API_KEY — không kiểm tra được video YouTube");
        }

        String url = UriComponentsBuilder.fromUriString(VIDEOS_ENDPOINT)
                .queryParam("part", "contentDetails,status")
                .queryParam("id", videoId)
                .queryParam("key", apiKey)
                .toUriString();

        JsonNode root;
        try {
            root = restTemplate.getForObject(url, JsonNode.class);
        } catch (RestClientException e) {
            throw new InvalidRequestException("Không kiểm tra được video YouTube: " + e.getMessage());
        }

        JsonNode items = root == null ? null : root.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            throw new InvalidRequestException("Video YouTube không tồn tại hoặc không công khai");
        }

        JsonNode durationNode = items.get(0).path("contentDetails").path("duration");
        if (!durationNode.isTextual()) {
            throw new InvalidRequestException("Không đọc được thời lượng video YouTube");
        }
        try {
            // ISO 8601 (vd "PT1H2M10S") — java.time.Duration đọc được thẳng định dạng này, không
            // cần tự viết parser hay thêm thư viện.
            return (int) Duration.parse(durationNode.asText()).getSeconds();
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Không đọc được thời lượng video YouTube");
        }
    }
}
