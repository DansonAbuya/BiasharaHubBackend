package com.biasharahub.courier;

import com.biasharahub.entity.CourierService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Integration with a courier provider (DHL, FedEx, Sendy, or generic REST).
 * API keys are supplied at runtime from configuration, not from the entity.
 */
public interface CourierProviderClient {

    /**
     * Whether this client supports the given provider type (e.g. "DHL", "MANUAL").
     */
    boolean supports(String providerType);

    /**
     * Get rate quotes for a shipment. Optional; return empty list if not supported.
     */
    List<RateQuote> getRates(CourierService courier, RateRequest request) throws CourierIntegrationException;

    /**
     * Create a shipment with the provider; returns tracking number and optional label URL.
     * For MANUAL provider, returns null (seller enters tracking manually).
     */
    CreateShipmentResult createShipment(CourierService courier, CreateShipmentRequest request) throws CourierIntegrationException;

    /**
     * Get tracking events for a tracking number. Return null if not available (e.g. MANUAL).
     */
    TrackingInfo getTracking(CourierService courier, String trackingNumber) throws CourierIntegrationException;
}
