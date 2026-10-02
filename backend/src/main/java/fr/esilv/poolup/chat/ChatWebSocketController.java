package fr.esilv.poolup.chat;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;

    @MessageMapping("/chat/{tripId}")
    @SendTo("/topic/trips/{tripId}")
    public MessageResponse send(@DestinationVariable Long tripId, Principal principal,
            @Payload MessageRequest request) {
        Long userId = extractUserId(principal);
        return messageService.sendMessage(tripId, userId, request.content());
    }

    private static Long extractUserId(Principal principal) {
        if (principal instanceof AbstractAuthenticationToken token && token.getPrincipal() instanceof Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }
        if (principal != null && principal.getName() != null) {
            try {
                return Long.valueOf(principal.getName());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        throw new IllegalStateException("Authentification STOMP manquante.");
    }
}
