package com.lms.dubbing.repository;

import com.lms.dubbing.entity.VoiceMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link VoiceMapping}.
 *
 * <p>BR-DUB-07 — day la nguon DUY NHAT quyet dinh ngon ngu long tieng kha dung.
 */
@Repository
public interface VoiceMappingRepository extends JpaRepository<VoiceMapping, Long> {

    /** Danh sach ngon ngu kha dung cho hoc vien chon (UC18). */
    List<VoiceMapping> findByIsActiveTrue();

    /** BR-DUB-07 — kiem tra 1 ngon ngu cu the co dang active hay khong. */
    List<VoiceMapping> findByLanguageAndIsActiveTrue(String language);
}
