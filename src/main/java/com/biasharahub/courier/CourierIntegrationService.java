package com.biasharahub.courier;

import com.biasharahub.entity.CourierService;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import com.biasharahub.repository.CourierServiceRepository;
import com.biasharahub.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for courier provider integration: create shipment with provider, get tracking.
 * Delegates to the appropriate CourierProviderClient by provider type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierIntegrationService {

    private final CourierServiceRepository courierServiceRepository;
    private final ShipmentRepository shipmentRepository;
    private final List<CourierProviderClient> providers;

    public CourierProviderClient getClient(CourierService courier) {
        String type = courier.getProviderType() == null ? "MANUAL" : courier.getProviderType();
        return providers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No courier provider client for type: " + type));
    }

    /**
     * Create a shipment with the configured courier provider. Updates the shipment with tracking number and label URL if returned.
     */
    public CreateShipmentResult createShipmentWithProvider(UUID shipmentId, String courierServiceCode) throws CourierIntegrationException {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (!"COURIER".equalsIgnoreCase(shipment.getDeliveryMode())) {
            throw new CourierIntegrationException("Shipment is not COURIER delivery mode");
        }
        CourierService courier = courierServiceRepository.findByCode(courierServiceCode.trim().toLowerCase())
                .orElseThrow(() -> new CourierIntegrationException("Courier service not found: " + courierServiceCode));
        CourierProviderClient client = getClient(courier);
        CreateShipmentRequest request = buildRequest(shipment);
        CreateShipmentResult result = client.createShipment(courier, request);
        if (result != null) {
            shipment.setCourierService(courier.getName());
            shipment.setTrackingNumber(result.getTrackingNumber());
            shipmentRepository.save(shipment);
        }
        return result;
    }

    /**
     * Get tracking info from the provider for a shipment. Returns null if MANUAL or provider does not support tracking.
     */
    public TrackingInfo getTracking(UUID shipmentId) throws CourierIntegrationException {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        String trackingNumber = shipment.getTrackingNumber();
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        String carrierNameOrCode = shipment.getCourierService();
        if (carrierNameOrCode == null || carrierNameOrCode.isBlank()) {
            return null;
        }
        Optional<CourierService> courierOpt = courierServiceRepository.findByCode(carrierNameOrCode.trim().toLowerCase())
                .or(() -> courierServiceRepository.findAllByOrderByNameAsc().stream()
                        .filter(c -> carrierNameOrCode.equalsIgnoreCase(c.getName()))
                        .findFirst());
        if (courierOpt.isEmpty()) {
            return null;
        }
        CourierService courier = courierOpt.get();
        CourierProviderClient client = getClient(courier);
        return client.getTracking(courier, trackingNumber);
    }

    public List<RateQuote> getRates(String courierServiceCode, RateRequest rateRequest) throws CourierIntegrationException {
        CourierService courier = courierServiceRepository.findByCode(courierServiceCode.trim().toLowerCase())
                .orElseThrow(() -> new CourierIntegrationException("Courier service not found: " + courierServiceCode));
        return getClient(courier).getRates(courier, rateRequest);
    }

    private CreateShipmentRequest buildRequest(Shipment shipment) {
        Order order = shipment.getOrder();
        return CreateShipmentRequest.builder()
                .orderId(order.getOrderId())
                .shipmentId(shipment.getShipmentId())
                .recipientName(order.getUser() != null ? order.getUser().getName() : null)
                .recipientPhone(order.getUser() != null ? order.getUser().getPhone() : null)
                .recipientAddress(order.getShippingAddress())
                .recipientCity(null)
                .recipientPostalCode(null)
                .recipientCountry("KE")
                .shipperName(null)
                .shipperPhone(null)
                .shipperAddress(null)
                .shipperCity(null)
                .shipperPostalCode(null)
                .shipperCountry("KE")
                .weightKg(java.math.BigDecimal.ONE)
                .parcelDescription("Order " + order.getOrderId())
                .build();
    }
}
