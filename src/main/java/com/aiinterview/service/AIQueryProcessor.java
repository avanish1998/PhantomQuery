package com.aiinterview.service;

import org.springframework.stereotype.Service;

@Service
public class AIQueryProcessor {
    private final OpenAiService openAiService;

    public AIQueryProcessor() {
        this.openAiService = new OpenAiService();
    }

    public String processQuery(String query) {
        return openAiService.getCompletion(query);
    }
} 