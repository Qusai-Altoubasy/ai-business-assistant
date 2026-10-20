package com.aibusinessassistant.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// The boundaries of the inventory-policy.md examples, which all use a minimum stock of 20.
class StockClassTest {

    @Test
    void zeroStockIsOutOfStock() {
        assertEquals(StockClass.OUT_OF_STOCK, StockClass.classify(0, 20));
    }

    @Test
    void oneUpToHalfTheMinimumIsCritical() {
        assertEquals(StockClass.CRITICAL, StockClass.classify(1, 20));
        assertEquals(StockClass.CRITICAL, StockClass.classify(10, 20));
    }

    @Test
    void aboveHalfUpToTheMinimumIsLow() {
        assertEquals(StockClass.LOW, StockClass.classify(11, 20));
        assertEquals(StockClass.LOW, StockClass.classify(20, 20));
    }

    @Test
    void aboveTheMinimumUpToFourTimesItIsHealthy() {
        assertEquals(StockClass.HEALTHY, StockClass.classify(21, 20));
        assertEquals(StockClass.HEALTHY, StockClass.classify(80, 20));
    }

    @Test
    void aboveFourTimesTheMinimumIsOverstock() {
        assertEquals(StockClass.OVERSTOCK, StockClass.classify(81, 20));
    }

    @Test
    void exactlyFourTimesTheMinimumIsHealthy() {
        // The seeded Notebook: 100 units with a minimum stock of 25.
        assertEquals(StockClass.HEALTHY, StockClass.classify(100, 25));
    }

    @Test
    void valuesOutsideThePolicyAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> StockClass.classify(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> StockClass.classify(5, 0));
    }
}
