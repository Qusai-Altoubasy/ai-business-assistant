package com.aibusinessassistant.customer.tools;

import com.aibusinessassistant.customer.Customer;
import com.aibusinessassistant.customer.CustomerRepository;
import com.aibusinessassistant.customer.dto.CustomerStatisticsDTO;
import com.aibusinessassistant.order.OrderRepository;
import com.aibusinessassistant.order.dto.CustomerOrderStatisticsDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class CustomerTools {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Tool("""
            Returns statistics for a specific customer by customer ID,
            including the total number of orders, total amount spent,
            and average order value.

            Use this tool when the user asks about a specific customer's
            purchase activity, spending, order count, or customer statistics.
            """)
    public CustomerStatisticsDTO getCustomerStatistics(
            @P("The unique ID of the customer")
            Long customerId) {
        log.info(
                "Tool called: getCustomerStatistics (customerId={})",
                customerId
        );

        try {
            Customer customer = customerRepository.findByIdOptional(customerId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Customer not found with id: " + customerId
                            )
                    );

            CustomerOrderStatisticsDTO orderStatistics =
                    orderRepository.getCustomerOrderStatistics(customerId);

            long orderCount = orderStatistics.orderCount();
            BigDecimal totalSpent = orderStatistics.totalSpent();

            BigDecimal averageOrderValue =
                    calculateAverageOrderValue(totalSpent, orderCount);

            CustomerStatisticsDTO result =
                    new CustomerStatisticsDTO(
                            customer.getId(),
                            customer.getName(),
                            orderCount,
                            totalSpent,
                            averageOrderValue
                    );

            log.info(
                    "Tool completed: getCustomerStatistics " +
                            "(customerId={}, orderCount={}, totalSpent={})",
                    customerId,
                    orderCount,
                    totalSpent
            );

            return result;

        } catch (RuntimeException exception) {
            log.error(
                    "Tool failed: getCustomerStatistics (customerId={})",
                    customerId,
                    exception
            );

            throw exception;
        }
    }

    private BigDecimal calculateAverageOrderValue(
            BigDecimal totalSpent,
            long orderCount
    ) {
        if (orderCount == 0) {
            return BigDecimal.ZERO;
        }

        return totalSpent.divide(
                BigDecimal.valueOf(orderCount),
                2,
                RoundingMode.HALF_UP
        );
    }
}
