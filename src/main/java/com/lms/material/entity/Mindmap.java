package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * So do tu duy dang Mermaid.js (UC27).
 *
 * <p>BR-MAT-06: {@code mermaidCode} <b>phai bien dich duoc bang Mermaid.js</b> truoc
 * khi luu. Khong hop le thi goi lai LLM toi da 2 lan, van sai thi danh dau FAILED.
 *
 * <p>Do sau toi da 4-5 cap. Mindmap <b>khong co</b> tuy chon So luong lan Do kho.
 */
@Entity
@Table(name = "mindmaps")
@Getter
@Setter
public class Mindmap extends BaseEntity {

    @Column(name = "mermaid_code", columnDefinition = "LONGTEXT", nullable = false)
    private String mermaidCode;

    /** Do sau toi da 4-5 cap (BR-MAT-04). */
    @Column(name = "node_count", nullable = false)
    private Integer nodeCount = 0;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_generation_id", nullable = false, unique = true)
    private MaterialGeneration materialGeneration;
}
