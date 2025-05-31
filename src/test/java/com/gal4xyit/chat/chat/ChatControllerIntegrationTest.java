package com.gal4xyit.chat.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@Import(ChatControllerIntegrationTest.WebSocketTestSecurityConfig.class)
public class ChatControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME_FOR_WEBSOCKET = "stompUser";

    private final BlockingQueue<ChatMessage> receivedMessages = new LinkedBlockingDeque<>();

    @TestConfiguration
    static class OidcMockBeansConfiguration {
        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ClientRegistrationRepository.class);
        }
        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    private static DefaultOidcUser getInterceptorTestUser() {
        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put("sub", "stomp-user-sub-interceptor");
        idTokenClaims.put("preferred_username", TEST_USERNAME_FOR_WEBSOCKET);
        idTokenClaims.put("email", TEST_USERNAME_FOR_WEBSOCKET + "@example.com");
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", List.of("USER"));
        idTokenClaims.put("realm_access", realmAccess);
        OidcIdToken idToken = new OidcIdToken("fake-interceptor-token", Instant.now(), Instant.now().plusSeconds(3600), idTokenClaims);
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        authorities.add(new OidcUserAuthority(idToken, null));
        return new DefaultOidcUser(authorities, idToken, "preferred_username");
    }

    @TestConfiguration
    static class WebSocketTestSecurityConfig implements WebSocketMessageBrokerConfigurer {
        @Override
        public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.interceptors(new ChannelInterceptor() {
                @Override
                public Message<?> preSend(Message<?> message, MessageChannel channel) {
                    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                    if (accessor != null) {
                        if (accessor.getUser() == null) {
                            DefaultOidcUser userToInject = getInterceptorTestUser();
                            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                                    userToInject,
                                    new ArrayList<>(userToInject.getAuthorities()),
                                    "keycloak"
                            );
                            accessor.setUser(authentication);
                            if (accessor.getSessionAttributes() != null) {
                                accessor.getSessionAttributes().put("username", userToInject.getAttribute("preferred_username"));
                            }
                        }
                    }
                    return message;
                }
            });
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        chatMessageRepository.deleteAllInBatch();

        List<Transport> transports = new ArrayList<>(1);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        this.stompClient = new WebSocketStompClient(sockJsClient);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();

        converter.setObjectMapper(this.objectMapper);

        this.stompClient.setMessageConverter(converter);

        String URL = "ws://localhost:" + port + "/ws";

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                System.out.println("Test STOMP Client Connected: " + session.getSessionId());
                session.subscribe("/topic/public", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ChatMessage.class;
                    }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        if (payload instanceof ChatMessage) {
                            receivedMessages.add((ChatMessage) payload);
                        } else if (payload != null) {
                            System.err.println("Received unexpected STOMP payload type: " + payload.getClass().getName());
                            if (payload instanceof String && objectMapper != null) {
                                try {
                                    ChatMessage msg = objectMapper.readValue((String) payload, ChatMessage.class);
                                    receivedMessages.add(msg);
                                    System.out.println("Manually parsed String payload in test to ChatMessage.");
                                } catch (Exception e) {
                                    System.err.println("Failed to manually parse String payload in test: " + e.getMessage());
                                }
                            }
                        } else {
                            System.err.println("Received null STOMP payload.");
                        }
                    }
                });
                ChatMessage joinMessage = ChatMessage.builder().sender(TEST_USERNAME_FOR_WEBSOCKET).type(MessageType.JOIN).build();
                session.send("/app/chat.addUser", joinMessage);
            }
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("STOMP Client Exception ("+command+"): " + exception.getMessage());
                exception.printStackTrace();
            }
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("STOMP Client Transport Error: " + exception.getMessage());
                exception.printStackTrace();
            }
        };

        this.stompSession = this.stompClient.connectAsync(URL, sessionHandler).get(10, TimeUnit.SECONDS);
        assertNotNull(this.stompSession, "STOMP session should not be null after connect");
        assertThat(this.stompSession.isConnected()).isTrue();

        ChatMessage initialJoinBroadcast = receivedMessages.poll(2, TimeUnit.SECONDS);
        assertNotNull(initialJoinBroadcast, "Did not receive the broadcast from our own JOIN message within setUp");
        assertThat(initialJoinBroadcast.getType()).isEqualTo(MessageType.JOIN);
        assertThat(initialJoinBroadcast.getSender()).isEqualTo(TEST_USERNAME_FOR_WEBSOCKET);

        receivedMessages.clear();
    }

    @AfterEach
    void tearDown() {
        if (this.stompSession != null && this.stompSession.isConnected()) {
            this.stompSession.disconnect();
        }
        if (this.stompClient != null && this.stompClient.isRunning()) {
            this.stompClient.stop();
        }
    }

    @Test
    void sendPublicChatMessage_messageIsSavedAndBroadcast() throws Exception {
        String messageContent = "Hello from integration test!";
        ChatMessage messageToSend = ChatMessage.builder()
                .sender(TEST_USERNAME_FOR_WEBSOCKET)
                .content(messageContent)
                .type(MessageType.CHAT)
                .build();

        stompSession.send("/app/chat.sendMessage", messageToSend);

        ChatMessage receivedBroadcast = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedBroadcast, "Did not receive broadcast CHAT message on /topic/public");
        assertThat(receivedBroadcast.getType()).isEqualTo(MessageType.CHAT);
        assertThat(receivedBroadcast.getContent()).isEqualTo(messageContent);
        assertThat(receivedBroadcast.getSender()).isEqualTo(TEST_USERNAME_FOR_WEBSOCKET);
        assertNotNull(receivedBroadcast.getTimestamp(), "Broadcasted message should have a server-set timestamp");
        assertNotNull(receivedBroadcast.getId(), "Broadcasted message should have a server-set ID after save");

        List<ChatMessage> savedChatMessages = chatMessageRepository.findByTypeOrderByTimestampAsc(MessageType.CHAT);
        assertThat(savedChatMessages).hasSize(1);

        ChatMessage savedDbMessage = savedChatMessages.get(0);
        assertThat(savedDbMessage.getContent()).isEqualTo(messageContent);
        assertThat(savedDbMessage.getSender()).isEqualTo(TEST_USERNAME_FOR_WEBSOCKET);
        assertThat(savedDbMessage.getType()).isEqualTo(MessageType.CHAT);
        assertNotNull(savedDbMessage.getTimestamp());
        assertNotNull(savedDbMessage.getId());
        assertThat(savedDbMessage.getId()).isEqualTo(receivedBroadcast.getId());
    }
}