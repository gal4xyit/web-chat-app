package com.gal4xyit.chat.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.ArrayList;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ConnectedUsersService connectedUsersService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(
            @Payload ChatMessage chatMessage,
            SimpMessageHeaderAccessor headerAccessor
    ){
        Principal principal = headerAccessor.getUser();

        if (principal == null) {
            System.err.println("sendMessage called without authenticated principal in WebSocket session!");
            return;
        }

        String authenticatedUsername = null;
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            authenticatedUsername = oauth2User.getAttribute("preferred_username");
            if (!StringUtils.hasText(authenticatedUsername)) {
                authenticatedUsername = oauth2User.getName();
            }
        } else {
            authenticatedUsername = principal.getName();
        }

        if (!StringUtils.hasText(authenticatedUsername)) {
            System.err.println("Could not determine username from principal for sendMessage.");
            return;
        }

        chatMessage.setSender(authenticatedUsername);

        if (chatMessage.getType() == MessageType.CHAT) {
            if (!StringUtils.hasText(chatMessage.getContent())) {
                return;
            }
            ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
            messagingTemplate.convertAndSend("/topic/public", savedMessage);
        }
    }

    @MessageMapping("/chat.addUser")
    public void addUser(
            @Payload ChatMessage joinRequestPayload,
            SimpMessageHeaderAccessor headerAccessor
    ){
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            System.err.println("addUser called without authenticated principal in WebSocket session!");
            return;
        }

        String authenticatedUsername = null;
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            authenticatedUsername = oauth2User.getAttribute("preferred_username");
            if (!StringUtils.hasText(authenticatedUsername)) {
                authenticatedUsername = oauth2User.getName();
            }
        } else {
            authenticatedUsername = principal.getName();
        }

        if (!StringUtils.hasText(authenticatedUsername)) {
            System.err.println("Could not determine username from principal for addUser.");
            return;
        }

        String sessionId = headerAccessor.getSessionId();
        if (sessionId == null) { return; }

        headerAccessor.getSessionAttributes().put("username", authenticatedUsername);

        boolean shouldBroadcastPublicJoinEvent = connectedUsersService.addUserSession(authenticatedUsername, sessionId);

        ChatMessage broadcastMessage = ChatMessage.builder()
                .sender(authenticatedUsername)
                .type(MessageType.JOIN)
                .connectedUsers(new ArrayList<>(connectedUsersService.getConnectedUsers()))
                .build();

        if (shouldBroadcastPublicJoinEvent) {
            broadcastMessage.setContent(authenticatedUsername + " joined!");
            chatMessageRepository.save(broadcastMessage);
        } else {
            broadcastMessage.setContent(null);
        }
        messagingTemplate.convertAndSend("/topic/public", broadcastMessage);
    }
}