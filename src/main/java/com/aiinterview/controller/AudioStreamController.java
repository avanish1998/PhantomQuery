package com.aiinterview.controller;

import com.aiinterview.service.StreamingSpeechToTextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class AudioStreamController {
    private static final Logger logger = LoggerFactory.getLogger(AudioStreamController.class);

    private final StreamingSpeechToTextService streamingSpeechToTextService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Map to store active streaming sessions
    private final Map<String, String> activeStreams = new ConcurrentHashMap<>();

    @Autowired
    public AudioStreamController(StreamingSpeechToTextService streamingSpeechToTextService, 
                                SimpMessagingTemplate messagingTemplate) {
        this.streamingSpeechToTextService = streamingSpeechToTextService;
        this.messagingTemplate = messagingTemplate;
        logger.info("AudioStreamController initialized");
    }

    @MessageMapping("/start-stream")
    public void startStream(@Payload Map<String, Object> payload) {
        String sessionId = UUID.randomUUID().toString();
        String clientId = (String) payload.get("clientId");
        
        logger.info("Starting new audio stream - Session ID: {}, Client ID: {}", sessionId, clientId);
        
        // Store the session
        activeStreams.put(clientId, sessionId);
        logger.info("Active streams count: {}", activeStreams.size());
        
        // Start streaming recognition
        streamingSpeechToTextService.startStreamingRecognition(
            sessionId,
            transcription -> {
                logger.debug("Received transcription for session {}: {}", sessionId, transcription);
                // Send transcription to the client
                Map<String, Object> response = new HashMap<>();
                response.put("type", "transcription");
                response.put("text", transcription);
                response.put("sessionId", sessionId);
                
                messagingTemplate.convertAndSend("/topic/transcription/" + clientId, response);
            },
            error -> {
                logger.error("Error in streaming recognition for session {}: {}", sessionId, error);
                // Send error to the client
                Map<String, Object> response = new HashMap<>();
                response.put("type", "error");
                response.put("message", error);
                response.put("sessionId", sessionId);
                
                messagingTemplate.convertAndSend("/topic/transcription/" + clientId, response);
            }
        );
        
        // Send confirmation to the client
        Map<String, Object> response = new HashMap<>();
        response.put("type", "started");
        response.put("sessionId", sessionId);
        
        messagingTemplate.convertAndSend("/topic/transcription/" + clientId, response);
        logger.info("Stream started successfully for session {}", sessionId);
    }

    @MessageMapping("/audio-data")
    public void receiveAudioData(@Payload Map<String, Object> payload) {
        logger.info("Received WebSocket message: {}", payload);
        
        String clientId = (String) payload.get("clientId");
        String type = (String) payload.get("type");
        String audioDataBase64 = (String) payload.get("audioData");
        
        logger.info("Processing audio data - Client: {}, Type: {}, Has Audio Data: {}", 
                   clientId, type, audioDataBase64 != null);
        
        if (audioDataBase64 != null) {
            try {
                byte[] audioData = java.util.Base64.getDecoder().decode(audioDataBase64);
                logger.info("Received audio: {} bytes", audioData.length);
                
                String sessionId = activeStreams.get(clientId);
                if (sessionId != null) {
                    streamingSpeechToTextService.sendAudioData(sessionId, audioData);
                    logger.info("Forwarded audio to streaming service for session: {}", sessionId);
                } else {
                    logger.warn("No active session for client: {}", clientId);
                }
            } catch (Exception e) {
                logger.error("Error processing audio: {}", e.getMessage());
            }
        }
    }

    @MessageMapping("/binary")
    public void receiveBinaryAudioData(@Payload byte[] audioData) {
        // This method will be called for binary audio data
        logger.debug("Received binary audio data - Size: {} bytes", audioData.length);
        
        // TODO: Implement proper handling of binary audio data
        // This will require tracking the client ID from the previous message
    }

    @MessageMapping("/stop-stream")
    public void stopStream(@Payload Map<String, Object> payload) {
        String clientId = (String) payload.get("clientId");
        String sessionId = activeStreams.remove(clientId);
        
        if (sessionId != null) {
            logger.info("Stopping stream - Session ID: {}, Client ID: {}", sessionId, clientId);
            // Stop the streaming recognition
            streamingSpeechToTextService.stopStreamingRecognition(sessionId);
            
            // Send confirmation to the client
            Map<String, Object> response = new HashMap<>();
            response.put("type", "stopped");
            response.put("sessionId", sessionId);
            
            messagingTemplate.convertAndSend("/topic/transcription/" + clientId, response);
            logger.info("Stream stopped successfully for session {}", sessionId);
        } else {
            logger.warn("Attempted to stop non-existent stream for client ID: {}", clientId);
        }
    }
    
    @GetMapping("/api/stream/status")
    @ResponseBody
    public Map<String, Object> getStreamStatus() {
        logger.info("Stream status requested - Active streams: {}", activeStreams.size());
        Map<String, Object> response = new HashMap<>();
        response.put("activeStreams", activeStreams.size());
        response.put("streams", activeStreams);
        return response;
    }
} 