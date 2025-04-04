package com.aiinterview.controller;

import com.aiinterview.service.SpeechToTextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechToTextService speechToTextService;

    @Autowired
    public SpeechController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> convertSpeechToText(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            byte[] audioData = file.getBytes();
            String result = speechToTextService.convertSpeechToText(audioData);
            
            response.put("success", true);
            response.put("transcription", result);
            response.put("message", "Speech recognition completed successfully");
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Error processing audio file: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSpeechRecognitionStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ready");
        response.put("message", "Speech recognition service is ready");
        return ResponseEntity.ok(response);
    }
} 