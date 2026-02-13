package com.biasharahub.courier;

/**
 * Thrown when a courier provider API call fails (auth, validation, network).
 */
public class CourierIntegrationException extends Exception {

    public CourierIntegrationException(String message) {
        super(message);
    }

    public CourierIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
