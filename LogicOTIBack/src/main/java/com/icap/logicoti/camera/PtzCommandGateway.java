package com.icap.logicoti.camera;

interface PtzCommandGateway {
    void send(int channel, CameraPtzDirection direction);
}
