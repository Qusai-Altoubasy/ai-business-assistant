# Inventory Policy

- Policy ID: POL-INV
- Version: 1.0
- Effective date: 2026-01-01
- Scope: This policy classifies every product by its current stock level, defines which stock classes violate the inventory policy, sets the restock quantity for low-stock products, and sets how products with limited stock can be sold.

## Stock classification

**INV-1** Every product is classified using only two values from its product record: stock_quantity (units currently in stock) and minimum_stock (the configured minimum stock level). This classification applies to products with a stock_quantity of 0 or more and a minimum_stock of 1 or more. Percentages and multiples of minimum_stock are compared exactly, without rounding.

**INV-2** A product is OUT_OF_STOCK when stock_quantity = 0.

**INV-3** A product is CRITICAL when stock_quantity > 0 and stock_quantity <= 50% of minimum_stock. The same condition in whole numbers is: stock_quantity >= 1 and 2 x stock_quantity <= minimum_stock. Example: a product with minimum_stock = 20 is CRITICAL when it has 1 to 10 units in stock.

**INV-4** A product is LOW when stock_quantity > 50% of minimum_stock and stock_quantity <= minimum_stock. Example: a product with minimum_stock = 20 is LOW when it has 11 to 20 units in stock.

**INV-5** A product is HEALTHY when stock_quantity > minimum_stock and stock_quantity <= 4 x minimum_stock. Example: a product with minimum_stock = 20 is HEALTHY when it has 21 to 80 units in stock.

**INV-6** A product is OVERSTOCK when stock_quantity > 4 x minimum_stock. Example: a product with minimum_stock = 20 is OVERSTOCK when it has 81 or more units in stock.

**INV-7** Every product with a stock_quantity of 0 or more and a minimum_stock of 1 or more belongs to exactly one stock class at any time: OUT_OF_STOCK (stock_quantity = 0), CRITICAL (stock_quantity > 0 and stock_quantity <= 50% of minimum_stock), LOW (stock_quantity > 50% of minimum_stock and stock_quantity <= minimum_stock), HEALTHY (stock_quantity > minimum_stock and stock_quantity <= 4 x minimum_stock) or OVERSTOCK (stock_quantity > 4 x minimum_stock).

## Inventory policy violations and the low-stock list

**INV-8** A product violates the inventory policy when its stock class is OUT_OF_STOCK (stock_quantity = 0), CRITICAL (stock_quantity > 0 and stock_quantity <= 50% of minimum_stock), LOW (stock_quantity > 50% of minimum_stock and stock_quantity <= minimum_stock) or OVERSTOCK (stock_quantity > 4 x minimum_stock). A product complies with the inventory policy only when it is HEALTHY (stock_quantity > minimum_stock and stock_quantity <= 4 x minimum_stock).

**INV-9** The products classified OUT_OF_STOCK (stock_quantity = 0), CRITICAL (stock_quantity > 0 and stock_quantity <= 50% of minimum_stock) or LOW (stock_quantity > 50% of minimum_stock and stock_quantity <= minimum_stock) together are exactly the products where stock_quantity <= minimum_stock. These are the low-stock products, and every low-stock product needs restocking. A product with stock_quantity <= minimum_stock is never HEALTHY (stock_quantity > minimum_stock and stock_quantity <= 4 x minimum_stock) or OVERSTOCK (stock_quantity > 4 x minimum_stock).

**INV-10** An OVERSTOCK product (stock_quantity > 4 x minimum_stock) violates the inventory policy but is not a low-stock product (stock_quantity <= minimum_stock) and never needs restocking.

## Restock target and restock quantity

**INV-11** The restock target for a product is 2 x minimum_stock units. For an OUT_OF_STOCK (stock_quantity = 0), CRITICAL (stock_quantity > 0 and stock_quantity <= 50% of minimum_stock) or LOW (stock_quantity > 50% of minimum_stock and stock_quantity <= minimum_stock) product, the restock quantity is 2 x minimum_stock - stock_quantity, using the current stock_quantity. A product restocked to its target is HEALTHY (stock_quantity > minimum_stock and stock_quantity <= 4 x minimum_stock).

## Selling products with limited stock

**INV-12** An OUT_OF_STOCK product (stock_quantity = 0) cannot be added to an order. Backorders are not accepted for any product.

**INV-13** The quantity on an order line must not exceed the product's stock_quantity at the moment the order is placed.
