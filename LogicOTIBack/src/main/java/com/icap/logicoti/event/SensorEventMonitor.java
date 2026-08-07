package com.icap.logicoti.event;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.intrusion.IntrusionMotionAlarmService;
import com.icap.logicoti.intrusion.AutomaticLightingService;
import com.icap.logicoti.plc.PlcCommunicationService;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class SensorEventMonitor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SensorEventMonitor.class);

    private final SensorEventHistoryService historyService;
    private final PlcCommunicationService plcCommunicationService;
    private final PlcProperties plcProperties;
    private final boolean enabled;
    private final SimpMessagingTemplate messagingTemplate;
    private final IntrusionMotionAlarmService motionAlarmService;
    private final AutomaticLightingService automaticLightingService;

    private final ConcurrentMap<Long, Boolean> lastStates =
            new ConcurrentHashMap<>();

    private List<SensorDefinition> sensorDefinitions =
            List.of();

    private Instant nextDefinitionRefresh =
            Instant.EPOCH;

    private boolean historyLoaded;

        public SensorEventMonitor(
                SensorEventHistoryService historyService,
                PlcCommunicationService plcCommunicationService,
                PlcProperties plcProperties,
                SimpMessagingTemplate messagingTemplate,
                IntrusionMotionAlarmService motionAlarmService,
                AutomaticLightingService automaticLightingService,

                @Value("${sensor.monitor.enabled:true}")
                boolean enabled
        ) {
                this.historyService = historyService;
                this.plcCommunicationService = plcCommunicationService;
                this.plcProperties = plcProperties;
                this.messagingTemplate = messagingTemplate;
                this.motionAlarmService = motionAlarmService;
                this.automaticLightingService = automaticLightingService;
                this.enabled = enabled;
        }

    @Scheduled(
            initialDelayString =
                    "${sensor.monitor.initial-delay-ms:5000}",
            fixedDelayString =
                    "${sensor.monitor.poll-ms:2000}"
    )
    public void monitorSensors() {

        if (!enabled || !plcProperties.isEnabled()) {
            return;
        }

        try {
            loadHistoryIfNeeded();

            List<SensorDefinition> sensors =
                    getSensorDefinitions();

            if (sensors.isEmpty()) {
                return;
            }

            Map<Long, Boolean> currentStates =
                    readSensorStates(sensors);

            sensors.forEach(sensor ->
                    processState(
                            sensor,
                            currentStates.get(sensor.id())
                    )
            );

        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudieron vigilar los sensores: {}",
                    exception.getMessage()
            );
        }
    }

    private void loadHistoryIfNeeded() {

        if (historyLoaded) {
            return;
        }

        lastStates.putAll(
                historyService.findLatestStates()
        );

        historyLoaded = true;

        LOGGER.info(
                "Se recuperaron {} estados de sensores del histórico.",
                lastStates.size()
        );
    }

    private List<SensorDefinition> getSensorDefinitions() {

        Instant now = Instant.now();

        if (!now.isBefore(nextDefinitionRefresh)) {
            sensorDefinitions =
                    historyService.findActiveSensors();

            nextDefinitionRefresh =
                    now.plusSeconds(60);

            LOGGER.info(
                    "Se cargaron {} sensores para vigilancia.",
                    sensorDefinitions.size()
            );
        }

        return sensorDefinitions;
    }

    private Map<Long, Boolean> readSensorStates(
            List<SensorDefinition> sensors
    ) {
        return plcCommunicationService.read(connection -> {

            if (!connection.getMetadata().isReadSupported()) {
                throw new IllegalStateException(
                        "La conexión no permite leer tags."
                );
            }

            PlcReadRequest.Builder builder =
                    connection.readRequestBuilder();

            sensors.forEach(sensor ->
                    builder.addTagAddress(
                            createAlias(sensor),
                            sensor.stateTag()
                    )
            );

            PlcReadResponse response = builder
                    .build()
                    .execute()
                    .get(
                            plcProperties
                                    .getTimeout()
                                    .toMillis(),
                            TimeUnit.MILLISECONDS
                    );

            Map<Long, Boolean> states =
                    new HashMap<>();

            for (SensorDefinition sensor : sensors) {
                String alias = createAlias(sensor);

                PlcResponseCode responseCode =
                        response.getResponseCode(alias);

                if (responseCode != PlcResponseCode.OK) {
                    LOGGER.warn(
                            "No se pudo leer {}. Respuesta: {}",
                            sensor.stateTag(),
                            responseCode
                    );
                    continue;
                }

                if (!response.isValidBoolean(alias)) {
                    LOGGER.warn(
                            "{} no devolvió un BOOL.",
                            sensor.stateTag()
                    );
                    continue;
                }

                states.put(
                        sensor.id(),
                        response.getBoolean(alias)
                );
            }

            return states;
        });
    }

    private void processState(
            SensorDefinition sensor,
            Boolean currentState
    ) {
        if (currentState == null) {
            return;
        }

        Boolean previousState =
                lastStates.get(sensor.id());

        /*
         * Primera lectura de un sensor sin histórico.
         * Si está apagado únicamente se guarda en memoria.
         * Si está activado, se registra para no perder una
         * posible alarma existente al iniciar el backend.
         */
        if (previousState == null) {

            if (currentState) {
                saveChange(
                        sensor,
                        null,
                        true
                );
            }

            lastStates.put(
                    sensor.id(),
                    currentState
            );

            return;
        }

        if (previousState.equals(currentState)) {
            if (currentState
                    && "MOTION".equalsIgnoreCase(sensor.type())) {
                automaticLightingService.refreshActiveMotion(
                        sensor.code()
                );
            }

            return;
        }

        saveChange(
                sensor,
                previousState,
                currentState
        );

        lastStates.put(
                sensor.id(),
                currentState
        );
    }

        private void saveChange(
                SensorDefinition sensor,
                Boolean previousState,
                boolean currentState
        ) {
                SensorEventHistoryResponse savedEvent =
                        historyService.saveChange(
                                sensor,
                                previousState,
                                currentState
                        );

                if ("SMOKE".equalsIgnoreCase(sensor.type())) {
                        messagingTemplate.convertAndSend(
                                "/topic/alerts/smoke",
                                savedEvent
                        );

                        LOGGER.info(
                                "Alerta de humo publicada por WebSocket para {}.",
                                sensor.code()
                        );
                }

                if ("MOTION".equalsIgnoreCase(sensor.type())) {
                        try {
                                automaticLightingService.processMotion(
                                        savedEvent
                                );
                        } catch (RuntimeException exception) {
                                LOGGER.warn(
                                        "No se pudo procesar iluminación automática para {}: {}",
                                        sensor.code(),
                                        exception.getMessage()
                                );
                        }

                        motionAlarmService.process(savedEvent);
                }

                LOGGER.info(
                        "Evento guardado: {} cambió de {} a {}.",
                        sensor.code(),
                        previousState,
                        currentState
                );
        }

    private String createAlias(
            SensorDefinition sensor
    ) {
        return "sensor_" + sensor.id();
    }
}
