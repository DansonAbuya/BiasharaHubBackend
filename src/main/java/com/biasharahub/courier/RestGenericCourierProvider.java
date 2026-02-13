package com.biasharahub.courier;

import com.biasharahub.entity.CourierService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic REST integration: calls provider's API base URL with API key from config.
 * Expects: POST {apiBaseUrl}/shipments -> { trackingNumber, labelUrl? }; GET {apiBaseUrl}/tracking/{trackingNumber} -> { status, events? }.
 */
@Slf4j
@Component
public class RestGenericCourierProvider implements CourierProviderClient {

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final CourierProviderConfig providerConfig;

    public RestGenericCourierProvider(CourierProviderConfig providerConfig, ObjectMapper objectMapper) {
        this.providerConfig = providerConfig;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public boolean supports(String providerType) {
        return "REST".equalsIgnoreCase(providerType == null ? "" : providerType.trim());
    }

    @Override
    public List<RateQuote> getRates(CourierService courier, RateRequest request) {
        return List.of();
    }

    @Override
    public CreateShipmentResult createShipment(CourierService courier, CreateShipmentRequest request) throws CourierIntegrationException {
        String baseUrl = courier.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new CourierIntegrationException("This courier provider is not fully set up. Please add an API base URL in Admin → Courier Services.");
        }
        String apiKey = providerConfig.getApiKey(courier.getCode());
        if (apiKey == null || apiKey.isBlank()) {
            throw new CourierIntegrationException("This courier is not connected yet. Ask your administrator to add the API key in the server settings for \"" + courier.getName() + "\".");
        }
        String url = baseUrl.replaceAll("/$", "") + "/shipments";
        Map<String, Object> shipper = new HashMap<>();
        shipper.put("name", nullToEmpty(request.getShipperName()));
        shipper.put("phone", nullToEmpty(request.getShipperPhone()));
        shipper.put("address", nullToEmpty(request.getShipperAddress()));
        shipper.put("city", nullToEmpty(request.getShipperCity()));
        shipper.put("postalCode", nullToEmpty(request.getShipperPostalCode()));
        shipper.put("country", nullToEmpty(request.getShipperCountry()));
        Map<String, Object> recipient = new HashMap<>();
        recipient.put("name", nullToEmpty(request.getRecipientName()));
        recipient.put("phone", nullToEmpty(request.getRecipientPhone()));
        recipient.put("address", nullToEmpty(request.getRecipientAddress()));
        recipient.put("city", nullToEmpty(request.getRecipientCity()));
        recipient.put("postalCode", nullToEmpty(request.getRecipientPostalCode()));
        recipient.put("country", nullToEmpty(request.getRecipientCountry()));
        Map<String, Object> body = new HashMap<>();
        body.put("shipper", shipper);
        body.put("recipient", recipient);
        body.put("weightKg", request.getWeightKg() != null ? request.getWeightKg() : BigDecimal.ONE);
        body.put("orderId", request.getOrderId() != null ? request.getOrderId().toString() : "");
        body.put("shipmentId", request.getShipmentId() != null ? request.getShipmentId().toString() : "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-API-Key", apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = REST_TEMPLATE.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CourierIntegrationException("The courier provider could not create the shipment. Please try again or enter the tracking number manually.");
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String trackingNumber = root.has("trackingNumber") ? root.get("trackingNumber").asText() : (root.has("tracking_number") ? root.get("tracking_number").asText() : null);
            String labelUrl = root.has("labelUrl") ? root.get("labelUrl").asText(null) : (root.has("label_url") ? root.get("label_url").asText(null) : null);
            if (trackingNumber == null || trackingNumber.isBlank()) {
                throw new CourierIntegrationException("The courier provider did not return a tracking number. You can add it manually in the shipment details.");
            }
            return CreateShipmentResult.builder()
                    .trackingNumber(trackingNumber.trim())
                    .labelUrl(labelUrl)
                    .carrierReference(root.has("carrierReference") ? root.get("carrierReference").asText(null) : null)
                    .build();
        } catch (CourierIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("REST courier createShipment failed: {}", e.getMessage());
            throw new CourierIntegrationException("We couldn't reach the courier provider right now. Please try again later or enter the tracking number manually.", e);
        }
    }

    @Override
    public TrackingInfo getTracking(CourierService courier, String trackingNumber) throws CourierIntegrationException {
        String baseUrl = courier.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank() || trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        String apiKey = providerConfig.getApiKey(courier.getCode());
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String url = baseUrl.replaceAll("/$", "") + "/tracking/" + trackingNumber.trim();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-API-Key", apiKey);
        try {
            ResponseEntity<String> response = REST_TEMPLATE.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.has("status") ? root.get("status").asText() : (root.has("state") ? root.get("state").asText() : "UNKNOWN");
            String statusDesc = root.has("statusDescription") ? root.get("statusDescription").asText(null) : (root.has("description") ? root.get("description").asText(null) : null);
            Instant estDelivery = null;
            if (root.has("estimatedDelivery")) {
                try {
                    estDelivery = Instant.parse(root.get("estimatedDelivery").asText());
                } catch (Exception ignored) {}
            }
            List<TrackingEvent> events = new ArrayList<>();
            if (root.has("events") && root.get("events").isArray()) {
                for (JsonNode ev : root.get("events")) {
                    Instant ts = null;
                    if (ev.has("timestamp")) {
                        try {
                            ts = Instant.parse(ev.get("timestamp").asText());
                        } catch (Exception ignored) {
                            // ignore invalid timestamp format
                        }
                    }
                    events.add(TrackingEvent.builder()
                            .timestamp(ts)
                            .status(ev.has("status") ? ev.get("status").asText() : null)
                            .description(ev.has("description") ? ev.get("description").asText(null) : null)
                            .location(ev.has("location") ? ev.get("location").asText(null) : null)
                            .build());
                }
            }
            return TrackingInfo.builder()
                    .trackingNumber(trackingNumber)
                    .status(status)
                    .statusDescription(statusDesc)
                    .estimatedDelivery(estDelivery)
                    .events(events)
                    .build();
        } catch (Exception e) {
            log.debug("REST tracking failed for {}: {}", trackingNumber, e.getMessage());
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
