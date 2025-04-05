package com.phantomquery.service;

import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

@Service
public class SystemAudioCaptureService {
    private TargetDataLine line;
    private AudioFormat format;
    private Thread captureThread;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final SpeechToTextService speechToTextService;
    private final OpenAiService openAiService;
    private final BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>();
    private static final int BUFFER_SIZE = 4096;
    private static final int MAX_BUFFER_COUNT = 20; // Process every 20 buffers for better recognition
    private final AtomicReference<String> lastTranscription = new AtomicReference<>("");
    private final AtomicReference<String> lastAiResponse = new AtomicReference<>("");
    private ByteArrayOutputStream audioDataStream;

    public SystemAudioCaptureService(SpeechToTextService speechToTextService, OpenAiService openAiService) {
        this.speechToTextService = speechToTextService;
        this.openAiService = openAiService;
        this.audioDataStream = new ByteArrayOutputStream();
    }

    public void startCapture() {
        if (isRecording.get()) {
            return;
        }

        try {
            // Reset the audio data stream
            audioDataStream = new ByteArrayOutputStream();
            
            // Try to find a supported format
            AudioFormat[] formats = {
                new AudioFormat(44100, 16, 2, true, false),
                new AudioFormat(44100, 16, 1, true, false),
                new AudioFormat(22050, 16, 2, true, false),
                new AudioFormat(22050, 16, 1, true, false),
                new AudioFormat(16000, 16, 2, true, false),
                new AudioFormat(16000, 16, 1, true, false),
                new AudioFormat(8000, 16, 2, true, false),
                new AudioFormat(8000, 16, 1, true, false)
            };
            
            // Try to find a supported format
            format = null;
            DataLine.Info info = null;
            
            for (AudioFormat testFormat : formats) {
                DataLine.Info testInfo = new DataLine.Info(TargetDataLine.class, testFormat);
                if (AudioSystem.isLineSupported(testInfo)) {
                    format = testFormat;
                    info = testInfo;
                    System.out.println("Found compatible format: " + format);
                    break;
                }
            }
            
            if (format == null) {
                // If no specific format is supported, try to get the default format
                Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
                for (Mixer.Info mixerInfo : mixerInfos) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    Line.Info[] lineInfos = mixer.getTargetLineInfo();
                    
                    for (Line.Info lineInfo : lineInfos) {
                        if (lineInfo instanceof DataLine.Info) {
                            DataLine.Info dataLineInfo = (DataLine.Info) lineInfo;
                            AudioFormat[] supportedFormats = dataLineInfo.getFormats();
                            
                            if (supportedFormats.length > 0) {
                                format = supportedFormats[0];
                                info = dataLineInfo;
                                System.out.println("Using default format from mixer: " + format);
                                break;
                            }
                        }
                    }
                    
                    if (format != null) {
                        break;
                    }
                }
            }
            
            if (format == null) {
                throw new RuntimeException("No compatible audio format found for your system");
            }
            
            // Get and open the target data line
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            isRecording.set(true);

            // Start the capture thread
            captureThread = new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bufferCount = 0;
                
                while (isRecording.get()) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        // Add the buffer to the queue
                        byte[] audioData = new byte[count];
                        System.arraycopy(buffer, 0, audioData, 0, count);
                        audioBuffer.offer(audioData);
                        audioDataStream.write(audioData, 0, count);
                        bufferCount++;
                        
                        // Process accumulated audio data periodically
                        if (bufferCount >= MAX_BUFFER_COUNT) {
                            processAccumulatedAudio();
                            bufferCount = 0;
                        }
                    }
                }
            });
            
            captureThread.start();
            System.out.println("Started system audio capture with format: " + format);
            
        } catch (LineUnavailableException e) {
            System.err.println("Failed to start audio capture: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to start audio capture: " + e.getMessage(), e);
        }
    }
    
    private void processAccumulatedAudio() {
        try {
            // Calculate total size of accumulated audio
            int totalSize = 0;
            for (byte[] buffer : audioBuffer) {
                totalSize += buffer.length;
            }
            
            // Combine all buffers into a single byte array
            byte[] combinedAudio = new byte[totalSize];
            int offset = 0;
            while (!audioBuffer.isEmpty()) {
                byte[] buffer = audioBuffer.poll();
                System.arraycopy(buffer, 0, combinedAudio, offset, buffer.length);
                offset += buffer.length;
            }
            
            // Process the combined audio data
            String transcription = speechToTextService.convertSpeechToText(combinedAudio);
            
            // Only process if we have meaningful transcription
            if (transcription != null && !transcription.isEmpty() && 
                !transcription.contains("No speech detected") && 
                !transcription.contains("Error processing audio")) {
                
                System.out.println("Transcription: " + transcription);
                lastTranscription.set(transcription);
                
                // We no longer automatically send to OpenAI
                // The UI will handle this through the "Get Answers" button
                lastAiResponse.set("Click 'Get Answers' in the UI to process with OpenAI");
            }
            
        } catch (Exception e) {
            System.err.println("Error processing accumulated audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopCapture() {
        isRecording.set(false);
        if (line != null) {
            line.stop();
            line.close();
        }
        if (captureThread != null) {
            try {
                captureThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Stopped system audio capture");
    }

    public boolean isCapturing() {
        return isRecording.get();
    }
    
    public String getLastTranscription() {
        return lastTranscription.get();
    }
    
    public String getLastAiResponse() {
        return lastAiResponse.get();
    }
    
    public byte[] getCapturedAudio() {
        return audioDataStream.toByteArray();
    }
} 