package com.aiinterview.service;

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
            LOGGER.info("GOOGLE_APPLICATION_CREDENTIALS environment variable: " + 
                        (credentialsPath != null ? credentialsPath : "Not set"));
            
            // Initialize the Google Cloud Speech client
            LOGGER.info("Attempting to create Google Cloud Speech client...");
            speechClient = SpeechClient.create();
            googleCloudAvailable = true;
            LOGGER.info("Google Cloud Speech client initialized successfully");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Google Cloud Speech client", e);
            googleCloudAvailable = false;
            LOGGER.warning("Falling back to simulated speech recognition");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error initializing Google Cloud Speech client", e);
            googleCloudAvailable = false;
            LOGGER.warning("Falling back to simulated speech recognition");
        }
    }

    public String convertSpeechToText(byte[] audioData) {
        LOGGER.info("Converting speech to text, audio data size: " + audioData.length + " bytes");
        try {
            // Create a simple audio format for the raw audio data
            // This is a fallback in case the audio data doesn't have proper headers
            // Use 16kHz, 16-bit, mono as default (optimal for Google Cloud Speech-to-Text)
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            
            // Create an audio input stream from the byte array
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream ais;
            
            try {
                // Try to get the audio format from the data
                LOGGER.info("Attempting to get audio format from data...");
                ais = AudioSystem.getAudioInputStream(bais);
                format = ais.getFormat();
                LOGGER.info("Processing audio with format: " + format);
            } catch (UnsupportedAudioFileException e) {
                // If the audio data doesn't have proper headers, create a raw audio stream
                LOGGER.warning("Audio data doesn't have proper headers, using raw format: " + format);
                ais = new AudioInputStream(bais, format, audioData.length / format.getFrameSize());
            }
            
            // Check if the audio format is suitable for speech recognition
            // More permissive format checking
            boolean isSuitableFormat = format.getSampleRate() >= 8000 && 
                                      format.getChannels() <= 2 && 
                                      format.getSampleSizeInBits() >= 8;
            
            LOGGER.info("Audio format suitable for speech recognition: " + isSuitableFormat);
            
            if (isSuitableFormat) {
                // Process the audio data for speech recognition
                processAudioForRecognition(audioData, format);
                
                // Wait for recognition to complete (with timeout)
                LOGGER.info("Waiting for recognition to complete (timeout: 10 seconds)...");
                boolean completed = recognitionLatch.await(10, TimeUnit.SECONDS);
                
                if (completed) {
                    String result = recognizedText.get();
                    if (result != null && !result.isEmpty()) {
                        LOGGER.info("Recognition completed successfully");
                        return "Recognized text: " + result;
                    } else {
                        LOGGER.warning("No speech detected in the audio");
                        return "No speech detected in the audio.";
                    }
                } else {
                    LOGGER.warning("Speech recognition timed out");
                    return "Speech recognition timed out. Please try again.";
                }
            } else {
                LOGGER.warning("Audio format not suitable for speech recognition");
                return "Audio received but may not be optimal for speech recognition. " +
                       "For best results, use audio with: 8kHz+ sample rate, mono or stereo, 8+ bit depth.";
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error processing audio", e);
            return "Error processing audio: " + e.getMessage();
        }
    }
    
    private void processAudioForRecognition(byte[] audioData, AudioFormat format) {
        LOGGER.info("Processing audio for recognition, format: " + format);
        
        // Reset the recognition state
        recognizedText.set("");
        // Create a new CountDownLatch instead of trying to reset it
        recognitionLatch = new CountDownLatch(1);
        
        // Start a new thread for processing
        Thread recognitionThread = new Thread(() -> {
            try {
                if (googleCloudAvailable && speechClient != null) {
                    LOGGER.info("Using Google Cloud Speech-to-Text for recognition");
                    // Use Google Cloud Speech-to-Text for real speech recognition
                    recognizeSpeechWithGoogleCloud(audioData, format);
                } else {
                    LOGGER.warning("Google Cloud Speech-to-Text not available, using simulated recognition");
                    // Fallback to simulated recognition if Google Cloud client is not available
                    simulateSpeechRecognition();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in speech recognition", e);
                recognizedText.set("Error in speech recognition: " + e.getMessage());
                recognitionLatch.countDown();
            }
        });
        
        recognitionThread.start();
    }
    
    private void recognizeSpeechWithGoogleCloud(byte[] audioData, AudioFormat format) throws IOException {
        LOGGER.info("Recognizing speech with Google Cloud, audio size: " + audioData.length + " bytes");
        
        try {
            // Configure the recognition
            LOGGER.info("Configuring recognition with sample rate: " + format.getSampleRate() + 
                        ", channels: " + format.getChannels());
            
            RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setLanguageCode("en-US")
                .setSampleRateHertz((int) format.getSampleRate())
                .setAudioChannelCount(format.getChannels())
                .setEnableAutomaticPunctuation(true)
                .setModel("video")  // Use the video model which is better for longer audio
                .build();
            
            // Create the audio content
            ByteString audioBytes = ByteString.copyFrom(audioData);
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build();
            
            LOGGER.info("Sending request to Google Cloud Speech-to-Text API...");
            
            // Perform the transcription
            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();
            
            LOGGER.info("Received response from Google Cloud Speech-to-Text API with " + 
                        results.size() + " results");
            
            StringBuilder transcription = new StringBuilder();
            for (SpeechRecognitionResult result : results) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                transcription.append(alternative.getTranscript()).append(" ");
            }
            
            String finalTranscription = transcription.toString().trim();
            LOGGER.info("Google Cloud Speech recognition result: " + finalTranscription);
            
            if (!finalTranscription.isEmpty()) {
                recognizedText.set(finalTranscription);
            } else {
                LOGGER.warning("No speech detected in the audio (Google Cloud)");
                recognizedText.set("No speech detected in the audio.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in Google Cloud Speech recognition", e);
            throw new IOException("Google Cloud Speech recognition error: " + e.getMessage(), e);
        } finally {
            recognitionLatch.countDown();
        }
    }
    
    private void simulateSpeechRecognition() throws InterruptedException {
        LOGGER.info("Simulating speech recognition");
        
        // Simulate speech recognition processing
        Thread.sleep(1000);
        
        // Simulate recognizing some text
        String simulatedText = "This is a simulated speech recognition result. " +
                          "In a real implementation, this would be the actual recognized text.";
        LOGGER.info("Simulated recognition result: " + simulatedText);
        
        recognizedText.set(simulatedText);
        
        // Signal that recognition is complete
        recognitionLatch.countDown();
    }
    
    public boolean isSendToOpenAI() {
        return sendToOpenAI.get();
    }
    
    public void setSendToOpenAI(boolean send) {
        sendToOpenAI.set(send);
        LOGGER.info("Send to OpenAI set to: " + send);
    }
    
    public boolean isGoogleCloudAvailable() {
        return googleCloudAvailable;
    }
} 