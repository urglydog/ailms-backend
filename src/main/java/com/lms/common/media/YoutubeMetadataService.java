package com.lms.common.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.common.exception.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Trích videoId + kiểm nguồn YouTube còn công khai (UC34, BR-CHUNK-01) qua {@code yt-dlp}
 * (cài trong {@code Dockerfile.dev}) — cùng công cụ dự án đã chọn để xử lý YouTube ở
 * Giai đoạn 5 (ai-worker), tránh phải xin thêm YouTube Data API key.
 */
@Service
public class YoutubeMetadataService {

    private static final int TIMEOUT_SECONDS = 60;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    public String extractVideoId(String url) {
        Matcher matcher = URL_PATTERN.matcher(url == null ? "" : url);
        if (!matcher.find()) {
            throw new InvalidRequestException("URL YouTube không hợp lệ");
        }
        return matcher.group(1);
    }

    /** Gọi mạng thật tới YouTube qua yt-dlp — ném lỗi nếu video private/đã xoá/không tồn tại. */
    public int fetchDurationSec(String videoId) {
        try {
            Process process = new ProcessBuilder(
                    "yt-dlp", "--dump-json", "--skip-download", "--no-warnings",
                    "https://www.youtube.com/watch?v=" + videoId)
                    .redirectErrorStream(false)
                    .start();

            String stdout = readAll(process.getInputStream());
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new InvalidRequestException("Hết thời gian kiểm tra video YouTube");
            }
            if (process.exitValue() != 0) {
                throw new InvalidRequestException("Video YouTube không tồn tại hoặc không công khai");
            }

            JsonNode root = new ObjectMapper().readTree(stdout);
            if (!root.has("duration") || root.get("duration").isNull()) {
                throw new InvalidRequestException("Không đọc được thời lượng video YouTube");
            }
            return root.get("duration").asInt();
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new InvalidRequestException("Không kiểm tra được video YouTube: " + e.getMessage());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
