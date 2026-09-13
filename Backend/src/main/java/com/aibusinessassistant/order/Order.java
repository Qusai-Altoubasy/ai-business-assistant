package com.aibusinessassistant.order;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.aibusinessassistant.customer.Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Column(name = "order_date", nullable = false)
    public LocalDate orderDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal totalAmount;
}
