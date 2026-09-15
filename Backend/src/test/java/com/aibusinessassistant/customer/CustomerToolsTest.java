package com.aibusinessassistant.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.customer.dto.CustomerStatisticsDTO;
import com.aibusinessassistant.customer.tools.CustomerTools;
import com.aibusinessassistant.order.Order;
import com.aibusinessassistant.order.OrderRepository;

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
        CustomerStatisticsDTO result = tools.getCustomerStatistics(1L);

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
        customer.name = "Customer statistics test";
        customer.email = "statistics-" + UUID.randomUUID() + "@example.com";
        customers.persistAndFlush(customer);

        CustomerStatisticsDTO empty = tools.getCustomerStatistics(customer.id);
        assertEquals(0, empty.orderCount());
        assertEquals(0, empty.totalSpent().compareTo(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, empty.averageOrderValue());

        for (String amount : List.of("1.00", "1.00", "0.00")) {
            Order order = new Order();
            order.customer = customer;
            order.orderDate = LocalDate.of(2026, 4, 1);
            order.totalAmount = new BigDecimal(amount);
            orders.persist(order);
        }
        orders.flush();

        CustomerStatisticsDTO result = tools.getCustomerStatistics(customer.id);
        assertEquals(3, result.orderCount());
        assertEquals(0, new BigDecimal("2.00").compareTo(result.totalSpent()));
        assertEquals(new BigDecimal("0.67"), result.averageOrderValue());
    }

    @Test
    @TestTransaction
    void missingCustomerIsRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tools.getCustomerStatistics(-1L));

        assertEquals("Customer not found with id: -1", exception.getMessage());
    }
}
