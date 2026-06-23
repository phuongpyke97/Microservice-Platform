package com.platform.auditlogservice.service;

import com.platform.auditlogservice.entity.AuditLog;
import com.platform.auditlogservice.entity.LyriaDailyStat;
import com.platform.auditlogservice.exception.AuditErrorCode;
import com.platform.auditlogservice.repository.AuditLogRepository;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.response.PageResponse;
import com.platform.common.rmq.event.AuditLogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repository;

    @Mock
    private com.platform.auditlogservice.repository.LyriaRequestLogRepository requestLogRepository;

    @Mock
    private com.platform.auditlogservice.repository.LyriaDailyStatRepository dailyStatRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, requestLogRepository, dailyStatRepository, new com.fasterxml.jackson.databind.ObjectMapper(), rabbitTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "alertEmail", "admin@platform.com");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "alertThresholdUsd", 100.0);
    }

    @Test
    void save_persistsEntityFromEvent() {
        var event = new AuditLogEvent(10L, "credit.deducted", "10.0.0.1", "SUCCESS", "{\"amount\":100}", 1000L);
        when(repository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        service.save(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getAction()).isEqualTo("credit.deducted");
        assertThat(saved.getTimestamp()).isEqualTo(1000L);
    }

    @Test
    void query_invalidDateRange_throws() {
        assertThatThrownBy(() -> service.query(null, null, null, 2000L, 1000L, PageRequest.of(0, 20)))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_INVALID_DATE_RANGE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void query_validFilters_returnsPageResponse() {
        AuditLog log = new AuditLog(1L, "login.failed", "127.0.0.1", "FAILED", null, 500L);
        when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<?> result = service.query(1L, "login.failed", null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void save_lyriaGenerateMusic_sendsAlertWhenThresholdExceeded() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        LyriaDailyStat existingStat = new LyriaDailyStat(today);
        existingStat.setEstimatedCostUsd(new BigDecimal("99.99")); 
        existingStat.setAlertSent(false);

        when(dailyStatRepository.findByStatDate(today)).thenReturn(Optional.of(existingStat));
        when(repository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        String metadata = "{\"duration_ms\":1200,\"lyria_token_usage\":{\"prompt_tokens\":50000,\"candidate_tokens\":50000,\"total_tokens\":100000,\"model\":\"lyria-3-clip-preview\",\"msisdn\":\"123456\"}}";
        var event = new AuditLogEvent(100L, "/campaigns/generate", "127.0.0.1", "SUCCESS", metadata, System.currentTimeMillis());

        service.save(event);

        verify(rabbitTemplate, times(1)).convertAndSend(any(String.class), any(String.class), any(com.platform.common.rmq.event.LyriaCostAlertEvent.class));
        assertThat(existingStat.isAlertSent()).isTrue();
        assertThat(existingStat.getEstimatedCostUsd()).isEqualByComparingTo("100.09");
    }

    @Test
    void save_lyriaGenerateMusic_doesNotSendAlertWhenAlreadySent() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        LyriaDailyStat existingStat = new LyriaDailyStat(today);
        existingStat.setEstimatedCostUsd(new BigDecimal("105.00")); 
        existingStat.setAlertSent(true);

        when(dailyStatRepository.findByStatDate(today)).thenReturn(Optional.of(existingStat));
        when(repository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        String metadata = "{\"duration_ms\":1200,\"lyria_token_usage\":{\"prompt_tokens\":50000,\"candidate_tokens\":50000,\"total_tokens\":100000,\"model\":\"lyria-3-clip-preview\",\"msisdn\":\"123456\"}}";
        var event = new AuditLogEvent(100L, "/campaigns/generate", "127.0.0.1", "SUCCESS", metadata, System.currentTimeMillis());

        service.save(event);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
        assertThat(existingStat.isAlertSent()).isTrue();
    }

    @Test
    void save_lyriaGenerateMusic_doesNotSendAlertWhenBelowThreshold() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        LyriaDailyStat existingStat = new LyriaDailyStat(today);
        existingStat.setEstimatedCostUsd(new BigDecimal("10.00")); 
        existingStat.setAlertSent(false);

        when(dailyStatRepository.findByStatDate(today)).thenReturn(Optional.of(existingStat));
        when(repository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        String metadata = "{\"duration_ms\":1200,\"lyria_token_usage\":{\"prompt_tokens\":50000,\"candidate_tokens\":50000,\"total_tokens\":100000,\"model\":\"lyria-3-clip-preview\",\"msisdn\":\"123456\"}}";
        var event = new AuditLogEvent(100L, "/campaigns/generate", "127.0.0.1", "SUCCESS", metadata, System.currentTimeMillis());

        service.save(event);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
        assertThat(existingStat.isAlertSent()).isFalse();
    }
}
