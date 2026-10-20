package com.aibusinessassistant.product;

/**
 * A product's stock class as defined by inventory-policy.md INV-2 to INV-6, in whole numbers. Which classes violate
 * the policy and what action applies are decided by the policy rules, not here.
 */
public enum StockClass {
    OUT_OF_STOCK,
    CRITICAL,
    LOW,
    HEALTHY,
    OVERSTOCK;

    // INV-1: the classification applies only to a stock quantity of 0 or more and a minimum stock of 1 or more.
    public static StockClass classify(int stockQuantity, int minimumStock) {
        if (stockQuantity < 0 || minimumStock < 1) {
            throw new IllegalArgumentException("The inventory policy does not classify stock quantity "
                    + stockQuantity + " with minimum stock " + minimumStock);
        }
        if (stockQuantity == 0) {
            return OUT_OF_STOCK;
        }
        if (2 * stockQuantity <= minimumStock) {
            return CRITICAL;
        }
        if (stockQuantity <= minimumStock) {
            return LOW;
        }
        if (stockQuantity <= 4 * minimumStock) {
            return HEALTHY;
        }
        return OVERSTOCK;
    }
}
