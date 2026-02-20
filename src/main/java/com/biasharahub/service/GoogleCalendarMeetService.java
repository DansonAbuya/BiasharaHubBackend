package com.biasharahub.service;

import com.biasharahub.mail.GmailOAuthConfig;
import com.google.auth.oauth2.UserCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Creates Google Calendar events with Google Meet links for virtual service appointments.
 * Uses the Calendar API v3 REST endpoint with the same OAuth2 credentials as Gmail.
 * The refresh token must have been obtained with scope https://www.googleapis.com/auth/calendar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarMeetService {

    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final GmailOAuthConfig gmailOAuthConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a Calendar event with a Google Meet link. Attendees receive a calendar invite.
     */
    @SuppressWarnings("unchecked")
    public Optional<CreateEventResult> createEventWithMeet(
            String title,
            String description,
            LocalDate startDate,
            LocalTime startTime,
            Integer durationMinutes,
            List<String> attendeeEmails) {
        if (!gmailOAuthConfig.isConfigured()) {
            log.debug("Google Calendar/Meet skipped: OAuth not configured");
            return Optional.empty();
        }
        try {
            UserCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(gmailOAuthConfig.getClientId())
                    .setClientSecret(gmailOAuthConfig.getClientSecret())
                    .setRefreshToken(gmailOAuthConfig.getRefreshToken())
                    .build();
            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();

            int duration = durationMinutes != null && durationMinutes > 0 ? durationMinutes : 60;
            ZonedDateTime start = startDate.atTime(startTime != null ? startTime : LocalTime.of(9, 0)).atZone(NAIROBI);
            ZonedDateTime end = start.plusMinutes(duration);
            String startRfc = start.format(RFC3339);
            String endRfc = end.format(RFC3339);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("summary", title != null && !title.isBlank() ? title : "BiasharaHub service meeting");
            if (description != null && !description.isBlank()) {
                body.put("description", description);
            }
            body.put("start", Map.of(
                    "dateTime", startRfc,
                    "timeZone", "Africa/Nairobi"
            ));
            body.put("end", Map.of(
                    "dateTime", endRfc,
                    "timeZone", "Africa/Nairobi"
            ));
            if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
                List<Map<String, String>> attendees = new ArrayList<>();
                for (String email : attendeeEmails) {
                    if (email != null && !email.isBlank()) {
                        attendees.add(Map.of("email", email));
                    }
                }
                if (!attendees.isEmpty()) {
                    body.put("attendees", attendees);
                }
            }
            body.put("conferenceData", Map.of(
                    "createRequest", Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "conferenceSolutionKey", Map.of("type", "hangoutsMeet")
                    )
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            String url = CALENDAR_API + "?conferenceDataVersion=1";
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Google Calendar API returned non-success: {}", response.getStatusCode());
                return Optional.empty();
            }

            Map<String, Object> created = response.getBody();
            String eventId = (String) created.get("id");

            String meetLink = null;
            Object confData = created.get("conferenceData");
            if (confData instanceof Map) {
                Map<String, Object> conf = (Map<String, Object>) confData;
                Object entryPoints = conf.get("entryPoints");
                if (entryPoints instanceof List) {
                    for (Object ep : (List<?>) entryPoints) {
                        if (ep instanceof Map) {
                            Map<String, Object> e = (Map<String, Object>) ep;
                            if ("video".equalsIgnoreCase((String) e.get("entryPointType"))) {
                                meetLink = (String) e.get("uri");
                                break;
                            }
                        }
                    }
                }
            }
            if (meetLink == null) {
                meetLink = (String) created.get("hangoutLink");
            }
            if (meetLink == null) {
                log.warn("Google Calendar event created but no Meet link for eventId={}", eventId);
                return Optional.empty();
            }

            String htmlLink = (String) created.get("htmlLink");
            log.info("Created Google Calendar event with Meet link for {}", title);
            return Optional.of(new CreateEventResult(meetLink, eventId, htmlLink));
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar/Meet event: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return gmailOAuthConfig.isConfigured();
    }

    @lombok.Value
    public static class CreateEventResult {
        String meetLink;
        String eventId;
        String calendarHtmlLink;
    }
}
