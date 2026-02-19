package com.biasharahub.mail;

/**
 * Thrown when email sending fails (after retries if applicable).
 */
public class EmailException extends RuntimeException {

    public EmailException(String message) {
        super(message);
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
