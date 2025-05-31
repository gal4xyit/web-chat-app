package com.gal4xyit.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gal4xyit.chat.chat.ChatMessage;
import com.gal4xyit.chat.chat.ChatMessageRepository;
import com.gal4xyit.chat.chat.MessageType;
import com.gal4xyit.chat.config.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageRestController.class)
@Import({SecurityConfig.class, MessageRestControllerTest.MockBeanConfiguration.class})
public class MessageRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private DefaultOidcUser testUser;

    @TestConfiguration
    static class MockBeanConfiguration {
        @Bean
        public ChatMessageRepository chatMessageRepository() {
            return Mockito.mock(ChatMessageRepository.class);
        }

        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ClientRegistrationRepository.class);
        }

        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }


    @BeforeEach
    void setUp() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test-user-sub");
        claims.put("preferred_username", "testControllerUser");
        OidcIdToken idToken = new OidcIdToken("fake-controller-token", Instant.now(), Instant.now().plusSeconds(3600), claims);
        testUser = new DefaultOidcUser(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")), idToken, "preferred_username");
    }

    @Test
    void getMessageHistory_whenAuthenticatedAndMessagesExist_shouldReturnMessages() throws Exception {
        ChatMessage msg1 = ChatMessage.builder().id(1L).sender("userA").content("Hello").type(MessageType.CHAT).timestamp(LocalDateTime.now().minusHours(1)).build();
        ChatMessage msg2 = ChatMessage.builder().id(2L).sender("userB").content("Hi").type(MessageType.CHAT).timestamp(LocalDateTime.now().minusHours(2)).build();


        List<ChatMessage> mockMessagesFromRepo = new ArrayList<>(List.of(msg1, msg2));

        when(this.chatMessageRepository.findTop100ByOrderByTimestampDesc()).thenReturn(mockMessagesFromRepo);
        mockMvc.perform(get("/api/messages/history")
                        .with(oidcLogin().oidcUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value(msg2.getContent()))
                .andExpect(jsonPath("$[1].content").value(msg1.getContent()));
    }

    @Test
    void getMessageHistory_whenAuthenticatedAndNoMessagesExist_shouldReturnEmptyList() throws Exception {
        when(this.chatMessageRepository.findTop100ByOrderByTimestampDesc()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/messages/history")
                        .with(oidcLogin().oidcUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getMessageHistory_whenNotAuthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/messages/history"))
                .andExpect(status().isUnauthorized());
    }
}