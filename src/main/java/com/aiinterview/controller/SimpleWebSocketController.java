package com.aiinterview.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Base64;
import java.io.IOException;
import com.aiinterview.service.SpeechToTextService;
import com.aiinterview.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import javax.sound.sampled.AudioFormat;

@Component
public class SimpleWebSocketController extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SimpleWebSocketController.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // For tracking speech segments
    private final Map<String, Integer> speechSegmentCount = new ConcurrentHashMap<>();
    
    // Services for speech-to-text and AI response
    private final SpeechToTextService speechToTextService;
    private final OpenAiService openAiService;
    
    private final Map<String, List<byte[]>> audioBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastChunkTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isSpeaking = new ConcurrentHashMap<>();
    private final Map<String, Long> speechStartTimes = new ConcurrentHashMap<>();
    private static final long CHUNK_TIMEOUT_MS = 1000; // 1 second timeout for chunks
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB max message size
    
    @Autowired
    public SimpleWebSocketController(SpeechToTextService speechToTextService, OpenAiService openAiService) {
        this.speechToTextService = speechToTextService;
        this.openAiService = openAiService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        speechSegmentCount.put(sessionId, 0);
        logger.info("New client connected - ID: {}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        // Check message size
        if (payload.length() > MAX_MESSAGE_SIZE) {
            logger.warn("Message too large ({} bytes), truncating", payload.length());
            payload = payload.substring(0, MAX_MESSAGE_SIZE);
        }
        
        logger.info("Received message from client {}: {} bytes", session.getId(), payload.length());
        
        // Store the message in session attributes for later use
        session.getAttributes().put("lastMessage", payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String clientId = jsonNode.get("clientId").asText();
            String type = jsonNode.get("type").asText();

            switch (type) {
                case "connection":
                    logger.info("Connection message from client {}: {}", clientId, jsonNode.get("message").asText());
                    break;
                case "speech_start":
                    handleSpeechStart(session, clientId, jsonNode);
                    break;
                case "speech_end":
                    handleSpeechEnd(session, clientId, jsonNode);
                    break;
                case "speech":
                    String audioData = jsonNode.get("audioData").asText();
                    processSpeechSegment(session, clientId, audioData);
                    break;
                default:
                    logger.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage());
            try {
                session.sendMessage(new TextMessage("Error processing message: " + e.getMessage()));
            } catch (IOException ex) {
                logger.error("Error sending error message: {}", ex.getMessage());
            }
        }
    }
    
    private void handleSpeechStart(WebSocketSession session, String clientId, JsonNode message) {
        long timestamp = message.get("timestamp").asLong();
        isSpeaking.put(clientId, true);
        speechStartTimes.put(clientId, timestamp);
        logger.info("Speech started for client {} at {}", clientId, new Date(timestamp));
        
        // Send acknowledgment
        try {
            Map<String, String> response = new HashMap<>();
            response.put("type", "speech_start_ack");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException e) {
            logger.error("Error sending speech start acknowledgment: {}", e.getMessage());
        }
    }

    private void handleSpeechEnd(WebSocketSession session, String clientId, JsonNode message) {
        long timestamp = message.get("timestamp").asLong();
        double duration = message.get("duration").asDouble();
        isSpeaking.put(clientId, false);
        logger.info("Speech ended for client {} at {} (duration: {:.2f}s)", 
                   clientId, new Date(timestamp), duration);
        
        // Send acknowledgment
        try {
            Map<String, String> response = new HashMap<>();
            response.put("type", "speech_end_ack");
            response.put("duration", String.valueOf(duration));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException e) {
            logger.error("Error sending speech end acknowledgment: {}", e.getMessage());
        }
    }

    /**
     * Process a complete speech segment.
     * This method converts the audio to text and optionally generates an AI response.
     */
    private void processSpeechSegment(WebSocketSession session, String clientId, String audioData) {
        try {
            // Decode base64 audio data
            byte[] audioBytes = Base64.getDecoder().decode(audioData);
            logger.info("Received audio data from client {}: {} bytes", clientId, audioBytes.length);
            
            // Get audio format information from the message
            JsonNode formatNode = null;
            try {
                formatNode = objectMapper.readTree(session.getAttributes().get("lastMessage").toString()).get("format");
            } catch (Exception e) {
                logger.warn("Could not parse format information: {}", e.getMessage());
            }
            
            AudioFormat audioFormat = null;
            
            if (formatNode != null) {
                // Create audio format from the provided information
                int sampleRate = formatNode.get("sampleRateHertz").asInt();
                int channels = formatNode.get("audioChannelCount").asInt();
                String encoding = formatNode.get("encoding").asText();
                
                // Determine sample size in bits based on encoding
                int sampleSizeInBits = 16; // Default for LINEAR16
                if ("LINEAR16".equals(encoding)) {
                    sampleSizeInBits = 16;
                } else if ("FLAC".equals(encoding)) {
                    sampleSizeInBits = 16;
                } else if ("MP3".equals(encoding)) {
                    sampleSizeInBits = 16;
                }
                
                // Create audio format
                audioFormat = new AudioFormat(
                    sampleRate,
                    sampleSizeInBits,
                    channels,
                    true, // signed
                    false // big endian
                );
                
                logger.info("Using audio format from client: {}", audioFormat);
            }
            
            // Convert speech to text
            String transcription = speechToTextService.convertSpeechToText(audioBytes);
            
            // Clean up the transcription
            if (transcription != null && transcription.startsWith("Recognized text: ")) {
                transcription = transcription.substring("Recognized text: ".length());
            }
            
            // Log the transcription
            logger.info("Transcription from client {}: {}", clientId, transcription);
            
            // Send transcription back to client
            Map<String, String> response = new HashMap<>();
            response.put("type", "transcription");
            response.put("text", transcription);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
            // Generate AI response if we have a valid transcription
            if (transcription != null && !transcription.isEmpty()) {
                String aiResponse = openAiService.getCompletion(transcription);
                Map<String, String> aiResponseMessage = new HashMap<>();
                aiResponseMessage.put("type", "ai_response");
                aiResponseMessage.put("text", aiResponse);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(aiResponseMessage)));
            }
        } catch (Exception e) {
            logger.error("Error processing speech segment: {}", e.getMessage());
            try {
                session.sendMessage(new TextMessage("Error processing speech: " + e.getMessage()));
            } catch (IOException ex) {
                logger.error("Error sending error message: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("Transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        speechSegmentCount.remove(sessionId);
        logger.info("Client disconnected - ID: {}", sessionId);
    }
} 