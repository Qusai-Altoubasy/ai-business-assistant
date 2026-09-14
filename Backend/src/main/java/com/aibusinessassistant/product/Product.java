package com.aibusinessassistant.product;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "products")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "category", nullable = false)
    public String category;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    public BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    public Integer stockQuantity;

    @Column(name = "minimum_stock", nullable = false)
    public Integer minimumStock;
}
