package com.biasharahub.courier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides API keys for courier providers. Keys are read from environment:
 * COURIER_&lt;CODE&gt;_API_KEY (e.g. COURIER_DHL_API_KEY, COURIER_SENDY_API_KEY).
 * Code is normalized: lowercase, hyphens replaced by underscore for env lookup.
 */
@Component
public class CourierProviderConfig {

    @Value("${app.courier.api-keys.dhl:${COURIER_DHL_API_KEY:}}")
    private String dhlApiKey;

    @Value("${app.courier.api-keys.fedex:${COURIER_FEDEX_API_KEY:}}")
    private String fedexApiKey;

    @Value("${app.courier.api-keys.sendy:${COURIER_SENDY_API_KEY:}}")
    private String sendyApiKey;

    /** Generic REST: key by courier code, e.g. app.courier.api-keys.my-provider or COURIER_MY_PROVIDER_API_KEY */
    private final Map<String, String> keyByCode = new HashMap<>();

    public String getApiKey(String courierCode) {
        if (courierCode == null || courierCode.isBlank()) return null;
        String normalized = courierCode.trim().toLowerCase().replace("-", "_");
        switch (normalized) {
            case "dhl": return blankToNull(dhlApiKey);
            case "fedex": return blankToNull(fedexApiKey);
            case "sendy": return blankToNull(sendyApiKey);
            default:
                String fromMap = keyByCode.get(normalized);
                if (fromMap != null) return blankToNull(fromMap);
                String envStyle = "COURIER_" + courierCode.trim().toUpperCase().replace("-", "_") + "_API_KEY";
                return blankToNull(System.getenv(envStyle));
        }
    }

    /** Allow runtime registration for REST providers (e.g. from app.courier.providers.*.api-key). */
    public void setApiKeyForCode(String code, String apiKey) {
        if (code != null) keyByCode.put(code.trim().toLowerCase().replace("-", "_"), apiKey);
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
