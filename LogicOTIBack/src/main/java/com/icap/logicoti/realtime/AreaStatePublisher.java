package com.icap.logicoti.realtime;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AreaStatePublisher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AreaStatePublisher.class);

    private static final String AREA_CODE = "P1_A01";

    private final AreaStateService areaStateService;
    private final SimpMessagingTemplate messagingTemplate;

    public AreaStatePublisher(
            AreaStateService areaStateService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.areaStateService = areaStateService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(
            initialDelay = 3000,
            fixedDelayString = "${realtime.poll-ms:1000}"
    )
    public void publishAreaState() {

        try {
            AreaStateResponse response =
                    areaStateService.getAreaState(AREA_CODE);

            messagingTemplate.convertAndSend(
                    "/topic/areas/" + AREA_CODE + "/state",
                    response
            );

        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudo publicar el estado del área {}: {}",
                    AREA_CODE,
                    exception.getMessage()
            );
        }
    }
}