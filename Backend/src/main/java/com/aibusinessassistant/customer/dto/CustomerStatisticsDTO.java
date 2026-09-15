package com.aibusinessassistant.customer.dto;

import java.math.BigDecimal;

public record CustomerStatisticsDTO(
        Long customerId,
        String customerName,
        long orderCount,
        BigDecimal totalSpent,
        BigDecimal averageOrderValue
) {
}