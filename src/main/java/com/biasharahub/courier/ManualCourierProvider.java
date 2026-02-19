package com.biasharahub.courier;

import com.biasharahub.entity.CourierService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * No API integration: seller enters carrier and tracking number manually.
 * Tracking link is built from entity's trackingUrlTemplate when present.
 */
@Component
public class ManualCourierProvider implements CourierProviderClient {

    @Override
    public boolean supports(String providerType) {
        return "MANUAL".equalsIgnoreCase(providerType == null ? "" : providerType.trim());
    }

    @Override
    public List<RateQuote> getRates(CourierService courier, RateRequest request) {
        return Collections.emptyList();
    }

    @Override
    public CreateShipmentResult createShipment(CourierService courier, CreateShipmentRequest request) {
        return null;
    }

    @Override
    public TrackingInfo getTracking(CourierService courier, String trackingNumber) {
        return null;
    }
}
