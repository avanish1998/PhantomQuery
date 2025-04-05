package com.aiinterview.controller;

import com.aiinterview.service.StreamingSpeechToTextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
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

    private final StreamingSpeechToTextService streamingSpeechToTextService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Map to store active streaming sessions
    private final Map<String, String> activeStreams = new ConcurrentHashMap<>();

    @Autowired
    public AudioStreamController(StreamingSpeechToTextService streamingSpeechToTextService, 
                                SimpMessagingTemplate messagingTemplate) {
        this.streamingSpeechToTextService = streamingSpeechToTextService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/start-stream")
    public void startStream(@Payload Map<String, Object> payload) {
        String sessionId = UUID.randomUUID().toString();
        String clientId = (String) payload.get("clientId");
        
        // Store the session
        activeStreams.put(clientId, sessionId);
        
        // Start streaming recognition
        streamingSpeechToTextService.startStreamingRecognition(
            sessionId,
            transcription -> {
                // Send transcription to the client
                Map<String, Object> response = new HashMap<>();
                response.put("type", "transcription");
                response.put("text", transcription);
                response.put("sessionId", sessionId);
                
                messagingTemplate.convertAndSend("/topic/transcription/" + clientId, response);
            },
            error -> {
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
    }

    @MessageMapping("/audio-data")
    public void receiveAudioData(@Payload Map<String, Object> payload) {
        String clientId = (String) payload.get("clientId");
        byte[] audioData = (byte[]) payload.get("audioData");
        
        String sessionId = activeStreams.get(clientId);
        if (sessionId != null) {
            // Send audio data to the streaming service
            streamingSpeechToTextService.sendAudioData(sessionId, audioData);
        }
    }

    @MessageMapping("/stop-stream")
    public void stopStream(@Payload Map<String, Object> payload) {
        String clientId = (String) payload.get("clientId");
        String sessionId = activeStreams.remove(clientId);
        
        if (sessionId != null) {
            // Stop the streaming recognition
            streamingSpeechToTextService.stopStreamingRecognition(sessionId);
            
            // Send confirmation to the client
            Map<String, Object> response = new HashMap<>();
            response.put("type", "stopped");
            response.put("sessionId", sessionId);
            
            messagingTemplate.convertAndSend("/topic/transcription/" + clientId, response);
        }
    }
    
    @GetMapping("/api/stream/status")
    @ResponseBody
    public Map<String, Object> getStreamStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("activeStreams", activeStreams.size());
        response.put("streams", activeStreams);
        return response;
    }
} 