package com.aiinterview.controller;

import com.aiinterview.service.AIQueryProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private AIQueryProcessor aiQueryProcessor;

    @GetMapping("/test/openai")
    public String testOpenAI(@RequestParam(defaultValue = "What is the capital of France?") String query) {
        try {
            return aiQueryProcessor.processQuery(query);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test/api-key")
    public String testApiKey() {
        try {
            // Try a simple query to test the API key
            String response = aiQueryProcessor.processQuery("Say 'API key is working' if you can read this.");
            if (response.contains("API key is working")) {
                return "✅ OpenAI API key is configured correctly!";
            }
            return "❌ OpenAI API key might be invalid. Response: " + response;
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage() + "\nThis usually means the API key is not set or is invalid.";
        }
    }
} 