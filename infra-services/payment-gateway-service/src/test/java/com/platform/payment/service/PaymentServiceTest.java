package com.platform.payment.service;

import com.platform.common.core.exception.BaseException;
import com.platform.payment.client.MpsChargeResponse;
import com.platform.payment.client.MpsClient;
import com.platform.payment.dto.request.ChargeRequest;
import com.platform.payment.dto.response.PaymentResponse;
import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.entity.PaymentTransaction;
import com.platform.payment.exception.PaymentErrorCode;
import com.platform.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentTransactionRepository repository;
    @Mock private MpsClient mpsClient;
    @Mock private RabbitTemplate rabbitTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(repository, mpsClient, rabbitTemplate);
    }

    @Test
    void charge_success_persistsAndPublishes() {
        ChargeRequest request = new ChargeRequest("idem-1", "PKG30", 1000, 30);
        PaymentTransaction pending = new PaymentTransaction("idem-1", 1L, "959123", "PKG30", 1000, 30);
        setId(pending, 10L);

        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mpsClient.charge(any())).thenReturn(new MpsChargeResponse(true, "mps-ref-1", "OK"));

        PaymentResponse response = paymentService.charge(1L, "959123", request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void charge_duplicateIdempotency_returnsExisting() {
        ChargeRequest request = new ChargeRequest("idem-1", "PKG30", 1000, 30);
        PaymentTransaction existing = new PaymentTransaction("idem-1", 1L, "959123", "PKG30", 1000, 30);
        existing.markSuccess("ref-1");
        setId(existing, 11L);
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.charge(1L, "959123", request);

        assertThat(response.transactionId()).isEqualTo(11L);
        verify(mpsClient, never()).charge(any());
    }

    @Test
    void charge_providerRejects_marksFailedAndPublishes() {
        ChargeRequest request = new ChargeRequest("idem-2", "PKG30", 1000, 30);
        when(repository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mpsClient.charge(any())).thenReturn(new MpsChargeResponse(false, null, "Rejected"));

        PaymentResponse response = paymentService.charge(1L, "959123", request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void getByIdempotencyKey_missing_throws() {
        when(repository.findByIdempotencyKey("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByIdempotencyKey("missing"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(PaymentErrorCode.PAY_TRANSACTION_NOT_FOUND));
    }

    @Test
    void charge_invalidAmount_throws() {
        ChargeRequest request = new ChargeRequest("idem-3", "PKG30", 0, 30);

        assertThatThrownBy(() -> paymentService.charge(1L, "959123", request))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(PaymentErrorCode.PAY_INVALID_AMOUNT));
    }

    private void setId(PaymentTransaction tx, Long id) {
        try {
            var field = PaymentTransaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(tx, id);
        } catch (Exception ignored) {
        }
    }
}
