package com.aibusinessassistant.product.tools;

import com.aibusinessassistant.product.Product;
import com.aibusinessassistant.product.ProductRepository;
import com.aibusinessassistant.product.dto.LowStockProductDTO;
import com.aibusinessassistant.product.dto.ProductStockDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class InventoryTools {

    private final ProductRepository productRepository;

    @Tool("""
        Returns products whose current stock quantity is less than
        or equal to their configured minimum stock level.
        Use this tool when the user asks about low-stock products
        or which products currently need restocking.
        """)
    public List<LowStockProductDTO> getLowStockProducts() {
        log.info("Tool called: getLowStockProducts");

        try {
            List<LowStockProductDTO> products = productRepository.findLowStockProducts()
                    .stream()
                    .map(product -> new LowStockProductDTO(
                            product.id,
                            product.name,
                            product.stockQuantity,
                            product.minimumStock
                    ))
                    .toList();

            log.info("Tool completed: getLowStockProducts (productsReturned={})", products.size());
            return products;
        } catch (RuntimeException exception) {
            log.error("Tool failed: getLowStockProducts", exception);
            throw exception;
        }
    }

    @Tool("""
        Returns the current stock information for a specific product by its ID.
        Use this tool when the user asks about the current inventory level,
        available quantity, or minimum stock level of a specific product.
        """)
    public ProductStockDTO getProductStock(
            @P("ID of the product whose current stock information should be retrieved")
            Long productId) {
        log.info("Tool called: getProductStock (productId={})", productId);

        Product product = productRepository.findByIdOptional(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found with id: " + productId
                        )
                );

        ProductStockDTO result = new ProductStockDTO(
                product.getId(),
                product.getName(),
                product.getStockQuantity(),
                product.getMinimumStock()
        );

        log.info(
                "Tool completed: getProductStock (productId={}, stockQuantity={})",
                product.getId(),
                product.getStockQuantity()
        );
        return result;
    }
}
