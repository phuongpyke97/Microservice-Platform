package com.platform.common.rmq.event;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LyriaCostAlertEvent(
    String email,
    BigDecimal thresholdCost,
    BigDecimal currentCost,
    LocalDate statDate
) {}
