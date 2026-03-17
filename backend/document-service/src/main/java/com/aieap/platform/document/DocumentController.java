package com.aieap.platform.document;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Documents")
@RequestMapping("/documents")
public class DocumentController {
    private final ConcurrentHashMap<String, DocumentItem> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DocumentChunk>> chunks = new ConcurrentHashMap<>();

    public DocumentController() {
        DocumentItem document = new DocumentItem("doc-1", "Annual_Report_2024.pdf", "PDF", 2516582L, "READY", "Quarterly performance with regional growth summary.", OffsetDateTime.now().minusDays(2));
        documents.put(document.id(), document);
        chunks.put(document.id(), List.of(
            new DocumentChunk("chunk-1", 0, "Revenue grew 18% year over year in APAC.", "p.4-paragraph-2"),
            new DocumentChunk("chunk-2", 1, "Operating costs declined 6% due to workflow automation.", "p.9-paragraph-1")
        ));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<DocumentItem> upload(
        @RequestPart("file") MultipartFile file,
        @RequestPart(value = "ownerUserId", required = false) String ownerUserId,
        HttpServletRequest request
    ) {
        String id = UUID.randomUUID().toString();
        DocumentItem document = new DocumentItem(id, file.getOriginalFilename(), file.getContentType(), file.getSize(), "UPLOADED", "Document queued for chunking and embedding.", OffsetDateTime.now());
        documents.put(id, document);
        chunks.put(id, List.of(new DocumentChunk(UUID.randomUUID().toString(), 0, "Initial chunk pending embedding pipeline.", "upload-0")));
        return ResponseFactory.success(request, document);
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<DocumentItem>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        List<DocumentItem> items = documents.values().stream().toList();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "createdAt,DESC"));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<DocumentItem> get(@PathVariable String id, HttpServletRequest request) {
        return ResponseFactory.success(request, document(id));
    }

    @PostMapping("/{id}/ask")
    public ApiEnvelope<DocumentAnswer> ask(@PathVariable String id, @Valid @RequestBody AskDocumentRequest askDocumentRequest, HttpServletRequest request) {
        document(id);
        List<DocumentChunk> citations = chunks.getOrDefault(id, List.of());
        return ResponseFactory.success(request, new DocumentAnswer(
            askDocumentRequest.question(),
            "Answer generated from retrieved chunks with citation-backed grounding.",
            0.91,
            citations
        ));
    }

    @GetMapping("/{id}/chunks")
    public ApiEnvelope<List<DocumentChunk>> getChunks(@PathVariable String id, HttpServletRequest request) {
        document(id);
        return ResponseFactory.success(request, chunks.getOrDefault(id, List.of()));
    }

    private DocumentItem document(String id) {
        DocumentItem document = documents.get(id);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }

    public record DocumentItem(String id, String fileName, String fileType, long fileSize, String processingStatus, String summary, OffsetDateTime createdAt) {
    }

    public record DocumentChunk(String id, int chunkIndex, String content, String citationLabel) {
    }

    public record AskDocumentRequest(@NotBlank String question) {
    }

    public record DocumentAnswer(String question, String answer, double confidence, List<DocumentChunk> citations) {
    }
}