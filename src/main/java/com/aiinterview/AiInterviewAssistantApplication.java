package com.aiinterview;

import com.aiinterview.ui.SystemTrayUI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AiInterviewAssistantApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AiInterviewAssistantApplication.class, args);
        
        // Initialize system tray
        SystemTrayUI systemTrayUI = context.getBean(SystemTrayUI.class);
        systemTrayUI.initialize();
    }
} 