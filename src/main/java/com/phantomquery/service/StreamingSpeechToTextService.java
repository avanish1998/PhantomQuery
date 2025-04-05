package com.phantomquery.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class StreamingSpeechToTextService {

    private static final Logger LOGGER = Logger.getLogger(StreamingSpeechToTextService.class.getName());
    
    private SpeechClient speechClient;
    private SpeechSettings speechSettings;
    private boolean googleCloudAvailable = false;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Map to store active streaming sessions
    private final Map<String, Object> responseObservers = new ConcurrentHashMap<>();
    private final Map<String, Object> streamingCallables = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        LOGGER.info("Initializing StreamingSpeechToTextService...");
        try {
            // Check for Google Cloud credentials
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            LOGGER.info("GOOGLE_APPLICATION_CREDENTIALS environment variable: " + 
                        (credentialsPath != null ? credentialsPath : "Not set"));
            
            // Initialize Google Cloud Speech client
            LOGGER.info("Attempting to create Google Cloud Speech client...");
            
            // Create credentials provider
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
            
            // Create speech settings
            speechSettings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();
            
            // Create speech client
            speechClient = SpeechClient.create(speechSettings);
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
    
    @PreDestroy
    public void cleanup() {
        LOGGER.info("Cleaning up StreamingSpeechToTextService...");
        executorService.shutdown();
        
        // Close all active streams
        for (String sessionId : responseObservers.keySet()) {
            stopStreamingRecognition(sessionId);
        }
        
        // Close the speech client
        if (speechClient != null) {
            speechClient.close();
        }
    }
    
    public void startStreamingRecognition(String sessionId, Consumer<String> transcriptionCallback, Consumer<String> errorCallback) {
        if (!googleCloudAvailable || speechClient == null) {
            LOGGER.warning("Google Cloud Speech-to-Text not available, using simulated recognition");
            errorCallback.accept("Google Cloud Speech-to-Text not available. Check logs for details.");
            return;
        }
        
        LOGGER.info("Starting streaming recognition for session: " + sessionId);
        
        try {
            // Create recognition config
            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setLanguageCode("en-US")
                    .setSampleRateHertz(16000)
                    .setAudioChannelCount(1)
                    .build();
            
            // Create streaming config
            StreamingRecognitionConfig streamingRecognitionConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(true)
                    .build();
            
            // Create response observer using reflection to avoid direct dependency on specific classes
            Object responseObserver = createResponseObserver(sessionId, transcriptionCallback, errorCallback);
            
            // Create streaming callable using reflection
            Object streamingCallable = createStreamingCallable();
            
            // Start the stream using reflection
            startStream(streamingCallable, responseObserver);
            
            // Send the initial config using reflection
            sendInitialConfig(streamingCallable, streamingRecognitionConfig);
            
            // Store the response observer and streaming callable
            responseObservers.put(sessionId, responseObserver);
            streamingCallables.put(sessionId, streamingCallable);
            
            LOGGER.info("Streaming recognition started for session: " + sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting streaming recognition", e);
            errorCallback.accept("Error starting streaming recognition: " + e.getMessage());
        }
    }
    
    private Object createResponseObserver(String sessionId, Consumer<String> transcriptionCallback, Consumer<String> errorCallback) {
        try {
            // Use reflection to create the response observer
            Class<?> responseObserverClass = Class.forName("com.google.cloud.speech.v1.SpeechClient$StreamingRecognizeResponseObserver");
            return java.lang.reflect.Proxy.newProxyInstance(
                responseObserverClass.getClassLoader(),
                new Class<?>[] { responseObserverClass },
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("onNext".equals(methodName)) {
                        Object response = args[0];
                        processResponse(response, transcriptionCallback);
                        return null;
                    } else if ("onError".equals(methodName)) {
                        Throwable t = (Throwable) args[0];
                        LOGGER.log(Level.SEVERE, "Error in streaming recognition", t);
                        errorCallback.accept("Error in streaming recognition: " + t.getMessage());
                        responseObservers.remove(sessionId);
                        streamingCallables.remove(sessionId);
                        return null;
                    } else if ("onComplete".equals(methodName)) {
                        LOGGER.info("Streaming recognition completed for session: " + sessionId);
                        responseObservers.remove(sessionId);
                        streamingCallables.remove(sessionId);
                        return null;
                    }
                    return method.invoke(proxy, args);
                }
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating response observer", e);
            throw new RuntimeException("Error creating response observer", e);
        }
    }
    
    private void processResponse(Object response, Consumer<String> transcriptionCallback) {
        try {
            // Use reflection to process the response
            java.lang.reflect.Method getResultsListMethod = response.getClass().getMethod("getResultsList");
            java.util.List<?> resultsList = (java.util.List<?>) getResultsListMethod.invoke(response);
            
            for (Object result : resultsList) {
                java.lang.reflect.Method getAlternativesListMethod = result.getClass().getMethod("getAlternativesList");
                java.util.List<?> alternativesList = (java.util.List<?>) getAlternativesListMethod.invoke(result);
                
                if (!alternativesList.isEmpty()) {
                    Object alternative = alternativesList.get(0);
                    java.lang.reflect.Method getTranscriptMethod = alternative.getClass().getMethod("getTranscript");
                    String transcript = (String) getTranscriptMethod.invoke(alternative);
                    
                    if (StringUtils.hasText(transcript)) {
                        LOGGER.info("Received transcription: " + transcript);
                        transcriptionCallback.accept(transcript);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing response", e);
        }
    }
    
    private Object createStreamingCallable() {
        try {
            // Use reflection to create the streaming callable
            java.lang.reflect.Method streamingRecognizeCallableMethod = speechClient.getClass().getMethod("streamingRecognizeCallable");
            return streamingRecognizeCallableMethod.invoke(speechClient);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating streaming callable", e);
            throw new RuntimeException("Error creating streaming callable", e);
        }
    }
    
    private void startStream(Object streamingCallable, Object responseObserver) {
        try {
            // Use reflection to start the stream
            java.lang.reflect.Method startMethod = streamingCallable.getClass().getMethod("start", responseObserver.getClass());
            startMethod.invoke(streamingCallable, responseObserver);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting stream", e);
            throw new RuntimeException("Error starting stream", e);
        }
    }
    
    private void sendInitialConfig(Object streamingCallable, StreamingRecognitionConfig streamingRecognitionConfig) {
        try {
            // Create the initial request
            StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.EMPTY)
                    .setStreamingConfig(streamingRecognitionConfig)
                    .build();
            
            // Use reflection to send the initial config
            java.lang.reflect.Method sendMethod = streamingCallable.getClass().getMethod("send", StreamingRecognizeRequest.class);
            sendMethod.invoke(streamingCallable, request);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error sending initial config", e);
            throw new RuntimeException("Error sending initial config", e);
        }
    }
    
    public void sendAudioData(String sessionId, byte[] audioData) {
        if (!googleCloudAvailable || speechClient == null) {
            LOGGER.warning("Google Cloud Speech-to-Text not available, cannot send audio data");
            return;
        }
        
        Object streamingCallable = streamingCallables.get(sessionId);
        if (streamingCallable == null) {
            LOGGER.warning("No active streaming callable for session: " + sessionId);
            return;
        }
        
        try {
            // Create audio content request
            StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(audioData))
                    .build();
            
            // Use reflection to send the audio data
            java.lang.reflect.Method sendMethod = streamingCallable.getClass().getMethod("send", StreamingRecognizeRequest.class);
            sendMethod.invoke(streamingCallable, request);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error sending audio data", e);
        }
    }
    
    public void stopStreamingRecognition(String sessionId) {
        LOGGER.info("Stopping streaming recognition for session: " + sessionId);
        
        Object streamingCallable = streamingCallables.remove(sessionId);
        if (streamingCallable != null) {
            try {
                // Use reflection to close the stream
                java.lang.reflect.Method closeSendMethod = streamingCallable.getClass().getMethod("closeSend");
                closeSendMethod.invoke(streamingCallable);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error closing streaming callable", e);
            }
        }
        
        responseObservers.remove(sessionId);
        LOGGER.info("Streaming recognition stopped for session: " + sessionId);
    }
    
    public boolean isGoogleCloudAvailable() {
        return googleCloudAvailable;
    }
} 