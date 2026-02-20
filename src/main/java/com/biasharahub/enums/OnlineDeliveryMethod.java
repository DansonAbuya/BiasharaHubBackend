package com.biasharahub.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Online service delivery methods for virtual/remote services.
 */
public enum OnlineDeliveryMethod {
    VIDEO_CALL("Video Call", "Live video session via Zoom, Google Meet, or similar"),
    PHONE_CALL("Phone Call", "Voice call consultation"),
    WHATSAPP("WhatsApp", "Chat, voice, or video via WhatsApp"),
    LIVE_CHAT("Live Chat", "Real-time text chat session"),
    EMAIL("Email", "Service delivered via email correspondence"),
    SCREEN_SHARE("Screen Share", "Remote desktop or screen sharing session"),
    FILE_DELIVERY("File Delivery", "Digital files delivered (documents, designs, etc.)"),
    RECORDED_CONTENT("Recorded Content", "Pre-recorded videos, tutorials, or courses"),
    SOCIAL_MEDIA("Social Media", "Service via Instagram, Facebook, or other platforms");

    private final String displayName;
    private final String description;

    OnlineDeliveryMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse comma-separated string into list of delivery methods.
     */
    public static List<OnlineDeliveryMethod> fromString(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return OnlineDeliveryMethod.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    /**
     * Convert list of methods to comma-separated string.
     */
    public static String toString(List<OnlineDeliveryMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            return null;
        }
        return methods.stream()
                .map(OnlineDeliveryMethod::name)
                .collect(Collectors.joining(","));
    }

    /**
     * Get human-readable display of delivery methods.
     */
    public static String toDisplayString(String commaSeparated) {
        List<OnlineDeliveryMethod> methods = fromString(commaSeparated);
        if (methods.isEmpty()) {
            return "Online";
        }
        return methods.stream()
                .map(OnlineDeliveryMethod::getDisplayName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Get emoji-prefixed display for WhatsApp/messaging.
     */
    public static String toWhatsAppDisplay(String commaSeparated) {
        List<OnlineDeliveryMethod> methods = fromString(commaSeparated);
        if (methods.isEmpty()) {
            return "🌐 Online service";
        }
        StringBuilder sb = new StringBuilder();
        for (OnlineDeliveryMethod m : methods) {
            String emoji = switch (m) {
                case VIDEO_CALL -> "📹";
                case PHONE_CALL -> "📞";
                case WHATSAPP -> "💬";
                case LIVE_CHAT -> "💭";
                case EMAIL -> "📧";
                case SCREEN_SHARE -> "🖥️";
                case FILE_DELIVERY -> "📁";
                case RECORDED_CONTENT -> "🎬";
                case SOCIAL_MEDIA -> "📱";
            };
            sb.append(emoji).append(" ").append(m.getDisplayName()).append("\n");
        }
        return sb.toString().trim();
    }
}
