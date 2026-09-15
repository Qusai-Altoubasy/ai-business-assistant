package com.aibusinessassistant.order.dto;

import java.math.BigDecimal;

public record CustomerOrderStatisticsDTO(
        long orderCount,
        BigDecimal totalSpent
) {
}