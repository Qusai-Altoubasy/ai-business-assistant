package com.aibusinessassistant.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.order.OrderItemRepository;
import com.aibusinessassistant.product.dto.InventorySnapshotItemDTO;
import com.aibusinessassistant.product.tools.InventoryTools;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class InventoryToolsTest {

    private static final InventorySnapshotItemDTO KEYBOARD =
            new InventorySnapshotItemDTO(3L, "Keyboard", 50, 10, StockClass.OVERSTOCK);

    @Inject
    InventoryTools tools;

    @Inject
    ProductRepository products;

    @Inject
    OrderItemRepository orderItems;

    @Test
    @TestTransaction
    void snapshotReturnsEverySeededProductInIdOrderWithItsStockClass() {
        assertEquals(List.of(
                new InventorySnapshotItemDTO(1L, "Laptop Stand", 12, 20, StockClass.LOW),
                new InventorySnapshotItemDTO(2L, "USB-C Cable", 3, 20, StockClass.CRITICAL),
                KEYBOARD,
                new InventorySnapshotItemDTO(4L, "Wireless Mouse", 35, 10, StockClass.HEALTHY),
                new InventorySnapshotItemDTO(5L, "27-inch Monitor", 8, 5, StockClass.HEALTHY),
                new InventorySnapshotItemDTO(6L, "USB-C Hub", 6, 10, StockClass.LOW),
                new InventorySnapshotItemDTO(7L, "Webcam", 18, 5, StockClass.HEALTHY),
                new InventorySnapshotItemDTO(8L, "Headset", 25, 8, StockClass.HEALTHY),
                new InventorySnapshotItemDTO(9L, "Desk Lamp", 22, 10, StockClass.HEALTHY),
                new InventorySnapshotItemDTO(10L, "Notebook", 100, 25, StockClass.HEALTHY)),
                tools.getInventorySnapshot());
    }

    @Test
    @TestTransaction
    void snapshotIncludesOverstockedProductsThatAreNotLowStock() {
        assertTrue(tools.getInventorySnapshot().contains(KEYBOARD));
        assertFalse(tools.getLowStockProducts().stream().anyMatch(product -> product.id().equals(KEYBOARD.id())));
    }

    @Test
    @TestTransaction
    void emptyProductsTableReturnsAnEmptySnapshot() {
        // Order items reference products, so they are removed first; the transaction rolls both back.
        orderItems.deleteAll();
        products.deleteAll();

        assertEquals(List.of(), tools.getInventorySnapshot());
    }
}
