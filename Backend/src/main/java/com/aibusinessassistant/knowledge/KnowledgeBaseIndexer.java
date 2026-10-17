package com.aibusinessassistant.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.aibusinessassistant.knowledge.KnowledgeChunkRepository.EmbeddedChunk;
import com.aibusinessassistant.knowledge.KnowledgeChunkRepository.StoredChunk;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Reads the knowledge-base files, splits them into chunks, embeds new or changed chunks and stores
 * them in knowledge_chunks. Unchanged chunks are skipped, and rows of files no longer indexed are removed.
 */
@ApplicationScoped
public class KnowledgeBaseIndexer {

    // Listed by name because a resource folder cannot be listed reliably from the packaged jar.
    static final List<String> FILES = List.of("customer-policy.md", "inventory-policy.md", "refund-policy.md");

    private final DocumentChunker chunker = new DocumentChunker();
    private final EmbeddingService embeddingService;
    private final KnowledgeChunkRepository repository;
    private final boolean indexAtStartup;

    public KnowledgeBaseIndexer(EmbeddingService embeddingService, KnowledgeChunkRepository repository,
                                @ConfigProperty(name = "app.knowledge-base.index-at-startup") boolean indexAtStartup) {
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.indexAtStartup = indexAtStartup;
    }

    // A failed run leaves the stored rows usable, so the application starts without a complete index.
    void onStart(@Observes StartupEvent event) {
        if (!indexAtStartup) {
            return;
        }
        try {
            indexAll();
        } catch (RuntimeException e) {
            Log.error("Knowledge-base indexing failed; the application starts with the chunks already stored", e);
        }
    }

    public void indexAll() {
        Map<String, String> markdownBySource = new LinkedHashMap<>();
        for (String file : FILES) {
            markdownBySource.put(file, readFile(file));
        }
        index(markdownBySource);
    }

    // Stops at the first failing source; rows of sources missing from the map are removed only after all succeed.
    public void index(Map<String, String> markdownBySource) {
        String model = embeddingService.modelName();
        for (Map.Entry<String, String> document : markdownBySource.entrySet()) {
            try {
                indexSource(document.getKey(), document.getValue(), model);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Could not index knowledge-base file " + document.getKey(), e);
            }
        }
        repository.deleteSourcesNotIn(markdownBySource.keySet());
    }

    private void indexSource(String source, String markdown, String model) {
        List<DocumentChunk> chunks = chunker.chunk(source, markdown);
        Map<Integer, StoredChunk> stored = repository.findStored(source).stream()
                .collect(Collectors.toMap(StoredChunk::chunkIndex, Function.identity()));

        List<DocumentChunk> changed = chunks.stream()
                .filter(chunk -> !isStored(chunk, stored.get(chunk.chunkIndex()), model))
                .toList();
        // Embedding runs before the write transaction, so a failed call leaves the file's rows unchanged.
        List<EmbeddedChunk> embedded = changed.stream()
                .map(chunk -> new EmbeddedChunk(chunk, embeddingService.embedDocument(chunk.content())))
                .toList();

        int removed = 0;
        if (!embedded.isEmpty() || stored.size() != chunks.size()) {
            removed = repository.saveAndDeleteStale(source, embedded, model, chunks.size());
        }
        Log.infof("Indexed %s: %d chunks, %d embedded and written, %d removed",
                source, chunks.size(), embedded.size(), removed);
    }

    private static boolean isStored(DocumentChunk chunk, StoredChunk stored, String model) {
        return stored != null
                && stored.contentHash().equals(chunk.contentHash())
                && stored.embeddingModel().equals(model);
    }

    static String readFile(String file) {
        String path = "/knowledge-base/" + file;
        try (InputStream content = KnowledgeBaseIndexer.class.getResourceAsStream(path)) {
            if (content == null) {
                throw new IllegalStateException("Knowledge-base file not found on the classpath: " + path);
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read knowledge-base file " + path, e);
        }
    }
}
