package com.phantomquery.service;

import com.phantomquery.model.Conversation;
import com.phantomquery.model.Message;
import com.phantomquery.repository.ConversationRepository;
import com.phantomquery.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAiService openAiService;

    @Autowired
    public ConversationService(ConversationRepository conversationRepository,
                             MessageRepository messageRepository,
                             OpenAiService openAiService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.openAiService = openAiService;
    }

    public Conversation createConversation(String title) {
        Conversation conversation = new Conversation(title);
        return conversationRepository.save(conversation);
    }

    public Optional<Conversation> getConversation(String id) {
        return conversationRepository.findById(id);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll();
    }

    public Message addUserMessage(String conversationId, String content) {
        Message message = new Message(content, "user", conversationId);
        message = messageRepository.save(message);
        
        // Get AI response
        String aiResponse = openAiService.getCompletion(content);
        Message aiMessage = new Message(aiResponse, "assistant", conversationId);
        return messageRepository.save(aiMessage);
    }

    public List<Message> getConversationMessages(String conversationId) {
        return messageRepository.findByConversation_Id(conversationId);
    }

    public void deleteConversation(String id) {
        conversationRepository.deleteById(id);
    }
} 