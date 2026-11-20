package com.lms.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.invoice.entity.InvoiceSequence;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.repository.InvoiceSequenceRepository;
import com.lms.invoice.service.InvoiceNumberService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceNumberServiceTest {

    @Mock private InvoiceSequenceRepository sequenceRepository;
    @InjectMocks private InvoiceNumberService service;

    @Test
    void next_incrementsExistingCounterAndFormatsNumberByTypeAndMonth() {
        InvoiceSequence seq = new InvoiceSequence();
        seq.setSeqKey("RCP-202611");
        seq.setSeqValue(41L);
        when(sequenceRepository.lockByKey("RCP-202611")).thenReturn(Optional.of(seq));

        String number = service.next(InvoiceType.RECEIPT, LocalDateTime.of(2026, 11, 20, 10, 0));

        assertThat(number).isEqualTo("RCP-202611-000042");
        assertThat(seq.getSeqValue()).isEqualTo(42L);
        verify(sequenceRepository, never()).createIfAbsent("RCP-202611");
    }

    @Test
    void next_createsCounterWhenMissingThenStartsAtOne() {
        InvoiceSequence created = new InvoiceSequence();
        created.setSeqKey("RFD-202610");
        when(sequenceRepository.lockByKey("RFD-202610")).thenReturn(Optional.empty(), Optional.of(created));

        String number = service.next(InvoiceType.REFUND, LocalDateTime.of(2026, 10, 1, 0, 0));

        assertThat(number).isEqualTo("RFD-202610-000001");
        verify(sequenceRepository).createIfAbsent("RFD-202610");
    }
}
