package com.example.notification.exception;

public class TransientProviderException extends NotificationException {
    public TransientProviderException(String message) {
        super(message);
    }

    public TransientProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
