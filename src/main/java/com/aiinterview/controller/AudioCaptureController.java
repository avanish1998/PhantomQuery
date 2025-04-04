package com.aiinterview.controller;

import com.aiinterview.service.SystemAudioCaptureService;
import com.aiinterview.service.SpeechToTextService;
import com.aiinterview.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/capture")
public class AudioCaptureController {

    private final SystemAudioCaptureService audioCaptureService;
    private final SpeechToTextService speechToTextService;
    private final OpenAiService openAiService;

    @Autowired
    public AudioCaptureController(SystemAudioCaptureService audioCaptureService, 
                                 SpeechToTextService speechToTextService,
                                 OpenAiService openAiService) {
        this.audioCaptureService = audioCaptureService;
        this.speechToTextService = speechToTextService;
        this.openAiService = openAiService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCapture() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            audioCaptureService.startCapture();
            response.put("success", true);
            response.put("message", "Audio capture started successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to start audio capture: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            audioCaptureService.stopCapture();
            response.put("success", true);
            response.put("message", "Audio capture stopped successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to stop audio capture: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCaptureStatus() {
        Map<String, Object> response = new HashMap<>();
        
        boolean isCapturing = audioCaptureService.isCapturing();
        String lastTranscription = audioCaptureService.getLastTranscription();
        String lastAiResponse = audioCaptureService.getLastAiResponse();
        boolean sendToOpenAI = speechToTextService.isSendToOpenAI();
        
        response.put("isCapturing", isCapturing);
        response.put("lastTranscription", lastTranscription);
        response.put("lastAiResponse", lastAiResponse);
        response.put("sendToOpenAI", sendToOpenAI);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/toggle-openai")
    public ResponseEntity<Map<String, Object>> toggleOpenAI(@RequestBody Map<String, Boolean> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Boolean enable = request.get("enable");
            if (enable == null) {
                response.put("success", false);
                response.put("error", "Missing 'enable' parameter in request body");
                return ResponseEntity.badRequest().body(response);
            }
            
            speechToTextService.setSendToOpenAI(enable);
            
            response.put("success", true);
            response.put("message", "OpenAI processing " + (enable ? "enabled" : "disabled"));
            response.put("sendToOpenAI", enable);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to toggle OpenAI processing: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/process-transcription")
    public ResponseEntity<Map<String, Object>> processTranscription(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String transcription = request.get("transcription");
            if (transcription == null || transcription.isEmpty()) {
                response.put("success", false);
                response.put("error", "Missing or empty transcription in request body");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Process the transcription with OpenAI
            String aiResponse = openAiService.getCompletion(transcription);
            
            response.put("success", true);
            response.put("aiResponse", aiResponse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to process transcription: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/google-cloud-status")
    public ResponseEntity<Map<String, Object>> getGoogleCloudStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean googleCloudAvailable = speechToTextService.isGoogleCloudAvailable();
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            
            response.put("success", true);
            response.put("googleCloudAvailable", googleCloudAvailable);
            response.put("credentialsPath", credentialsPath != null ? credentialsPath : "Not set");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to get Google Cloud status: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
} 