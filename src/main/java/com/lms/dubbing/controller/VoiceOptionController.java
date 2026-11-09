package com.lms.dubbing.controller;

import com.lms.dubbing.dto.DubbingDto.VoiceOptionRes;
import com.lms.dubbing.repository.VoiceMappingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC20 mở rộng — danh mục ngôn ngữ + giọng đọc đang hỗ trợ (nguồn duy nhất: {@code voice_mappings}
 * đang {@code isActive}, BR-DUB-07), để FE dựng ô chọn ngôn ngữ/giọng lúc kích hoạt lồng tiếng.
 * Public GET (xem {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}) — dữ liệu tham chiếu, không gắn
 * bài học hay tài khoản cụ thể nào, giống {@code /api/v1/courses}/{@code /categories}.
 */
@RestController
@RequiredArgsConstructor
public class VoiceOptionController {

    private final VoiceMappingRepository voiceMappingRepository;

    @GetMapping("/api/v1/voice-options")
    public ResponseEntity<List<VoiceOptionRes>> getVoiceOptions() {
        List<VoiceOptionRes> options = voiceMappingRepository.findByIsActiveTrue().stream()
                .map(v -> new VoiceOptionRes(v.getLanguage(), v.getVoiceName(), v.getGender(), v.getIsDefault()))
                .toList();
        return ResponseEntity.ok(options);
    }
}
