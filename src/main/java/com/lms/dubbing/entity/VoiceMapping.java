package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bang anh xa giong doc theo ngon ngu, Admin cau hinh (UC47).
 *
 * <p><b>BR-DUB-07:</b> day la <b>nguon duy nhat</b> quyet dinh ngon ngu long tieng
 * kha dung. Hoc vien chi chon duoc ban ghi {@code isActive = true}.
 * TUYET DOI khong hardcode danh sach ngon ngu o bat ky dau khac.
 */
@Entity
@Table(name = "voice_mappings",
        uniqueConstraints = @UniqueConstraint(name = "uk_voice_lang_name", columnNames = {"language", "voice_name"}))
@Getter
@Setter
public class VoiceMapping extends BaseEntity {

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "voice_name", nullable = false, length = 100)
    private String voiceName;

    /** MALE hoac FEMALE. */
    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    /** Giong mac dinh cho ngon ngu do. */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /** Admin bat/tat qua UC47 - quyet dinh ngon ngu co kha dung hay khong. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
