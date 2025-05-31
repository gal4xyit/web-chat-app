package com.gal4xyit.chat.config;

import com.gal4xyit.chat.chat.ChatMessage;
import com.gal4xyit.chat.chat.ChatMessageRepository;
import com.gal4xyit.chat.chat.ConnectedUsersService;
import com.gal4xyit.chat.chat.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConnectedUsersService connectedUsersService;
    private final ChatMessageRepository chatMessageRepository;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String sessionId = headerAccessor.getSessionId();

        if (username != null && sessionId != null) {
            log.info("Session {} for user {} disconnected.", sessionId, username);

            boolean userIsNowFullyOffline = connectedUsersService.removeUserSession(username, sessionId);

            ChatMessage messageToBroadcast = ChatMessage.builder()
                    .sender(username)
                    .connectedUsers(new ArrayList<>(connectedUsersService.getConnectedUsers()))
                    .build();

            if (userIsNowFullyOffline) {
                log.info("User {} is now fully offline. Broadcasting and saving LEAVE event.", username);
                messageToBroadcast.setType(MessageType.LEAVE);
                messageToBroadcast.setContent(username + " left!");
                chatMessageRepository.save(messageToBroadcast);
            } else {
                log.info("User {} still has other active sessions. Broadcasting updated user list.", username);
                messageToBroadcast.setType(MessageType.JOIN);
                messageToBroadcast.setContent(null);
            }
            messagingTemplate.convertAndSend("/topic/public", messageToBroadcast);
        } else {
            log.warn("Disconnected session without username in attributes or missing sessionId. Attributes: {}",
                    headerAccessor.getSessionAttributes());
        }
    }
}