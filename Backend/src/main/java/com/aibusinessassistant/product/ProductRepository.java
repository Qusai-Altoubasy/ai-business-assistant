package com.aibusinessassistant.product;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    public List<Product> findLowStockProducts() {
        return list("stockQuantity <= minimumStock");
    }

    public List<Product> findAllOrderedById() {
        return listAll(Sort.by("id"));
    }
}
