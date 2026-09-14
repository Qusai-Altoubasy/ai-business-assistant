package com.aibusinessassistant.order.tools;

import com.aibusinessassistant.order.OrderRepository;
import com.aibusinessassistant.order.dto.SalesSummaryDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class SalesTools {

    private final OrderRepository orderRepository;

    @Tool("""
        Returns a sales summary for a specific date range.

        Use this tool when the user asks about sales revenue,
        total sales, or number of orders during a specific period.

        The 'from' date and 'to' date are inclusive.
        """)
    public SalesSummaryDTO getSales(
            @P("Start date of the sales period in ISO format YYYY-MM-DD")
            LocalDate from,

            @P("End date of the sales period in ISO format YYYY-MM-DD")
            LocalDate to
    ) {
        log.info(
                "Tool called: getSales (from={}, to={})",
                from,
                to
        );

        validateDateRange(from, to);

        try {
            SalesSummaryDTO result = orderRepository.getSalesSummary(from, to);

            log.info(
                    "Tool completed: getSales (from={}, to={}, orderCount={}, totalSales={})",
                    from,
                    to,
                    result.orderCount(),
                    result.totalSales()
            );

            return result;

        } catch (RuntimeException exception) {
            log.error(
                    "Tool failed: getSales (from={}, to={})",
                    from,
                    to,
                    exception
            );

            throw exception;
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Both 'from' and 'to' dates are required."
            );
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "'from' date must be before or equal to 'to' date."
            );
        }
    }
}
