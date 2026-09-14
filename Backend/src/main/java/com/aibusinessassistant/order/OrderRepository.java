package com.aibusinessassistant.order;

import com.aibusinessassistant.order.dto.SalesSummaryDTO;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;

@ApplicationScoped
public class OrderRepository implements PanacheRepository<Order> {
    public SalesSummaryDTO getSalesSummary(LocalDate from, LocalDate to) {
        return getEntityManager()
                .createQuery("""
                    SELECT NEW com.aibusinessassistant.order.dto.SalesSummaryDTO(
                        CAST(:from AS LocalDate),
                        CAST(:to AS LocalDate),
                        COALESCE(SUM(o.totalAmount), 0),
                        COUNT(o)
                    )
                    FROM Order o
                    WHERE o.orderDate BETWEEN :from AND :to
                    """, SalesSummaryDTO.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }
}
