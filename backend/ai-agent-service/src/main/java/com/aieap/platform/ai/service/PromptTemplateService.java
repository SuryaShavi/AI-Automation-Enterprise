package com.aieap.platform.ai.service;

import com.aieap.platform.ai.domain.PromptTemplate;
import com.aieap.platform.ai.repository.PromptTemplateRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PromptTemplateService {
    private final PromptTemplateRepository promptTemplateRepository;

    public PromptTemplateService(PromptTemplateRepository promptTemplateRepository) {
        this.promptTemplateRepository = promptTemplateRepository;
    }

    @CacheEvict(value = "prompt-templates", allEntries = true)
    public PromptTemplate createTemplate(String name, String category, String systemPrompt, String description) {
        PromptTemplate template = new PromptTemplate(name, category, systemPrompt);
        template.setDescription(description);
        return promptTemplateRepository.save(template);
    }

    public PromptTemplate getTemplate(String name) {
        return promptTemplateRepository.findByName(name)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prompt template not found: " + name));
    }

    public PromptTemplate getTemplateById(UUID id) {
        return promptTemplateRepository.findById(Objects.requireNonNull(id, "template id must not be null"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prompt template not found"));
    }

    @Cacheable(value = "prompt-templates", key = "'all'", unless = "#result.isEmpty()")
    public List<PromptTemplateDto> getAllActiveTemplates() {
        return promptTemplateRepository.findAllActive().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Cacheable(value = "prompt-templates", key = "'cat:' + #category", unless = "#result.isEmpty()")
    public List<PromptTemplateDto> getTemplatesByCategory(String category) {
        return promptTemplateRepository.findByCategory(category).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @CacheEvict(value = "prompt-templates", allEntries = true)
    public PromptTemplate updateTemplate(UUID id, String systemPrompt, String description) {
        PromptTemplate template = getTemplateById(id);
        
        // Increment version for update
        template.setVersion(template.getVersion() + 1);
        template.setSystemPrompt(systemPrompt);
        template.setDescription(description);
        
        return promptTemplateRepository.save(template);
    }

    @CacheEvict(value = "prompt-templates", allEntries = true)
    public void deleteTemplate(UUID id) {
        PromptTemplate template = getTemplateById(id);
        template.setIsActive(false);
        promptTemplateRepository.save(template);
    }

    public String getSystemPrompt(String templateName) {
        return getTemplate(templateName).getSystemPrompt();
    }

    // DTO
    public record PromptTemplateDto(
        String id,
        String name,
        String category,
        String description,
        String systemPrompt,
        Integer version,
        Boolean isActive
    ) {}

    private PromptTemplateDto toDto(PromptTemplate template) {
        return new PromptTemplateDto(
            template.getId().toString(),
            template.getName(),
            template.getCategory(),
            template.getDescription(),
            template.getSystemPrompt(),
            template.getVersion(),
            template.getIsActive()
        );
    }
}
