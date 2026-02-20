package com.biasharahub.controller;

import com.biasharahub.service.WhatsAppChatbotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Twilio WhatsApp webhook: receives incoming messages and delegates to the AI chatbot.
 * Configure this URL in Twilio Console: https://your-domain/webhooks/whatsapp
 * Security: permitAll for this path (validated by Twilio signature if needed later).
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppChatbotService chatbotService;

    /**
     * Twilio sends POST with application/x-www-form-urlencoded: From, Body, To, etc.
     * Location shares include Latitude and Longitude parameters.
     */
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleIncoming(
            @RequestParam(value = "From", required = false) String from,
            @RequestParam(value = "Body", required = false) String body,
            @RequestParam(value = "Latitude", required = false) String latitude,
            @RequestParam(value = "Longitude", required = false) String longitude) {
        try {
            if (from != null && !from.isBlank()) {
                chatbotService.handleIncomingMessage(from, body != null ? body : "", latitude, longitude);
            }
        } catch (Exception e) {
            log.warn("WhatsApp webhook error: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("WhatsApp webhook is active. Configure this URL in Twilio.");
    }
}
