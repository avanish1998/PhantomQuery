package com.phantomquery.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

// Google Cloud Speech-to-Text imports
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import java.util.List;

@Service
public class SpeechToTextService {
    
    private static final Logger LOGGER = Logger.getLogger(SpeechToTextService.class.getName());
    
    private final AtomicReference<String> recognizedText = new AtomicReference<>("");
    private CountDownLatch recognitionLatch = new CountDownLatch(1);
    private final AtomicBoolean sendToOpenAI = new AtomicBoolean(true);
    private SpeechClient speechClient;
    private boolean googleCloudAvailable = false;
    
    public SpeechToTextService() {
        LOGGER.info("Initializing SpeechToTextService...");
        try {
            // Check for Google Cloud credentials
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            
            // Initialize the Google Cloud Speech client
            speechClient = SpeechClient.create();
            googleCloudAvailable = true;
            LOGGER.info("Google Cloud Speech client initialized");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Google Cloud Speech client", e);
            googleCloudAvailable = false;
            LOGGER.warning("Falling back to simulated speech recognition");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error initializing SpeechToTextService", e);
            googleCloudAvailable = false;
        }
    }
    
    public String convertSpeechToText(byte[] audioData) {
        LOGGER.info("Converting speech to text, audio data size: " + audioData.length + " bytes");
        
        // Reset the recognition latch and text
        recognitionLatch = new CountDownLatch(1);
        recognizedText.set("");
        
        // Default audio format (16kHz, 16-bit, mono)
        AudioFormat defaultFormat = new AudioFormat(16000, 16, 1, true, false);
        
        try {
            // Try to get the audio format from the data
            LOGGER.info("Attempting to get audio format from data...");
            AudioFormat format = defaultFormat;
            
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                AudioInputStream ais = AudioSystem.getAudioInputStream(bais);
                format = ais.getFormat();
                LOGGER.info("Audio format: " + format);
            } catch (UnsupportedAudioFileException e) {
                LOGGER.warning("Audio data doesn't have proper headers, using raw format: " + defaultFormat);
                format = defaultFormat;
            }
            
            // Check if the format is suitable for speech recognition
            boolean formatSuitable = isFormatSuitable(format);
            LOGGER.info("Audio format suitable for speech recognition: " + formatSuitable);
            
            if (!formatSuitable) {
                LOGGER.warning("Audio format may not be optimal for recognition: " + format);
            }
            
            // Process the audio for recognition
            LOGGER.info("Processing audio for recognition, format: " + format);
            processAudioForRecognition(audioData, format);
            
            // Wait for recognition to complete
            LOGGER.info("Waiting for recognition to complete (timeout: 10 seconds)...");
            boolean completed = recognitionLatch.await(10, TimeUnit.SECONDS);
            
            if (completed) {
                LOGGER.info("Recognition completed successfully");
                return "Recognized text: " + recognizedText.get();
            } else {
                LOGGER.warning("Recognition timed out");
                return "Recognition timed out";
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error converting speech to text", e);
            return "Error: " + e.getMessage();
        }
    }
    
    private boolean isFormatSuitable(AudioFormat format) {
        // Check if the format is suitable for speech recognition
        // Most speech recognition systems work best with:
        // - Sample rate >= 8000 Hz
        // - Channels <= 2 (mono or stereo)
        // - Bits per sample >= 8
        return format.getSampleRate() >= 8000 && 
               format.getChannels() <= 2 && 
               format.getSampleSizeInBits() >= 8;
    }
    
    private void processAudioForRecognition(byte[] audioData, AudioFormat format) {
        // Start a thread for recognition to avoid blocking
        Thread recognitionThread = new Thread(() -> {
            try {
                if (googleCloudAvailable) {
                    LOGGER.info("Using Google Cloud Speech-to-Text for recognition");
                    recognizeSpeechWithGoogleCloud(audioData, format);
                } else {
                    LOGGER.info("Using simulated speech recognition");
                    simulateSpeechRecognition();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in recognition thread", e);
                recognizedText.set("Error during recognition: " + e.getMessage());
            } finally {
                recognitionLatch.countDown();
            }
        });
        
        recognitionThread.start();
    }
    
    private void recognizeSpeechWithGoogleCloud(byte[] audioData, AudioFormat format) throws IOException {
        LOGGER.info("Recognizing speech with Google Cloud, audio size: " + audioData.length + " bytes");
        
        // Configure the recognition
        LOGGER.info("Configuring recognition with sample rate: " + format.getSampleRate() + 
                   ", channels: " + format.getChannels());
        
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setLanguageCode("en-US")
                .setSampleRateHertz((int) format.getSampleRate())
                .setAudioChannelCount(format.getChannels())
                .setEnableAutomaticPunctuation(true)
                .setModel("video")
                .build();
        
        // Create the audio content
        ByteString audioBytes = ByteString.copyFrom(audioData);
        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build();
        
        // Perform the transcription
        LOGGER.info("Sending request to Google Cloud Speech-to-Text API...");
        RecognizeResponse response = speechClient.recognize(config, audio);
        List<SpeechRecognitionResult> results = response.getResultsList();
        
        LOGGER.info("Received response from Google Cloud Speech-to-Text API with " + 
                   results.size() + " results");
        
        // Process the results
        StringBuilder transcription = new StringBuilder();
        for (SpeechRecognitionResult result : results) {
            List<SpeechRecognitionAlternative> alternatives = result.getAlternativesList();
            for (SpeechRecognitionAlternative alternative : alternatives) {
                String text = alternative.getTranscript();
                LOGGER.info("Google Cloud Speech recognition result: " + text);
                transcription.append(text).append(" ");
            }
        }
        
        recognizedText.set(transcription.toString().trim());
    }
    
    private void simulateSpeechRecognition() throws InterruptedException {
        // Simulate a delay for testing purposes
        Thread.sleep(1000);
        recognizedText.set("This is a simulated speech recognition result for testing purposes.");
    }
    
    public boolean isSendToOpenAI() {
        return sendToOpenAI.get();
    }
    
    public void setSendToOpenAI(boolean send) {
        sendToOpenAI.set(send);
    }
    
    public boolean isGoogleCloudAvailable() {
        return googleCloudAvailable;
    }
} 