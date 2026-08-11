package com.lms.dubbing.service;

import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.dto.VoiceMappingDto.CreateReq;
import com.lms.dubbing.dto.VoiceMappingDto.Res;
import com.lms.dubbing.dto.VoiceMappingDto.UpdateReq;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC47 — Admin cấu hình giọng đọc theo ngôn ngữ. BR-DUB-07: đây là nguồn DUY NHẤT quyết định
 * ngôn ngữ lồng tiếng khả dụng, tuyệt đối không hardcode danh sách ngôn ngữ ở nơi khác.
 */
@Service
@RequiredArgsConstructor
public class VoiceMappingService {

    private final VoiceMappingRepository voiceMappingRepository;
    private final AudioTrackRepository audioTrackRepository;

    @Transactional(readOnly = true)
    public List<Res> getAll() {
        return voiceMappingRepository.findAll().stream()
                .map(this::mapToRes)
                .collect(Collectors.toList());
    }

    @Transactional
    public Res create(CreateReq req) {
        VoiceMapping voice = new VoiceMapping();
        voice.setLanguage(req.language());
        voice.setVoiceName(req.voiceName());
        voice.setGender(req.gender());
        voice.setIsDefault(req.isDefault());
        voice.setIsActive(true);

        if (Boolean.TRUE.equals(req.isDefault())) {
            clearOtherDefaults(req.language(), null);
        }

        try {
            return mapToRes(voiceMappingRepository.save(voice));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Giọng đọc này đã tồn tại cho ngôn ngữ đã chọn");
        }
    }

    @Transactional
    public Res update(Long id, UpdateReq req) {
        VoiceMapping voice = voiceMappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VoiceMapping", id));
        voice.setGender(req.gender());
        voice.setIsDefault(req.isDefault());
        voice.setIsActive(req.isActive());

        if (Boolean.TRUE.equals(req.isDefault())) {
            clearOtherDefaults(voice.getLanguage(), id);
        }

        return mapToRes(voiceMappingRepository.save(voice));
    }

    @Transactional
    public void delete(Long id) {
        VoiceMapping voice = voiceMappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VoiceMapping", id));
        if (audioTrackRepository.existsByVoiceMapping_Id(id)) {
            throw new ConflictException("Không thể xoá giọng đọc đang được audio lồng tiếng sử dụng");
        }
        voiceMappingRepository.delete(voice);
    }

    /** Mỗi ngôn ngữ chỉ 1 giọng mặc định — bỏ cờ default của các giọng khác cùng ngôn ngữ. */
    private void clearOtherDefaults(String language, Long exceptId) {
        voiceMappingRepository.findByLanguageAndIsActiveTrue(language).stream()
                .filter(v -> !v.getId().equals(exceptId) && Boolean.TRUE.equals(v.getIsDefault()))
                .forEach(v -> {
                    v.setIsDefault(false);
                    voiceMappingRepository.save(v);
                });
    }

    private Res mapToRes(VoiceMapping voice) {
        return new Res(
                voice.getId(),
                voice.getLanguage(),
                voice.getVoiceName(),
                voice.getGender(),
                voice.getIsDefault(),
                voice.getIsActive()
        );
    }
}
