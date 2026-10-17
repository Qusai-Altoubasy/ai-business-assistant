package com.aibusinessassistant.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a knowledge-base Markdown file into one chunk per paragraph. The preamble under the
 * "# " title is filed under the title; every other paragraph is filed under its "## " heading.
 */
public class DocumentChunker {

    // A rule paragraph starts with its bold id, for example "**INV-2** A product is ...".
    private static final Pattern RULE_ID = Pattern.compile("^\\*\\*([A-Z]+-\\d+)\\*\\*");

    public List<DocumentChunk> chunk(String source, String markdown) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String title = null;
        String section = null;
        List<String> paragraph = new ArrayList<>();

        // A heading or a blank line ends the current paragraph; any other line continues it.
        for (String line : markdown.lines().toList()) {
            if (line.startsWith("# ")) {
                flushParagraph(chunks, source, title, section, paragraph);
                title = line.substring(2).strip();
            } else if (line.startsWith("## ")) {
                flushParagraph(chunks, source, title, section, paragraph);
                section = line.substring(3).strip();
            } else if (line.isBlank()) {
                flushParagraph(chunks, source, title, section, paragraph);
            } else {
                paragraph.add(line);
            }
        }
        flushParagraph(chunks, source, title, section, paragraph);
        return chunks;
    }

    // Turns the collected lines into a chunk, if there are any, and starts a new paragraph.
    private static void flushParagraph(List<DocumentChunk> chunks, String source, String title, String section,
                                       List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        if (title == null) {
            throw new IllegalStateException(source + " has text before its # title");
        }
        String text = String.join("\n", paragraph);
        String chunkSection = section == null ? title : section;
        String content = title + " > " + chunkSection + "\n" + text;
        Matcher ruleId = RULE_ID.matcher(text);
        chunks.add(new DocumentChunk(source, title, chunkSection, ruleId.find() ? ruleId.group(1) : null,
                chunks.size(), content, sha256(content)));
        paragraph.clear();
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
