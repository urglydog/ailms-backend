package com.lms.report.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Bản chụp số liệu của một lần tạo báo cáo tài chính (định kỳ hoặc chạy tay). Không lưu file — file sinh lại từ số liệu khi cần. */
@Entity
@Table(name = "report_snapshots")
@Getter
@Setter
public class ReportSnapshot extends BaseEntity {

    @Column(name = "report_type", nullable = false, length = 10)
    private String reportType;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "trigger_type", nullable = false, length = 10)
    private String triggerType;

    @Column(name = "dedupe_key", length = 60, unique = true)
    private String dedupeKey;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "emailed_to", length = 500)
    private String emailedTo;
}
