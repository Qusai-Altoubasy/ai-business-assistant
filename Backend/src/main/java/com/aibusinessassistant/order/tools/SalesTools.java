package com.aibusinessassistant.order.tools;

import com.aibusinessassistant.order.OrderRepository;
import com.aibusinessassistant.order.dto.SalesSummaryDTO;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class salesTools {
    private final OrderRepository orderRepository;

    @Tool("""
    Returns a sales summary for the specified date range.
    Use this tool when the user asks about sales, revenue,
    or total sold amount during a specific period.
    """)
    public SalesSummaryDTO getSales(LocalDate from, LocalDate to) {

    }
}
