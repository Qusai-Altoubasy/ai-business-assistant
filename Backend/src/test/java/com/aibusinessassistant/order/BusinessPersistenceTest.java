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
        assertEquals("USB-C Cable", cable.getName());
        assertEquals(3, cable.getStockQuantity());
        assertTrue(cable.getStockQuantity() < cable.getMinimumStock());
        Product keyboard = products.findById(3L);
        assertTrue(keyboard.getStockQuantity() > keyboard.getMinimumStock());

        OrderItem item = orderItems.findById(1L);
        assertEquals("Laptop Stand", item.getProduct().getName());
        assertEquals("maya.reed@example.com", item.getOrder().getCustomer().getEmail());
    }

    @Test
    @TestTransaction
    void seededOrderTotalsMatchHistoricalLinePricesAcrossThreeMonths() {
        List<Order> seededOrders = orders.list("id between 1 and 12");
        assertEquals(Set.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
                seededOrders.stream().map(order -> YearMonth.from(order.getOrderDate())).collect(Collectors.toSet()));

        for (Order order : seededOrders) {
            List<OrderItem> items = orderItems.list("order.id", order.getId());
            assertTrue(!items.isEmpty());
            BigDecimal itemTotal = items.stream()
                    .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, order.getTotalAmount().compareTo(itemTotal), "Order " + order.getId());
        }
    }

    @Test
    @TestTransaction
    void generatedIdsAndRelationshipsRoundTripAfterExplicitSeedIds() {
        Product product = new Product();
        product.setName("Persistence test product");
        product.setCategory("Test");
        product.setPrice(new BigDecimal("19.95"));
        product.setStockQuantity(15);
        product.setMinimumStock(5);
        products.persist(product);

        Customer customer = new Customer();
        customer.setName("Persistence test customer");
        customer.setEmail("persistence-" + UUID.randomUUID() + "@example.com");
        customers.persist(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDate.of(2026, 4, 1));
        order.setTotalAmount(new BigDecimal("39.90"));
        orders.persist(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(product.getPrice());
        orderItems.persistAndFlush(item);

        assertTrue(product.getId() > 10);
        assertTrue(customer.getId() > 5);
        assertTrue(order.getId() > 12);
        assertTrue(item.getId() > 24);
        orderItems.getEntityManager().clear();

        OrderItem reloaded = orderItems.findById(item.getId());
        assertEquals(product.getName(), reloaded.getProduct().getName());
        assertEquals(customer.getEmail(), reloaded.getOrder().getCustomer().getEmail());
        assertEquals(order.getOrderDate(), reloaded.getOrder().getOrderDate());
        assertEquals(order.getTotalAmount(), reloaded.getOrder().getTotalAmount());
        assertEquals(item.getPrice(), reloaded.getPrice());
        assertEquals(item.getQuantity(), reloaded.getQuantity());
    }
}
