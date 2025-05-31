package com.gal4xyit.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class UserController {

    @Operation(summary = "Get current authenticated user's details",
            security = @SecurityRequirement(name = "oauth2_keycloak_implicit_flow"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user details",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/api/user")
    public ResponseEntity<Map<String, Object>> getUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        Map<String, Object> userDetails = new HashMap<>();
        String usernameToUse;
        if (principal instanceof OidcUser oidcUser) {
            usernameToUse = oidcUser.getPreferredUsername();
            userDetails.put("idToken", oidcUser.getIdToken().getTokenValue());
        } else {
            usernameToUse = principal.getAttribute("preferred_username");
        }
        if (usernameToUse == null || usernameToUse.isBlank()) {
            usernameToUse = principal.getName();
        }
        userDetails.put("username", usernameToUse);
        userDetails.put("authorities", principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        return ResponseEntity.ok(userDetails);
    }

    @Operation(summary = "Admin-only greeting", security = @SecurityRequirement(name = "oauth2_keycloak_implicit_flow"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful greeting for admin"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role")
    })
    @GetMapping("/api/admin/greeting")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> getAdminGreeting(@AuthenticationPrincipal OAuth2User principal) {
        return ResponseEntity.ok("Hello Admin " + principal.getAttribute("preferred_username") + "!");
    }

    @Operation(summary = "User greeting", security = @SecurityRequirement(name = "oauth2_keycloak_implicit_flow"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful greeting for user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User does not have USER role (if USER is not default)")
    })
    @GetMapping("/api/user/greeting")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> getUserGreeting(@AuthenticationPrincipal OAuth2User principal) {
        return ResponseEntity.ok("Hello User " + principal.getAttribute("preferred_username") + "!");
    }
}