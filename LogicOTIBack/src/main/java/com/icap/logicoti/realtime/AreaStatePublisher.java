package com.icap.logicoti.realtime;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateResponse.DeviceStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.signal.SignalQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AreaStatePublisher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AreaStatePublisher.class);

    private final AreaStateService areaStateService;
    private final AreaSubscriptionRegistry subscriptionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

 
    private final ConcurrentMap<String, AreaSnapshot> lastSnapshots =
            new ConcurrentHashMap<>();

    public AreaStatePublisher(
            AreaStateService areaStateService,
            AreaSubscriptionRegistry subscriptionRegistry,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.areaStateService = areaStateService;
        this.subscriptionRegistry = subscriptionRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(
            initialDelay = 3000,
            fixedDelayString = "${realtime.poll-ms:1000}"
    )
    public void publishActiveAreas() {

        Set<String> activeAreas =
                subscriptionRegistry.getActiveAreaCodes();

     
        removeInactiveSnapshots(activeAreas);

    
        if (activeAreas.isEmpty()) {
            return;
        }

     
        activeAreas.forEach(this::readAndPublishArea);
    }

    private void readAndPublishArea(String areaCode) {

        try {
            AreaStateResponse response =
                    areaStateService.getAreaState(areaCode);

            AreaSnapshot currentSnapshot =
                    createSnapshot(response);

            AreaSnapshot previousSnapshot =
                    lastSnapshots.put(
                            areaCode,
                            currentSnapshot
                    );

          
            if (!currentSnapshot.equals(previousSnapshot)) {

                messagingTemplate.convertAndSend(
                        "/topic/areas/"
                                + areaCode
                                + "/state",
                        response
                );

                LOGGER.info(
                        "Cambio publicado por WebSocket para {}",
                        areaCode
                );
            }

        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudo procesar el área {}: {}",
                    areaCode,
                    exception.getMessage()
            );
        }
    }

    private AreaSnapshot createSnapshot(
            AreaStateResponse response
    ) {

        List<DeviceSnapshot> devices =
                response.devices()
                        .stream()
                        .map(this::createDeviceSnapshot)
                        .toList();

        return new AreaSnapshot(
                response.areaCode(),
                response.areaName(),
                response.plcEnabled(),
                response.connected(),
                devices,
                response.message()
        );
    }

    private DeviceSnapshot createDeviceSnapshot(
            DeviceStateResponse device
    ) {

        return new DeviceSnapshot(
                device.id(),
                device.code(),
                device.name(),
                device.type(),
                device.number(),
                device.controllable(),
                device.command(),
                device.state(),
                device.fault(),
                device.quality()
        );
    }

    private void removeInactiveSnapshots(
            Set<String> activeAreas
    ) {

        lastSnapshots.keySet().forEach(areaCode -> {

            if (!activeAreas.contains(areaCode)) {

                lastSnapshots.remove(areaCode);

                LOGGER.info(
                        "Estado temporal eliminado para {}",
                        areaCode
                );
            }
        });
    }

    /*
     * Representa el estado del área sin timestamp.
     */
    private record AreaSnapshot(
            String areaCode,
            String areaName,
            boolean plcEnabled,
            boolean connected,
            List<DeviceSnapshot> devices,
            String message
    ) {
    }

    /*
     * Representa los datos importantes de cada dispositivo.
     */
    private record DeviceSnapshot(
            Long id,
            String code,
            String name,
            String type,
            int number,
            boolean controllable,
            Boolean command,
            Boolean state,
            Boolean fault,
            SignalQuality quality
    ) {
    }
}
