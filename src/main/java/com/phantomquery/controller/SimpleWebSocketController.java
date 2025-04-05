package com.phantomquery.controller;

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
import com.phantomquery.service.SpeechToTextService;
import com.phantomquery.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import javax.sound.sampled.AudioFormat;
import java.util.UUID;
import com.phantomquery.model.Conversation;
import com.phantomquery.model.Message;
import com.phantomquery.service.ConversationService;

@Component
public class SimpleWebSocketController extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SimpleWebSocketController.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> audioBuffers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // For tracking speech segments
    private final Map<String, Integer> speechSegmentCount = new ConcurrentHashMap<>();
    
    // Services for speech-to-text and AI response
    private final SpeechToTextService speechToTextService;
    private final OpenAiService openAiService;
    
    private final Map<String, Long> lastChunkTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isSpeaking = new ConcurrentHashMap<>();
    private final Map<String, Long> speechStartTimes = new ConcurrentHashMap<>();
    private static final long CHUNK_TIMEOUT_MS = 1000; // 1 second timeout for chunks
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB max message size
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    public SimpleWebSocketController(SpeechToTextService speechToTextService, OpenAiService openAiService) {
        this.speechToTextService = speechToTextService;
        this.openAiService = openAiService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        audioBuffers.put(sessionId, new StringBuilder());
        logger.info("Client connected: {}", sessionId);
        
        // Send the client ID to the client
        try {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("type", "client_id");
            responseMap.put("clientId", sessionId);
            
            String response = objectMapper.writeValueAsString(responseMap);
            logger.info("Sending client ID to client {}: {}", sessionId, response);
            
            session.sendMessage(new TextMessage(response));
        } catch (IOException e) {
            logger.error("Error sending client ID to client {}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String sessionId = session.getId();
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String type = jsonNode.get("type").asText();

        switch (type) {
            case "connection":
                // Just acknowledge the connection
                logger.info("Client {} connected: {}", sessionId, jsonNode.get("message").asText());
                break;
            case "speech_start":
                handleSpeechStart(sessionId);
                break;
            case "speech_data":
                handleSpeechData(sessionId, jsonNode);
                break;
            case "speech_end":
                handleSpeechEnd(sessionId);
                break;
            case "speech":
                // Handle direct speech data from Python client
                String audioData = jsonNode.get("audioData").asText();
                String clientId = jsonNode.has("clientId") ? jsonNode.get("clientId").asText() : sessionId;
                logger.info("Received speech data from client: {}", clientId);
                processSpeechSegment(session, clientId, audioData);
                break;
            case "send_message":
                handleSendMessage(sessionId, jsonNode);
                break;
            case "clear_input":
                handleClearInput(sessionId);
                break;
            default:
                logger.warn("Unknown message type: {}", type);
        }
    }

    private void processSpeechSegment(WebSocketSession session, String sessionId, String audioData) {
        try {
            // Decode base64 audio data
            byte[] audioBytes = Base64.getDecoder().decode(audioData);
            logger.info("Received audio data size: {} bytes", audioBytes.length);
            
            // Convert speech to text
            String transcription = speechToTextService.convertSpeechToText(audioBytes);
            
            // Clean up the transcription (remove prefix if present)
            if (transcription != null && transcription.startsWith("Recognized text: ")) {
                transcription = transcription.substring("Recognized text: ".length());
            }
            
            // Log the transcription
            logger.info("Transcription for session {}: {}", sessionId, transcription);
            
            // Create a simple, direct transcription message
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("type", "transcription");
            responseMap.put("text", transcription);
            responseMap.put("append", false);
            
            // Convert to JSON
            String response = objectMapper.writeValueAsString(responseMap);
            logger.info("Broadcasting transcription to all clients: {}", response);
            
            // Broadcast to all connected clients
            for (WebSocketSession clientSession : sessions.values()) {
                try {
                    clientSession.sendMessage(new TextMessage(response));
                    logger.info("Sent transcription to client: {}", clientSession.getId());
                } catch (IOException e) {
                    logger.error("Error sending transcription to client {}: {}", clientSession.getId(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing speech segment: {}", e.getMessage());
            try {
                String errorMessage = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", "Error processing speech: " + e.getMessage()
                ));
                session.sendMessage(new TextMessage(errorMessage));
            } catch (IOException ex) {
                logger.error("Error sending error message: {}", ex.getMessage());
            }
        }
    }

    private void handleSpeechStart(String sessionId) {
        audioBuffers.get(sessionId).setLength(0);
        logger.info("Speech started for session: {}", sessionId);
    }

    private void handleSpeechData(String sessionId, JsonNode jsonNode) throws IOException {
        String audioData = jsonNode.get("audio").asText();
        audioBuffers.get(sessionId).append(audioData);
        logger.info("Received audio data for session: {}", sessionId);
        
        // Process the audio data immediately
        byte[] audioBytes = Base64.getDecoder().decode(audioData);
        logger.info("Decoded audio data size: {} bytes", audioBytes.length);
        
        String transcription = speechToTextService.convertSpeechToText(audioBytes);
        
        // Clean up the transcription (remove prefix if present)
        if (transcription != null && transcription.startsWith("Recognized text: ")) {
            transcription = transcription.substring("Recognized text: ".length());
        }
        
        // Log the transcription
        logger.info("Transcription for session {}: {}", sessionId, transcription);
        
        // Send the transcription back to the client
        WebSocketSession session = sessions.get(sessionId);
        String response = objectMapper.writeValueAsString(Map.of(
            "type", "transcription",
            "text", transcription,
            "append", false
        ));
        logger.info("Sending transcription response to client: {}", response);
        session.sendMessage(new TextMessage(response));
        
        // No longer automatically generate AI response
    }

    private void handleSpeechEnd(String sessionId) throws IOException {
        StringBuilder buffer = audioBuffers.get(sessionId);
        if (buffer.length() > 0) {
            String transcription = speechToTextService.convertSpeechToText(buffer.toString().getBytes());
            WebSocketSession session = sessions.get(sessionId);
            
            // Get the current input value from the session attributes
            String currentInput = (String) session.getAttributes().getOrDefault("currentInput", "");
            boolean shouldAppend = !currentInput.isEmpty();
            logger.info("Speech end - Current input: '{}', Should append: {}", currentInput, shouldAppend);
            
            // Store the new input value
            session.getAttributes().put("currentInput", transcription);
            
            // Send the transcription back to the client
            String response = objectMapper.writeValueAsString(Map.of(
                "type", "transcription",
                "text", transcription,
                "append", shouldAppend
            ));
            logger.info("Sending final transcription response to client: {}", response);
            session.sendMessage(new TextMessage(response));
            
            logger.info("Speech ended for session: {}", sessionId);
            
            // No longer automatically generate AI response
        }
    }

    private void handleSendMessage(String sessionId, JsonNode jsonNode) throws IOException {
        String content = jsonNode.get("content").asText();
        String conversationId = jsonNode.get("conversationId").asText();
        
        // Add user message and get AI response
        Message aiMessage = conversationService.addUserMessage(conversationId, content);
        WebSocketSession session = sessions.get(sessionId);
        
        // Clear the input after sending
        session.getAttributes().put("currentInput", "");
        
        // Send AI response back to client
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "ai_response",
            "content", aiMessage.getContent()
        ))));
        logger.info("AI response sent for session: {}", sessionId);
    }

    private void handleClearInput(String sessionId) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        session.getAttributes().put("currentInput", "");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "input_cleared"
        ))));
        logger.info("Input cleared for session: {}", sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("Transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        audioBuffers.remove(sessionId);
        logger.info("Client disconnected: {}", sessionId);
    }
} 