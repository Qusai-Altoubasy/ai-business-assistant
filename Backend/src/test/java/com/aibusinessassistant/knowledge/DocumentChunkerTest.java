package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    private static final String INVENTORY = "inventory-policy.md";

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void inventoryPolicyGivesThePreambleThenOneChunkPerRule() {
        List<DocumentChunk> chunks = chunker.chunk(INVENTORY, KnowledgeBaseIndexer.readFile(INVENTORY));

        assertEquals(14, chunks.size());
        assertEquals(IntStream.range(0, 14).boxed().toList(), chunks.stream().map(DocumentChunk::chunkIndex).toList());
        assertEquals("Inventory Policy", chunks.get(0).section());
        assertNull(chunks.get(0).ruleId());
        assertEquals(IntStream.rangeClosed(1, 13).mapToObj(n -> "INV-" + n).toList(),
                chunks.subList(1, 14).stream().map(DocumentChunk::ruleId).toList());

        String classification = "Stock classification";
        String violations = "Inventory policy violations and the low-stock list";
        String restock = "Restock target and restock quantity";
        String selling = "Selling products with limited stock";
        assertEquals(List.of(classification, classification, classification, classification, classification,
                        classification, classification, violations, violations, violations, restock, selling, selling),
                chunks.subList(1, 14).stream().map(DocumentChunk::section).toList());
    }

    @Test
    void everyFileGivesThePreambleThenChunksWithUniqueRuleIds() {
        Map<String, Integer> expectedCounts = Map.of(
                "customer-policy.md", 11, "refund-policy.md", 15, INVENTORY, 14);

        for (String file : KnowledgeBaseIndexer.FILES) {
            List<DocumentChunk> chunks = chunker.chunk(file, KnowledgeBaseIndexer.readFile(file));

            assertEquals(expectedCounts.get(file), chunks.size(), file);
            chunks.forEach(chunk -> assertFalse(chunk.content().isBlank(), file));
            chunks.forEach(chunk -> assertEquals(file, chunk.source()));
            chunks.subList(1, chunks.size()).forEach(chunk ->
                    assertNotNull(chunk.ruleId(), file + " chunk " + chunk.chunkIndex() + " has no rule id"));
            assertEquals(chunks.size() - 1, chunks.stream().map(DocumentChunk::ruleId).filter(id -> id != null)
                    .distinct().count(), file);
        }
    }

    @Test
    void contentIsTheHeadingLinePlusTheParagraphVerbatim() throws NoSuchAlgorithmException {
        String markdown = KnowledgeBaseIndexer.readFile(INVENTORY);
        List<DocumentChunk> chunks = chunker.chunk(INVENTORY, markdown);

        assertEquals("Inventory Policy > Stock classification\n"
                + "**INV-2** A product is OUT_OF_STOCK when stock_quantity = 0.", chunks.get(2).content());
        assertTrue(chunks.get(0).content().startsWith("Inventory Policy > Inventory Policy\n- Policy ID: POL-INV\n"),
                chunks.get(0).content());
        for (DocumentChunk chunk : chunks) {
            String headingLine = chunk.title() + " > " + chunk.section() + "\n";
            assertTrue(chunk.content().startsWith(headingLine), chunk.content());
            assertTrue(markdown.contains(chunk.content().substring(headingLine.length())), chunk.content());
            assertEquals(sha256(chunk.content()), chunk.contentHash());
        }
    }

    @Test
    void paragraphWithoutALeadingRuleIdHasNoRuleId() {
        List<DocumentChunk> chunks = chunker.chunk("test.md", """
                # Test Policy

                ## Notes

                A general note that mentions **TST-9** after its first word.
                """);

        assertEquals(1, chunks.size());
        assertEquals("Notes", chunks.get(0).section());
        assertNull(chunks.get(0).ruleId());
    }

    @Test
    void linesWithoutABlankLineBetweenThemFormOneChunk() {
        List<DocumentChunk> chunks = chunker.chunk("test.md", """
                # Test Policy

                ## Rules

                **TST-1** The first line of a rule
                continues on a second line.

                **TST-2** Another rule.
                """);

        assertEquals(2, chunks.size());
        assertEquals("TST-1", chunks.get(0).ruleId());
        assertEquals("Test Policy > Rules\n**TST-1** The first line of a rule\ncontinues on a second line.",
                chunks.get(0).content());
        assertEquals("TST-2", chunks.get(1).ruleId());
        assertEquals(1, chunks.get(1).chunkIndex());
    }

    @Test
    void textBeforeTheTitleIsRejected() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> chunker.chunk("test.md", "An introduction without a title.\n\n# Test Policy\n"));

        assertEquals("test.md has text before its # title", failure.getMessage());
    }

    private static String sha256(String text) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
