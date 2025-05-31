package com.gal4xyit.chat.controller;

import com.gal4xyit.chat.chat.ChatMessage;
import com.gal4xyit.chat.chat.ChatMessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageRestController {

    private final ChatMessageRepository chatMessageRepository;

    @Operation(summary = "Get chat message history",
            security = @SecurityRequirement(name = "oauth2_keycloak_implicit_flow"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved message history",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ChatMessage.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getMessageHistory() {
        List<ChatMessage> recentMessages = chatMessageRepository.findTop100ByOrderByTimestampDesc();
        Collections.reverse(recentMessages);
        return ResponseEntity.ok(recentMessages);
    }
}