package com.gal4xyit.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gal4xyit.chat.config.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsInAnyOrder;


@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserControllerTest.MockBeanConfiguration.class})
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class MockBeanConfiguration {
        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ClientRegistrationRepository.class);
        }

        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    private DefaultOidcUser createMockOidcUser(String username, String idTokenValue, List<String> keycloakRoles) {
        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put("sub", "subject-" + username);
        idTokenClaims.put("preferred_username", username);
        idTokenClaims.put("email", username + "@example.com");

        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", keycloakRoles);
        idTokenClaims.put("realm_access", realmAccess);

        OidcIdToken idToken = new OidcIdToken(
                idTokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                idTokenClaims
        );

        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
        if (keycloakRoles != null) {
            keycloakRoles.forEach(role ->
                    mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            );
        }

        mappedAuthorities.add(new OidcUserAuthority(idToken, null));

        if (keycloakRoles == null || keycloakRoles.isEmpty()) {
            if (mappedAuthorities.stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_"))) {
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
        }

        return new DefaultOidcUser(mappedAuthorities, idToken, "preferred_username");
    }

    @Test
    void getUser_whenAuthenticated_shouldReturnUserDetails() throws Exception {
        DefaultOidcUser mockUser = createMockOidcUser("testUser", "token1", List.of("USER"));

        mockMvc.perform(get("/api/user")
                        .with(oidcLogin().oidcUser(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.idToken").value("token1"))
                .andExpect(jsonPath("$.authorities", containsInAnyOrder("ROLE_USER", "OIDC_USER")));
    }

    @Test
    void getUser_whenNotAuthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAdminGreeting_whenUserHasAdminRole_shouldSucceed() throws Exception {
        DefaultOidcUser adminUser = createMockOidcUser("adminUser", "admin-token", List.of("ADMIN", "USER"));

        mockMvc.perform(get("/api/admin/greeting")
                        .with(oidcLogin().oidcUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello Admin adminUser!"));
    }

    @Test
    void getAdminGreeting_whenUserDoesNotHaveAdminRole_shouldBeForbidden() throws Exception {
        DefaultOidcUser regularUser = createMockOidcUser("regularUser", "user-token", List.of("USER"));

        mockMvc.perform(get("/api/admin/greeting")
                        .with(oidcLogin().oidcUser(regularUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserGreeting_whenUserHasUserRole_shouldSucceed() throws Exception {
        DefaultOidcUser regularUser = createMockOidcUser("anotherUser", "user-token-2", List.of("USER"));

        mockMvc.perform(get("/api/user/greeting")
                        .with(oidcLogin().oidcUser(regularUser)))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello User anotherUser!"));
    }

    @Test
    void getUserGreeting_whenUserHasAdminRole_shouldAlsoSucceed() throws Exception {
        DefaultOidcUser adminUser = createMockOidcUser("superAdmin", "super-token", List.of("ADMIN"));

        mockMvc.perform(get("/api/user/greeting")
                        .with(oidcLogin().oidcUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello User superAdmin!"));
    }

    @Test
    void getUserGreeting_whenUserHasNoMatchingRole_shouldBeForbiddenIfEndpointStrict() throws Exception {
        DefaultOidcUser otherUser = createMockOidcUser("guestUser", "guest-token", List.of("GUEST"));

        mockMvc.perform(get("/api/user/greeting")
                        .with(oidcLogin().oidcUser(otherUser)))
                .andExpect(status().isForbidden());
    }
}