package com.lms.common.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.common.exception.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * Đọc thời lượng video bằng {@code ffprobe} (cài trong {@code Dockerfile.dev}) — dùng cho MP4
 * nạp trực tiếp (UC34, BR-CHUNK-01). Không dùng thư viện Java thuần vì cần đọc chính xác
 * container MP4 thật, không tin theo phần mở rộng tên file.
 */
@Service
public class FfprobeService {

    private static final int TIMEOUT_SECONDS = 30;

    /** @return thời lượng làm tròn theo giây. */
    public int probeDurationSec(Path videoFile) {
        try {
            Process process = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "json",
                    videoFile.toAbsolutePath().toString())
                    .redirectErrorStream(false)
                    .start();

            String stdout = readAll(process.getInputStream());
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new InvalidRequestException("Hết thời gian phân tích video, file có thể bị hỏng");
            }
            if (process.exitValue() != 0) {
                throw new InvalidRequestException("File không phải video hợp lệ (ffprobe từ chối đọc)");
            }

            JsonNode root = new ObjectMapper().readTree(stdout);
            String durationStr = root.path("format").path("duration").asText(null);
            if (durationStr == null) {
                throw new InvalidRequestException("Không đọc được thời lượng video");
            }
            return (int) Math.round(Double.parseDouble(durationStr));
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IOException | InterruptedException | NumberFormatException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new InvalidRequestException("Không đọc được thời lượng video: " + e.getMessage());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
