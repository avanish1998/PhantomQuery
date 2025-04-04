package com.aiinterview.controller;

import com.aiinterview.service.AudioCaptureService;
import com.aiinterview.service.SpeechToTextService;
import com.aiinterview.service.AIQueryProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class InterviewController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AudioCaptureService audioCaptureService;

    @Autowired
    private SpeechToTextService speechToTextService;

    @Autowired
    private AIQueryProcessor aiQueryProcessor;

    @MessageMapping("/start-audio-capture")
    @SendTo("/topic/status")
    public String startAudioCapture() {
        try {
            audioCaptureService.startCapture();
            return "Audio capture started";
        } catch (Exception e) {
            return "Error starting audio capture: " + e.getMessage();
        }
    }

    @MessageMapping("/stop-audio-capture")
    @SendTo("/topic/status")
    public String stopAudioCapture() {
        try {
            byte[] audioData = audioCaptureService.stopCapture();
            String transcript = speechToTextService.convertSpeechToText(audioData);
            String aiResponse = aiQueryProcessor.processQuery(transcript);
            
            messagingTemplate.convertAndSend("/topic/response", 
                "Question: " + transcript + "\nAnswer: " + aiResponse);
            
            return "Audio capture stopped and processed";
        } catch (Exception e) {
            return "Error processing audio: " + e.getMessage();
        }
    }
} 