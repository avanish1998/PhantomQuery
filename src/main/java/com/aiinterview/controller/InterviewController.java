package com.aiinterview.controller;

import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.CompletionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.google.cloud.speech.v1.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

@Controller
public class InterviewController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final OpenAiService openAiService;
    private final SpeechClient speechClient;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public InterviewController() throws IOException {
        // Initialize OpenAI service (you'll need to set OPENAI_API_KEY environment variable)
        this.openAiService = new OpenAiService(System.getenv("OPENAI_API_KEY"));
        // Initialize Google Cloud Speech client (you'll need to set GOOGLE_APPLICATION_CREDENTIALS)
        this.speechClient = SpeechClient.create();
    }

    @MessageMapping("/start-audio-capture")
    @SendTo("/topic/status")
    public String startAudioCapture() {
        if (isProcessing.compareAndSet(false, true)) {
            return "Audio capture started";
        }
        return "Audio capture already in progress";
    }

    @MessageMapping("/stop-audio-capture")
    @SendTo("/topic/status")
    public String stopAudioCapture() {
        if (isProcessing.compareAndSet(true, false)) {
            return "Audio capture stopped";
        }
        return "No audio capture in progress";
    }

    @MessageMapping("/process-audio")
    @SendTo("/topic/transcript")
    public String processAudio(String audioData) throws Exception {
        if (!isProcessing.get()) {
            return "Audio capture not started";
        }

        // Convert base64 audio data to bytes
        byte[] audioBytes = Base64.getDecoder().decode(audioData);
        
        // Configure recognition with streaming settings
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("en-US")
                .setEnableAutomaticGainControl(true)
                .setEnableVoiceActivityDetection(true)
                .setModel("latest_long")
                .build();
        
        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(com.google.protobuf.ByteString.copyFrom(audioBytes))
                .build();

        // Perform the speech recognition request
        RecognizeResponse response = speechClient.recognize(config, audio);
        String transcript = response.getResultsList().stream()
                .findFirst()
                .map(result -> result.getAlternativesList().get(0).getTranscript())
                .orElse("");

        if (!transcript.isEmpty()) {
            // Get ChatGPT response
            String chatGptResponse = getChatGptResponse(transcript);
            return "Question: " + transcript + "\nAnswer: " + chatGptResponse;
        }
        
        return "No speech detected";
    }

    private String getChatGptResponse(String question) {
        CompletionRequest completionRequest = CompletionRequest.builder()
                .prompt(question)
                .model("text-davinci-003")
                .maxTokens(200)
                .temperature(0.7)
                .build();

        return openAiService.createCompletion(completionRequest).getChoices().get(0).getText();
    }
} 