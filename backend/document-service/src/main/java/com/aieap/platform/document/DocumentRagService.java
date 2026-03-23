package com.aieap.platform.document;

import com.aieap.platform.common.ai.LlmClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentRagService {
    private final LlmClient llmClient;

    public DocumentRagService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ProcessedDocument processUpload(MultipartFile file) {
        String text = extractText(file);
        List<String> chunks = chunkText(text, 1200, 200);
        String summary = summarize(text, chunks);
        return new ProcessedDocument(summary, chunks);
    }

    public String answerQuestion(String question, List<DocumentController.DocumentChunk> citations) {
        if (citations == null || citations.isEmpty()) {
            return "No relevant document context was found for the question.";
        }

        StringBuilder context = new StringBuilder();
        int limit = Math.min(6, citations.size());
        for (int i = 0; i < limit; i++) {
            DocumentController.DocumentChunk chunk = citations.get(i);
            context.append("[")
                .append(chunk.citationLabel())
                .append("] ")
                .append(chunk.content())
                .append("\n");
        }

        if (!llmClient.isConfigured()) {
            return "Retrieved context is available, but AI provider key is missing. " +
                "Configure AI_PROVIDER_API_KEY to generate grounded answers.";
        }

        String systemPrompt = "You are a document QA assistant. " +
            "Answer only from supplied context and cite citation labels in square brackets.";
        String userPrompt = "Question: " + question + "\n\nContext:\n" + context;
        return llmClient.completion(systemPrompt, userPrompt);
    }

    private String summarize(String text, List<String> chunks) {
        if (!StringUtils.hasText(text)) {
            return "Uploaded file has no extractable text.";
        }

        if (!llmClient.isConfigured()) {
            String compact = text.replaceAll("\\s+", " ").trim();
            return compact.substring(0, Math.min(compact.length(), 220));
        }

        String source = chunks.isEmpty() ? text : chunks.getFirst();
        String systemPrompt = "Summarize enterprise documents in 2 concise sentences.";
        String userPrompt = "Summarize this text:\n" + source;
        return llmClient.completion(systemPrompt, userPrompt);
    }

    private String extractText(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                return "";
            }

            if (contentType != null && contentType.startsWith("text/")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }

            if (hasExtension(filename, ".txt", ".md", ".csv", ".json")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }

            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf") || hasExtension(filename, ".pdf")) {
                return extractPdf(bytes);
            }

            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("word") || hasExtension(filename, ".docx")) {
                return extractDocx(bytes);
            }

            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception ex) {
            return "";
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                builder.append(paragraph.getText()).append("\n");
            }
            return builder.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean hasExtension(String filename, String... extensions) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (normalized.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    public record ProcessedDocument(String summary, List<String> chunks) {
    }
}
