package com.gal4xyit.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(
                                        "/", "/index.html", "/css/**", "/js/**",
                                        "/ws/**",
                                        "/error", "/favicon.ico",
                                        "/webjars/**",
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**"
                                ).permitAll()
                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/user/**", "/api/messages/**").hasAnyRole("USER", "ADMIN")
                                .anyRequest().authenticated()
                )
                .oauth2Login(oauth2Login ->
                        oauth2Login
                                .defaultSuccessUrl("/", true)
                                .userInfoEndpoint(userInfoEndpoint ->
                                        userInfoEndpoint
                                                .userAuthoritiesMapper(userAuthoritiesMapper())
                                )
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler())
                );

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Collections.singletonList("http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler successHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
        successHandler.setPostLogoutRedirectUri("{baseUrl}");
        return successHandler;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return (authorities) -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                    Map<String, Object> idTokenClaims = oidcUserAuthority.getIdToken().getClaims();

                    Map<String, Object> realmAccess = (Map<String, Object>) idTokenClaims.get("realm_access");
                    if (realmAccess != null && realmAccess.get("roles") instanceof List) {
                        ((List<String>) realmAccess.get("roles")).forEach(roleName ->
                                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase()))
                        );
                    }

                    Map<String, Object> resourceAccess = (Map<String, Object>) idTokenClaims.get("resource_access");
                    if (resourceAccess != null && resourceAccess.get("spring-boot-chat-client") instanceof Map) {
                        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("spring-boot-chat-client");
                        if (clientAccess != null && clientAccess.get("roles") instanceof List) {
                            ((List<String>) clientAccess.get("roles")).forEach(roleName ->
                                    mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase()))
                            );
                        }
                    }
                    mappedAuthorities.add(oidcUserAuthority);
                } else if (authority instanceof OAuth2UserAuthority oauth2UserAuthority) {
                    mappedAuthorities.add(oauth2UserAuthority);
                } else {
                    mappedAuthorities.add(authority);
                }
            });
            return mappedAuthorities;
        };
    }
}