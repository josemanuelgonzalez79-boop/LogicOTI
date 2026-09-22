package com.icap.logicoti.camera;

public class CameraHistoryUnavailableException extends RuntimeException {

    public CameraHistoryUnavailableException(String message) {
        super(message);
    }

    public CameraHistoryUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
