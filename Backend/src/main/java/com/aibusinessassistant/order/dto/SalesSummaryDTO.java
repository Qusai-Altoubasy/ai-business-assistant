package com.aibusinessassistant.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesSummaryDTO(
        LocalDate from,
        LocalDate to,
        BigDecimal totalSales,
        Long orderCount
) {
}