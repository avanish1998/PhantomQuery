package com.aiinterview.service;

import com.google.cloud.speech.v1.*;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class SpeechToTextService {
    private final SpeechClient speechClient;

    public SpeechToTextService() throws IOException {
        this.speechClient = SpeechClient.create();
    }

    public String convertSpeechToText(byte[] audioData) {
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("en-US")
                .setModel("latest_long")
                .build();

        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(com.google.protobuf.ByteString.copyFrom(audioData))
                .build();

        RecognizeResponse response = speechClient.recognize(config, audio);
        
        return response.getResultsList().stream()
                .findFirst()
                .map(result -> result.getAlternativesList().get(0).getTranscript())
                .orElse("");
    }
} 