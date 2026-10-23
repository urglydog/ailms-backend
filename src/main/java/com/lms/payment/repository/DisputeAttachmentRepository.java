package com.lms.payment.repository;

import com.lms.payment.entity.DisputeAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeAttachmentRepository extends JpaRepository<DisputeAttachment, Long> {
    List<DisputeAttachment> findByDispute_IdOrderByIdAsc(Long disputeId);
}
