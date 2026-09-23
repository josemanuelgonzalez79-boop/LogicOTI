package com.icap.logicoti.camera;

public enum CameraPtzDirection {
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    LEFT(-1, 0, 0),
    RIGHT(1, 0, 0),
    ZOOM_IN(0, 0, 1),
    ZOOM_OUT(0, 0, -1),
    STOP(0, 0, 0);

    private final int pan;
    private final int tilt;
    private final int zoom;

    CameraPtzDirection(int pan, int tilt, int zoom) {
        this.pan = pan;
        this.tilt = tilt;
        this.zoom = zoom;
    }

    int pan(int speed) { return pan * speed; }
    int tilt(int speed) { return tilt * speed; }
    int zoom(int speed) { return zoom * speed; }
}
