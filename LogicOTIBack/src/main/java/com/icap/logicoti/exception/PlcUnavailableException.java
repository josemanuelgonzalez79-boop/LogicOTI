package com.icap.logicoti.exception;

public class PlcUnavailableException extends RuntimeException {

    public PlcUnavailableException(String message) {
        super(message);
    }

    public PlcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}