package com.aiinterview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aiinterview.controller.SimpleWebSocketController;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
    
    @Autowired
    private SimpleWebSocketController simpleWebSocketController;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple memory-based message broker to send messages to clients
        config.enableSimpleBroker("/topic");
        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoints
        registry.addEndpoint("/audio-stream/websocket")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // Enable SockJS fallback
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Increase message size limit to 16MB
        registration.setMessageSizeLimit(16 * 1024 * 1024);
        // Increase send buffer size to 16MB
        registration.setSendBufferSizeLimit(16 * 1024 * 1024);
        // Increase send time limit to 10 seconds
        registration.setSendTimeLimit(10000);
    }
    
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        // Add a converter for binary messages
        messageConverters.add(new ByteArrayMessageConverter());
        return true;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simpleWebSocketController, "/simple-websocket")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Set maximum text message size to 1MB
        container.setMaxTextMessageBufferSize(1024 * 1024);
        // Set maximum binary message size to 1MB
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        return container;
    }
} 