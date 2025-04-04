package com.aiinterview.service;

import org.springframework.stereotype.Service;

@Service
public class SpeechToTextService {
    
    public SpeechToTextService() {
        // Empty constructor - no Google Cloud initialization
    }

    public String convertSpeechToText(byte[] audioData) {
        // Placeholder implementation
        return "Speech-to-text service is not configured. Received " + audioData.length + " bytes of audio data.";
    }
} 