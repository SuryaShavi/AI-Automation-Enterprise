package com.aieap.platform.ai.service;

import com.aieap.platform.common.ai.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AiReportAgentService {
    private static final Logger log = LoggerFactory.getLogger(AiReportAgentService.class);
    private final LlmClient llmClient;

    @Autowired
    public AiReportAgentService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ReportDTO generateWeeklyReport(List<TaskData> tasks, List<EmailSummary> emails) {
        String data = "Tasks: " + tasks + "\nEmails: " + emails;
        String prompt = "Generate professional weekly report. Structure: Executive Summary, Accomplishments, Pending, Next Actions. Concise 200-400 words.\nData: " + data;
        String content = llmClient.completion("Report Generator", prompt, null);
        log.info("Report generated: {} tasks, {} emails", tasks.size(), emails.size());
        return new ReportDTO("Weekly Report", content, tasks.size(), emails.size());
    }

    public record TaskData(String description, String status) {}
    public record EmailSummary(String subject, String summary) {}
    public record ReportDTO(String title, String content, int taskCount, int emailCount) {}
}

