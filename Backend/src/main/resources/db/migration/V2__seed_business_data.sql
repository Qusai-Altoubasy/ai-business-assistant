-- Fictional office shop; stock is the current snapshot, not opening inventory.
INSERT INTO products (id, name, category, price, stock_quantity, minimum_stock) VALUES
    (1, 'Laptop Stand', 'Desk Accessories', 45.00, 12, 20),
    (2, 'USB-C Cable', 'Cables', 12.50, 3, 20),
    (3, 'Keyboard', 'Peripherals', 65.00, 50, 10),
    (4, 'Wireless Mouse', 'Peripherals', 28.00, 35, 10),
    (5, '27-inch Monitor', 'Displays', 240.00, 8, 5),
    (6, 'USB-C Hub', 'Adapters', 55.00, 6, 10),
    (7, 'Webcam', 'Peripherals', 75.00, 18, 5),
    (8, 'Headset', 'Audio', 90.00, 25, 8),
    (9, 'Desk Lamp', 'Desk Accessories', 38.00, 22, 10),
    (10, 'Notebook', 'Stationery', 6.00, 100, 25);

INSERT INTO customers (id, name, email) VALUES
    (1, 'Maya Reed', 'maya.reed@example.com'),
    (2, 'Omar Ellis', 'omar.ellis@example.com'),
    (3, 'Lina Brooks', 'lina.brooks@example.com'),
    (4, 'Noah Patel', 'noah.patel@example.com'),
    (5, 'Sara Bennett', 'sara.bennett@example.com');

-- Four orders per month, January through March 2026.
INSERT INTO orders (id, customer_id, order_date, total_amount) VALUES
    (1, 1, DATE '2026-01-05', 110.00),
    (2, 2, DATE '2026-01-12', 93.00),
    (3, 3, DATE '2026-01-20', 295.00),
    (4, 1, DATE '2026-01-28', 106.00),
    (5, 4, DATE '2026-02-03', 165.00),
    (6, 5, DATE '2026-02-10', 112.00),
    (7, 2, DATE '2026-02-18', 505.00),
    (8, 3, DATE '2026-02-25', 160.00),
    (9, 1, DATE '2026-03-04', 267.00),
    (10, 4, DATE '2026-03-11', 163.00),
    (11, 5, DATE '2026-03-19', 267.50),
    (12, 2, DATE '2026-03-27', 155.00);

-- price is the unit price at purchase; each total equals SUM(quantity * price).
INSERT INTO order_items (id, order_id, product_id, quantity, price) VALUES
    (1, 1, 1, 2, 45.00),
    (2, 1, 2, 2, 10.00),
    (3, 2, 3, 1, 65.00),
    (4, 2, 4, 1, 28.00),
    (5, 3, 5, 1, 240.00),
    (6, 3, 6, 1, 55.00),
    (7, 4, 10, 5, 6.00),
    (8, 4, 9, 2, 38.00),
    (9, 5, 7, 1, 75.00),
    (10, 5, 8, 1, 90.00),
    (11, 6, 4, 2, 28.00),
    (12, 6, 10, 10, 5.60),
    (13, 7, 5, 2, 240.00),
    (14, 7, 2, 2, 12.50),
    (15, 8, 6, 2, 55.00),
    (16, 8, 2, 4, 12.50),
    (17, 9, 3, 3, 65.00),
    (18, 9, 10, 12, 6.00),
    (19, 10, 1, 3, 45.00),
    (20, 10, 4, 1, 28.00),
    (21, 11, 8, 2, 90.00),
    (22, 11, 2, 7, 12.50),
    (23, 12, 7, 1, 75.00),
    (24, 12, 9, 2, 40.00);

-- Explicit seed IDs do not advance identity sequences. Keep future inserts safe.
SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT MAX(id) FROM products));
SELECT setval(pg_get_serial_sequence('customers', 'id'), (SELECT MAX(id) FROM customers));
SELECT setval(pg_get_serial_sequence('orders', 'id'), (SELECT MAX(id) FROM orders));
SELECT setval(pg_get_serial_sequence('order_items', 'id'), (SELECT MAX(id) FROM order_items));
