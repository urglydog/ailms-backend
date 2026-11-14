package com.lms.finance.repository;

import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import com.lms.finance.entity.PayosPayoutOrder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayosPayoutOrderRepository extends JpaRepository<PayosPayoutOrder, Long> {

    List<PayosPayoutOrder> findByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind kind, Long entityId);

    Optional<PayosPayoutOrder> findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind kind, Long entityId);

    List<PayosPayoutOrder> findByStatusIn(Collection<PayosOrderStatus> statuses);
}
