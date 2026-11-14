package com.aibusinessassistant.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.customer.dto.CustomerStatisticsDTO;
import com.aibusinessassistant.customer.tools.CustomerTools;
import com.aibusinessassistant.order.Order;
import com.aibusinessassistant.order.OrderRepository;

import dev.langchain4j.internal.Json;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class CustomerToolsTest {

    @Inject
    CustomerTools tools;

    @Inject
    CustomerRepository customers;

    @Inject
    OrderRepository orders;

    @Test
    @TestTransaction
    void seededCustomerStatisticsIncludeAllRecordedOrders() {
        CustomerStatisticsDTO result = statistics(1L);

        assertEquals(1L, result.customerId());
        assertEquals("Maya Reed", result.customerName());
        assertEquals(3, result.orderCount());
        assertEquals(0, new BigDecimal("483.00").compareTo(result.totalSpent()));
        assertEquals(new BigDecimal("161.00"), result.averageOrderValue());
    }

    @Test
    @TestTransaction
    void emptyCustomerHasZeroTotalsAndAverageIsRoundedAfterOrdersAreAdded() {
        Customer customer = new Customer();
        customer.setName("Customer statistics test");
        customer.setEmail("statistics-" + UUID.randomUUID() + "@example.com");
        customers.persistAndFlush(customer);

        CustomerStatisticsDTO empty = statistics(customer.getId());
        assertEquals(0, empty.orderCount());
        assertEquals(0, empty.totalSpent().compareTo(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, empty.averageOrderValue());

        for (String amount : List.of("1.00", "1.00", "0.00")) {
            Order order = new Order();
            order.setCustomer(customer);
            order.setOrderDate(LocalDate.of(2026, 4, 1));
            order.setTotalAmount(new BigDecimal(amount));
            orders.persist(order);
        }
        orders.flush();

        CustomerStatisticsDTO result = statistics(customer.getId());
        assertEquals(3, result.orderCount());
        assertEquals(0, new BigDecimal("2.00").compareTo(result.totalSpent()));
        assertEquals(new BigDecimal("0.67"), result.averageOrderValue());
    }

    @Test
    @TestTransaction
    void foundCustomerIsReturnedAsTheResultObjectAndAsJsonText() {
        ToolExecutionResult result = tools.getCustomerStatistics(1L);

        assertEquals(Json.toJson(result.result()), result.resultText());
        assertTrue(result.resultText().contains("Maya Reed"), result.resultText());
    }

    @Test
    @TestTransaction
    void missingCustomerReturnsTheNotFoundResult() {
        ToolExecutionResult result = tools.getCustomerStatistics(99L);

        assertEquals("No customer found with id 99.", result.resultText());
        assertNull(result.result());
    }

    private CustomerStatisticsDTO statistics(Long customerId) {
        return (CustomerStatisticsDTO) tools.getCustomerStatistics(customerId).result();
    }
}
