package com.icap.logicoti.camera;

public class CameraPtzUnavailableException extends RuntimeException {

    public CameraPtzUnavailableException(String message) {
        super(message);
    }

    public CameraPtzUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
