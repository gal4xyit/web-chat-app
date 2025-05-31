package com.gal4xyit.chat.controller;

import com.gal4xyit.chat.chat.ChatMessage;
import com.gal4xyit.chat.chat.ChatMessageRepository;
import com.gal4xyit.chat.chat.MessageType;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MessageRestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ClientRegistrationRepository.class);
        }

        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    private DefaultOidcUser testUser;
    private String testUsername = "historyApiUser";

    private DefaultOidcUser createMockOidcUser(String username, String idTokenValue, List<String> actualKeycloakRoles) {
        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put("sub", "test-subject-" + username);
        idTokenClaims.put("preferred_username", username);
        idTokenClaims.put("email", username + "@example.com");

        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", actualKeycloakRoles);
        idTokenClaims.put("realm_access", realmAccess);

        OidcIdToken idToken = new OidcIdToken(
                idTokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                idTokenClaims
        );

        Set<GrantedAuthority> authorities = new HashSet<>();

        if (actualKeycloakRoles != null) {
            actualKeycloakRoles.forEach(role ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            );
        }

        authorities.add(new OidcUserAuthority(idToken, null));

        boolean hasRolePrefixedAuthority = false;
        for (GrantedAuthority auth : authorities) {
            if (auth.getAuthority().startsWith("ROLE_")) {
                hasRolePrefixedAuthority = true;
                break;
            }
        }
        if (!hasRolePrefixedAuthority && (actualKeycloakRoles == null || actualKeycloakRoles.isEmpty())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return new DefaultOidcUser(authorities, idToken, "preferred_username");
    }


    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAllInBatch();

        testUser = createMockOidcUser(testUsername, "fake-history-api-token", List.of("MEMBER"));

        chatMessageRepository.save(ChatMessage.builder().sender("user1").content("Oldest join").type(MessageType.JOIN).timestamp(LocalDateTime.now().minusMinutes(6)).build());
        chatMessageRepository.save(ChatMessage.builder().sender("user1").content("Hello from past").type(MessageType.CHAT).timestamp(LocalDateTime.now().minusMinutes(5)).build());
        chatMessageRepository.save(ChatMessage.builder().sender("user2").content("Hi there also from past").type(MessageType.CHAT).timestamp(LocalDateTime.now().minusMinutes(4)).build());
    }

    @Test
    void getMessageHistory_whenAuthenticated_shouldReturnMessagesInChronologicalOrder() throws Exception {
        mockMvc.perform(get("/api/messages/history")
                        .with(oidcLogin().oidcUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].content").value("Oldest join"))
                .andExpect(jsonPath("$[0].sender").value("user1"))
                .andExpect(jsonPath("$[0].type").value("JOIN"))
                .andExpect(jsonPath("$[1].content").value("Hello from past"))
                .andExpect(jsonPath("$[1].sender").value("user1"))
                .andExpect(jsonPath("$[1].type").value("CHAT"))
                .andExpect(jsonPath("$[2].content").value("Hi there also from past"))
                .andExpect(jsonPath("$[2].sender").value("user2"))
                .andExpect(jsonPath("$[2].type").value("CHAT"));
    }

    @Test
    void getMessageHistory_whenNoMessages_shouldReturnEmptyList() throws Exception {
        chatMessageRepository.deleteAllInBatch();

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