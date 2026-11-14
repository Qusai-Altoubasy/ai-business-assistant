package com.aibusinessassistant.product.tools;

import com.aibusinessassistant.product.Product;
import com.aibusinessassistant.product.ProductRepository;
import com.aibusinessassistant.product.StockClass;
import com.aibusinessassistant.product.dto.InventorySnapshotItemDTO;
import com.aibusinessassistant.product.dto.LowStockProductDTO;
import com.aibusinessassistant.product.dto.ProductStockDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.internal.Json;
import dev.langchain4j.service.tool.ToolExecutionResult;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class InventoryTools {

    // A missing product is an answer the model can give, not a failure of the request.
    public static final String PRODUCT_NOT_FOUND = "No product found with id %d.";

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
                            product.getId(),
                            product.getName(),
                            product.getStockQuantity(),
                            product.getMinimumStock()
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
        Returns the current stock quantity, minimum stock level and stock class of all products.
        The stock class is computed from the inventory policy thresholds. Which stock classes
        violate the policy and what action applies must come from the policy search.
        Use this tool when the user asks a question that compares all products
        against stock rules or the inventory policy.
        """)
    public List<InventorySnapshotItemDTO> getInventorySnapshot() {
        log.info("Tool called: getInventorySnapshot");

        try {
            List<InventorySnapshotItemDTO> products = productRepository.findAllOrderedById()
                    .stream()
                    .map(product -> new InventorySnapshotItemDTO(
                            product.getId(),
                            product.getName(),
                            product.getStockQuantity(),
                            product.getMinimumStock(),
                            StockClass.classify(product.getStockQuantity(), product.getMinimumStock())
                    ))
                    .toList();

            log.info("Tool completed: getInventorySnapshot (productsReturned={})", products.size());
            return products;
        } catch (RuntimeException exception) {
            log.error("Tool failed: getInventorySnapshot", exception);
            throw exception;
        }
    }

    @Tool("""
        Returns the current stock information for a specific product by its ID.
        Use this tool when the user asks about the current inventory level,
        available quantity, or minimum stock level of a specific product.
        """)
    public ToolExecutionResult getProductStock(
            @P("ID of the product whose current stock information should be retrieved")
            Long productId) {
        log.info("Tool called: getProductStock (productId={})", productId);

        Optional<Product> found = productRepository.findByIdOptional(productId);
        if (found.isEmpty()) {
            log.info("Tool completed: getProductStock (productId={}, found=false)", productId);
            return ToolExecutionResult.builder()
                    .resultText(PRODUCT_NOT_FOUND.formatted(productId))
                    .build();
        }
        Product product = found.get();

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
        // The same JSON text the tool executor writes for a returned object.
        return ToolExecutionResult.builder()
                .result(result)
                .resultText(Json.toJson(result))
                .build();
    }
}
