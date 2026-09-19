package com.aibusinessassistant.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import com.aibusinessassistant.customer.Customer;
import com.aibusinessassistant.customer.CustomerRepository;
import com.aibusinessassistant.product.Product;
import com.aibusinessassistant.product.ProductRepository;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class BusinessPersistenceTest {

    @Inject
    ProductRepository products;

    @Inject
    CustomerRepository customers;

    @Inject
    OrderRepository orders;

    @Inject
    OrderItemRepository orderItems;

    @Inject
    Flyway flyway;

    @Test
    void bothMigrationsAreRegistered() {
        assertEquals(List.of("1", "2", "3"), Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().toString()).toList());
        assertEquals(0, flyway.info().pending().length);
    }

    @Test
    @TestTransaction
    void seedRecordsAreReadableThroughRepositories() {
        assertEquals(10, products.count("id between 1 and 10"));
        assertEquals(5, customers.count("id between 1 and 5"));
        assertEquals(12, orders.count("id between 1 and 12"));
        assertEquals(24, orderItems.count("id between 1 and 24"));

        Product cable = products.findById(2L);
        assertEquals("USB-C Cable", cable.name);
        assertEquals(3, cable.stockQuantity);
        assertTrue(cable.stockQuantity < cable.minimumStock);
        Product keyboard = products.findById(3L);
        assertTrue(keyboard.stockQuantity > keyboard.minimumStock);

        OrderItem item = orderItems.findById(1L);
        assertEquals("Laptop Stand", item.product.name);
        assertEquals("maya.reed@example.com", item.order.customer.email);
    }

    @Test
    @TestTransaction
    void seededOrderTotalsMatchHistoricalLinePricesAcrossThreeMonths() {
        List<Order> seededOrders = orders.list("id between 1 and 12");
        assertEquals(Set.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
                seededOrders.stream().map(order -> YearMonth.from(order.orderDate)).collect(Collectors.toSet()));

        for (Order order : seededOrders) {
            List<OrderItem> items = orderItems.list("order.id", order.id);
            assertTrue(!items.isEmpty());
            BigDecimal itemTotal = items.stream()
                    .map(item -> item.price.multiply(BigDecimal.valueOf(item.quantity)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, order.totalAmount.compareTo(itemTotal), "Order " + order.id);
        }
    }

    @Test
    @TestTransaction
    void generatedIdsAndRelationshipsRoundTripAfterExplicitSeedIds() {
        Product product = new Product();
        product.name = "Persistence test product";
        product.category = "Test";
        product.price = new BigDecimal("19.95");
        product.stockQuantity = 15;
        product.minimumStock = 5;
        products.persist(product);

        Customer customer = new Customer();
        customer.name = "Persistence test customer";
        customer.email = "persistence-" + UUID.randomUUID() + "@example.com";
        customers.persist(customer);

        Order order = new Order();
        order.customer = customer;
        order.orderDate = LocalDate.of(2026, 4, 1);
        order.totalAmount = new BigDecimal("39.90");
        orders.persist(order);

        OrderItem item = new OrderItem();
        item.order = order;
        item.product = product;
        item.quantity = 2;
        item.price = product.price;
        orderItems.persistAndFlush(item);

        assertTrue(product.id > 10);
        assertTrue(customer.id > 5);
        assertTrue(order.id > 12);
        assertTrue(item.id > 24);
        orderItems.getEntityManager().clear();

        OrderItem reloaded = orderItems.findById(item.id);
        assertEquals(product.name, reloaded.product.name);
        assertEquals(customer.email, reloaded.order.customer.email);
        assertEquals(order.orderDate, reloaded.order.orderDate);
        assertEquals(order.totalAmount, reloaded.order.totalAmount);
        assertEquals(item.price, reloaded.price);
        assertEquals(item.quantity, reloaded.quantity);
    }
}
