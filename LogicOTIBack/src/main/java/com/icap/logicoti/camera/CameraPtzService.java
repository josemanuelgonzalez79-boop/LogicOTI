package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraPtzProperties;
import com.icap.logicoti.exception.BadRequestException;
import com.icap.logicoti.exception.ConflictException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class CameraPtzService {

    private final CameraService cameraService;
    private final CameraPtzProperties properties;
    private final PtzCommandGateway gateway;
    private final ConcurrentHashMap<Integer, ReentrantLock> channelLocks =
            new ConcurrentHashMap<>();

    public CameraPtzService(
            CameraService cameraService,
            CameraPtzProperties properties,
            PtzCommandGateway gateway
    ) {
        this.cameraService = cameraService;
        this.properties = properties;
        this.gateway = gateway;
    }

    public void pulse(String cameraCode, CameraPtzDirection direction) {
        if (!properties.isConfigured()) {
            throw new CameraPtzUnavailableException(
                    "El control PTZ no está configurado."
            );
        }
        CameraResponse camera = cameraService.findByCode(cameraCode);
        if (!camera.active() || !camera.videoAvailable()
                || !properties.isChannelAllowed(camera.channelNumber())) {
            throw new BadRequestException(
                    "Esta cámara no tiene control PTZ habilitado."
            );
        }
        if (direction == null || direction == CameraPtzDirection.STOP) {
            throw new BadRequestException("Selecciona una dirección PTZ válida.");
        }

        ReentrantLock lock = channelLocks.computeIfAbsent(
                camera.channelNumber(), ignored -> new ReentrantLock()
        );
        if (!lock.tryLock()) {
            throw new ConflictException("Ya se está moviendo esta cámara. Espera un momento.");
        }
        try {
            try {
                gateway.send(camera.channelNumber(), direction);
                Thread.sleep(properties.getPulseDuration().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CameraPtzUnavailableException(
                        "Se interrumpió el movimiento PTZ.", exception
                );
            } finally {
                // Incluso si el primer PUT no recibe respuesta, la cámara
                // podría haberse movido. Siempre intentar detenerla.
                stop(camera.channelNumber());
            }
        } finally {
            lock.unlock();
        }
    }

    private void stop(int channel) {
        try {
            gateway.send(channel, CameraPtzDirection.STOP);
        } catch (CameraPtzUnavailableException exception) {
            // STOP es idempotente: reintentar una vez si se perdió la respuesta.
            gateway.send(channel, CameraPtzDirection.STOP);
        }
    }
}
