package com.gal4xyit.chat;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.mockito.Mockito;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private DefaultOidcUser createMockOidcUser(String username, String idTokenValue) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "subject-" + username);
        claims.put("preferred_username", username);
        OidcIdToken idToken = new OidcIdToken(idTokenValue, Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new DefaultOidcUser(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")), idToken, "preferred_username");
    }

    @Test
    void whenUnauthenticatedAccessToProtectedApi_thenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/messages/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void afterSimulatedSuccessfulLogin_canAccessProtectedApiAndUserInfo() throws Exception {
        DefaultOidcUser mockUser = createMockOidcUser("authedUser", "fake-token-123");

        mockMvc.perform(get("/api/user")
                        .with(oidcLogin().oidcUser(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("authedUser"))
                .andExpect(jsonPath("$.idToken").value("fake-token-123"));

        mockMvc.perform(get("/api/messages/history")
                        .with(oidcLogin().oidcUser(mockUser)))
                .andExpect(status().isOk());
    }
}