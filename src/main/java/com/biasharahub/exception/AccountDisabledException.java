package com.biasharahub.exception;

/**
 * Thrown when a user attempts to log in but their account is disabled, suspended, or banned.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
