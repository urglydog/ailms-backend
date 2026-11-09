package com.lms.common.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.common.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UC34 (BR-CHUNK-01) — sau BUG THẬT 05/09/2026 (yt-dlp bị YouTube chặn IP VPS), service này gọi
 * thẳng YouTube Data API v3 qua {@link RestTemplate} — test bằng {@link JsonNode} thật dựng từ
 * chuỗi JSON mẫu đúng shape thật của {@code videos.list}, không mock JsonNode giả.
 */
@ExtendWith(MockitoExtension.class)
class YoutubeMetadataServiceTest {

    private static final String VIDEO_ID = "abc12345678";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private RestTemplate restTemplate;

    private YoutubeMetadataService service;

    @BeforeEach
    void setUp() {
        service = new YoutubeMetadataService(restTemplate, "test-api-key");
    }

    private JsonNode json(String content) throws Exception {
        return MAPPER.readTree(content);
    }

    @Test
    void extractVideoId_linkYoutubeThuong_layDungId() {
        assertThat(service.extractVideoId("https://www.youtube.com/watch?v=" + VIDEO_ID)).isEqualTo(VIDEO_ID);
    }

    @Test
    void extractVideoId_linkYoutuBeNgan_layDungId() {
        assertThat(service.extractVideoId("https://youtu.be/" + VIDEO_ID + "?si=xyz")).isEqualTo(VIDEO_ID);
    }

    @Test
    void extractVideoId_urlKhongPhaiYoutube_nemInvalidRequest() {
        assertThatThrownBy(() -> service.extractVideoId("https://vimeo.com/12345"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void fetchDurationSec_videoCongKhai_traDungGiay() throws Exception {
        JsonNode response = json("""
                {"items": [{"contentDetails": {"duration": "PT10M30S"}, "status": {"privacyStatus": "public"}}]}
                """);
        when(restTemplate.getForObject(anyString(), org.mockito.Mockito.eq(JsonNode.class))).thenReturn(response);

        int durationSec = service.fetchDurationSec(VIDEO_ID);

        assertThat(durationSec).isEqualTo(630);
    }

    @Test
    void fetchDurationSec_itemsRong_nemVideoKhongTonTaiHoacKhongCongKhai() throws Exception {
        JsonNode response = json("{\"items\": []}");
        when(restTemplate.getForObject(anyString(), org.mockito.Mockito.eq(JsonNode.class))).thenReturn(response);

        assertThatThrownBy(() -> service.fetchDurationSec(VIDEO_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("không tồn tại hoặc không công khai");
    }

    @Test
    void fetchDurationSec_khongCoDuration_nemKhongDocDuocThoiLuong() throws Exception {
        JsonNode response = json("{\"items\": [{\"contentDetails\": {}}]}");
        when(restTemplate.getForObject(anyString(), org.mockito.Mockito.eq(JsonNode.class))).thenReturn(response);

        assertThatThrownBy(() -> service.fetchDurationSec(VIDEO_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("thời lượng");
    }

    @Test
    void fetchDurationSec_goiMangThatBai_nemInvalidRequestBoc() {
        when(restTemplate.getForObject(anyString(), org.mockito.Mockito.eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.fetchDurationSec(VIDEO_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Không kiểm tra được video YouTube");
    }

    @Test
    void fetchDurationSec_thieuApiKey_nemLoiCauHinh() {
        YoutubeMetadataService serviceKhongKey = new YoutubeMetadataService(restTemplate, "");

        assertThatThrownBy(() -> serviceKhongKey.fetchDurationSec(VIDEO_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("YOUTUBE_API_KEY");
    }
}
