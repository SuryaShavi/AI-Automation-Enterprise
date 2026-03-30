package com.aieap.platform.ai;

import com.aieap.platform.ai.service.PromptTemplateService;
import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Prompt Templates")
@RequestMapping("/ai/templates")
@PreAuthorize("isAuthenticated()")
public class PromptTemplateController {
    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public ApiEnvelope<List<PromptTemplateService.PromptTemplateDto>> getAllTemplates(HttpServletRequest request) {
        return ResponseFactory.success(request, promptTemplateService.getAllActiveTemplates());
    }

    @GetMapping("/category/{category}")
    public ApiEnvelope<List<PromptTemplateService.PromptTemplateDto>> getTemplatesByCategory(
        @PathVariable String category,
        HttpServletRequest request
    ) {
        return ResponseFactory.success(request, promptTemplateService.getTemplatesByCategory(category));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<PromptTemplateService.PromptTemplateDto> getTemplateById(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        var template = promptTemplateService.getTemplateById(id);
        return ResponseFactory.success(request, new PromptTemplateService.PromptTemplateDto(
            template.getId().toString(),
            template.getName(),
            template.getCategory(),
            template.getDescription(),
            template.getSystemPrompt(),
            template.getVersion(),
            template.getIsActive()
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<PromptTemplateService.PromptTemplateDto> createTemplate(
        @Valid @RequestBody CreateTemplateRequest request,
        HttpServletRequest servletRequest
    ) {
        var template = promptTemplateService.createTemplate(
            request.name(),
            request.category(),
            request.systemPrompt(),
            request.description()
        );
        return ResponseFactory.success(servletRequest, new PromptTemplateService.PromptTemplateDto(
            template.getId().toString(),
            template.getName(),
            template.getCategory(),
            template.getDescription(),
            template.getSystemPrompt(),
            template.getVersion(),
            template.getIsActive()
        ));
    }

    @PutMapping("/{id}")
    public ApiEnvelope<PromptTemplateService.PromptTemplateDto> updateTemplate(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTemplateRequest request,
        HttpServletRequest servletRequest
    ) {
        var template = promptTemplateService.updateTemplate(id, request.systemPrompt(), request.description());
        return ResponseFactory.success(servletRequest, new PromptTemplateService.PromptTemplateDto(
            template.getId().toString(),
            template.getName(),
            template.getCategory(),
            template.getDescription(),
            template.getSystemPrompt(),
            template.getVersion(),
            template.getIsActive()
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable UUID id) {
        promptTemplateService.deleteTemplate(id);
    }

    // Request Records
    public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotBlank String systemPrompt,
        String description
    ) {}

    public record UpdateTemplateRequest(
        @NotBlank String systemPrompt,
        String description
    ) {}
}
