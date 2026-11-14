package com.lms.payment.repository;

import com.lms.payment.entity.RefundRateAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRateAlertRepository extends JpaRepository<RefundRateAlert, Long> {
}
