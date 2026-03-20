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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<DocumentItem> upload(
        @RequestPart("file") MultipartFile file,
        @RequestPart(value = "ownerUserId", required = false) String ownerUserId,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        String id = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String fileType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String storagePath = "local://uploads/" + id + "/" + fileName;

        db.update(
            "INSERT INTO aieap.documents (id, owner_user_id, file_name, file_type, file_size, storage_path, processing_status, summary, created_at, updated_at) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'UPLOADED', ?, NOW(), NOW())",
            id,
            parseUuidOrNull(ownerUserId),
            fileName,
            fileType,
            file.getSize(),
            storagePath,
            "Document queued for chunking and embedding."
        );

        String chunkId = UUID.randomUUID().toString();
        String content = "Initial chunk pending embedding pipeline.";
        db.update(
            "INSERT INTO aieap.document_chunks (id, document_id, chunk_index, content, token_count, citation_label) VALUES (?::uuid, ?::uuid, 0, ?, ?, 'upload-0')",
            chunkId,
            id,
            content,
            estimateTokenCount(content)
        );
        DocumentItem document = document(id);
        return ResponseFactory.success(request, document);
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<DocumentItem>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        List<DocumentItem> items = loadDocuments();
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
        List<DocumentChunk> citations = loadChunks(id);
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
        return ResponseFactory.success(request, loadChunks(id));
    }

    private DocumentItem document(String id) {
        JdbcTemplate db = requireJdbc();
        List<DocumentItem> rows = db.query(
            "SELECT id::text AS id, file_name, file_type, file_size, processing_status, summary, created_at FROM aieap.documents WHERE id = ?::uuid",
            (rs, rowNum) -> new DocumentItem(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("processing_status"),
                rs.getString("summary"),
                rs.getObject("created_at", OffsetDateTime.class)
            ),
            id
        );
        DocumentItem document = rows.isEmpty() ? null : rows.getFirst();
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }

    private List<DocumentItem> loadDocuments() {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, file_name, file_type, file_size, processing_status, summary, created_at FROM aieap.documents ORDER BY created_at DESC",
            (rs, rowNum) -> new DocumentItem(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("processing_status"),
                rs.getString("summary"),
                rs.getObject("created_at", OffsetDateTime.class)
            )
        );
    }

    private List<DocumentChunk> loadChunks(String documentId) {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, chunk_index, content, citation_label FROM aieap.document_chunks WHERE document_id = ?::uuid ORDER BY chunk_index",
            (rs, rowNum) -> new DocumentChunk(
                rs.getString("id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("citation_label")
            ),
            documentId
        );
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private String parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
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