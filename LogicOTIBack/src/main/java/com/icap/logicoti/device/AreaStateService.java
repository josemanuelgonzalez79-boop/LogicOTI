package com.icap.logicoti.device;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.device.AreaStateResponse.DeviceStateResponse;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.PlcUnavailableException;

import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import com.icap.logicoti.plc.PlcCommunicationService;
import com.icap.logicoti.signal.SignalQuality;
import com.icap.logicoti.signal.SignalQualityRegistry;
import com.icap.logicoti.signal.SignalQualitySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


@Service
@Transactional(readOnly = true)

public class AreaStateService {
        
    private final PlcCommunicationService plcCommunicationService;

    private static final String AREA_QUERY = """
            SELECT
                code,
                name
            FROM building_area
            WHERE code = ?
              AND active = TRUE
            """;

    private static final String DEVICES_QUERY = """
            SELECT
                device.id,
                device.code,
                device.name,
                device.device_type,
                device.device_number,
                device.controllable,
                device.plc_data_type,
                device.plc_command_tag,
                device.plc_state_tag,
                device.plc_fault_tag
            FROM building_device device
            INNER JOIN building_area area
                ON area.id = device.area_id
            WHERE area.code = ?
              AND area.active = TRUE
              AND device.active = TRUE
            ORDER BY device.display_order
            """;

            private static final String DEVICE_COMMAND_QUERY = """
                SELECT
                        device.code,
                        area.code AS area_code,
                        device.controllable,
                        device.plc_data_type,
                        device.plc_command_tag
                FROM building_device device
                INNER JOIN building_area area
                        ON area.id = device.area_id
                WHERE UPPER(device.code) = ?
                AND device.active = TRUE
                AND area.active = TRUE
                """;

    private final JdbcTemplate jdbcTemplate;
    private final PlcProperties plcProperties;
    private final SignalQualityRegistry signalQualityRegistry;

    public AreaStateService(
            JdbcTemplate jdbcTemplate,
            PlcProperties plcProperties,
            PlcCommunicationService plcCommunicationService,
            SignalQualityRegistry signalQualityRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.plcProperties = plcProperties;
        this.plcCommunicationService = plcCommunicationService;
        this.signalQualityRegistry = signalQualityRegistry;
    }

    public synchronized AreaStateResponse getAreaState(
            String requestedAreaCode
    ) {
        String areaCode = requestedAreaCode
                .trim()
                .toUpperCase(Locale.ROOT);

        AreaInfo area = findArea(areaCode);
        List<DeviceDefinition> devices = findDevices(areaCode);

        if (devices.isEmpty()) {
            return unavailableResponse(
                    area,
                    devices,
                    "El área todavía no tiene dispositivos configurados."
            );
        }

        if (!plcProperties.isEnabled()) {
            return unavailableResponse(
                    area,
                    devices,
                    "La comunicación con el PLC está deshabilitada."
            );
        }

        if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

            return unavailableResponse(
                    area,
                    devices,
                    "No se configuró PLC_CONNECTION_STRING."
            );
        }

                try {
                        return readAreaState(area, devices);

                } catch (PlcUnavailableException exception) {

                        return unavailableResponse(
                                area,
                                devices,
                                exception.getMessage()
                        );
                }

    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AreaStateResponse readAreaState(
        AreaInfo area,
        List<DeviceDefinition> devices
        ) {
                return plcCommunicationService.read(connection -> {

                        if (!connection.getMetadata().isReadSupported()) {
                        throw new IllegalStateException(
                                "La conexión no permite leer tags."
                        );
                        }

                        PlcReadRequest.Builder builder =
                                connection.readRequestBuilder();

                        for (DeviceDefinition device : devices) {

                        if (hasText(device.commandTag())) {
                                builder.addTagAddress(
                                        commandAlias(device),
                                        device.commandTag()
                                );
                        }

                        if (!hasText(device.stateTag())) {
                                throw new IllegalStateException(
                                        "El dispositivo "
                                                + device.code()
                                                + " no tiene plc_state_tag configurado."
                                );
                        }

                        builder.addTagAddress(
                                stateAlias(device),
                                device.stateTag()
                        );

                        if (hasText(device.faultTag())) {
                                builder.addTagAddress(
                                        faultAlias(device),
                                        device.faultTag()
                                );
                        }
                        }

                        PlcReadResponse response = builder
                                .build()
                                .execute()
                                .get(
                                        plcProperties.getTimeout().toMillis(),
                                        TimeUnit.MILLISECONDS
                                );

                        List<DeviceStateResponse> states = devices.stream()
                                .map(device -> toDeviceState(
                                        response,
                                        device
                                ))
                                .toList();

                        long problemSignals = states.stream()
                                .filter(device ->
                                        device.quality()
                                                != SignalQuality.GOOD
                                )
                                .count();

                        String message = problemSignals == 0
                                ? "Estados leídos correctamente."
                                : "PLC conectado; "
                                        + problemSignals
                                        + " señales requieren revisión.";

                        return new AreaStateResponse(
                                area.code(),
                                area.name(),
                                true,
                                true,
                                states,
                                message,
                                Instant.now()
                        );
                });
        }

    private DeviceStateResponse toDeviceState(
            PlcReadResponse response,
            DeviceDefinition device
    ) {
        Boolean command = hasText(device.commandTag())
                ? readBooleanResult(
                        response,
                        commandAlias(device),
                        device.commandTag()
                ).value()
                : null;

        Boolean fault = hasText(device.faultTag())
                ? readBooleanResult(
                        response,
                        faultAlias(device),
                        device.faultTag()
                ).value()
                : null;

        BooleanReadResult stateRead = readBooleanResult(
                response,
                stateAlias(device),
                device.stateTag()
        );

        if (stateRead.error() == null) {
            signalQualityRegistry.recordGood(
                    device.id(),
                    stateRead.value()
            );
        } else {
            signalQualityRegistry.recordBad(
                    device.id(),
                    stateRead.error()
            );
        }

        SignalQualitySnapshot quality =
                signalQualityRegistry.snapshot(device.id());

        return new DeviceStateResponse(
                device.id(),
                device.code(),
                device.name(),
                device.type(),
                device.number(),
                device.controllable(),
                command,
                quality.value(),
                fault,
                quality.quality(),
                quality.lastUpdatedAt(),
                quality.detail()
        );
    }

    private AreaInfo findArea(String areaCode) {

        List<AreaInfo> areas = jdbcTemplate.query(
                AREA_QUERY,
                (resultSet, rowNumber) -> new AreaInfo(
                        resultSet.getString("code"),
                        resultSet.getString("name")
                ),
                areaCode
        );

        if (areas.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No existe el área " + areaCode
            );
        }

        return areas.getFirst();
    }

    private List<DeviceDefinition> findDevices(String areaCode) {

        return jdbcTemplate.query(
                DEVICES_QUERY,
                (resultSet, rowNumber) -> new DeviceDefinition(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("device_type"),
                        resultSet.getInt("device_number"),
                        resultSet.getBoolean("controllable"),
                        resultSet.getString("plc_data_type"),
                        resultSet.getString("plc_command_tag"),
                        resultSet.getString("plc_state_tag"),
                        resultSet.getString("plc_fault_tag")
                ),
                areaCode
        );
    }

    private BooleanReadResult readBooleanResult(
            PlcReadResponse response,
            String alias,
            String tag
    ) {
        PlcResponseCode responseCode =
                response.getResponseCode(alias);

        if (responseCode != PlcResponseCode.OK) {
            return new BooleanReadResult(
                    null,
                    "El PLC respondió "
                            + responseCode
                            + " al leer "
                            + tag
                            + "."
            );
        }

        if (!response.isValidBoolean(alias)) {
            return new BooleanReadResult(
                    null,
                    tag + " no devolvió un BOOL."
            );
        }

        return new BooleanReadResult(
                response.getBoolean(alias),
                null
        );
    }

    private AreaStateResponse unavailableResponse(
            AreaInfo area,
            List<DeviceDefinition> devices,
            String message
    ) {
        signalQualityRegistry.recordBad(
                devices.stream()
                        .map(DeviceDefinition::id)
                        .toList(),
                message
        );

        List<DeviceStateResponse> unavailableDevices = devices.stream()
                .map(device -> {
                    SignalQualitySnapshot quality =
                            signalQualityRegistry.snapshot(device.id());

                    return new DeviceStateResponse(
                            device.id(),
                            device.code(),
                            device.name(),
                            device.type(),
                            device.number(),
                            device.controllable(),
                            null,
                            quality.value(),
                            null,
                            quality.quality(),
                            quality.lastUpdatedAt(),
                            quality.detail()
                    );
                })
                .toList();

        return new AreaStateResponse(
                area.code(),
                area.name(),
                plcProperties.isEnabled(),
                false,
                unavailableDevices,
                message,
                Instant.now()
        );
    }

    private String commandAlias(DeviceDefinition device) {
        return "command_" + device.id();
    }

    private String stateAlias(DeviceDefinition device) {
        return "state_" + device.id();
    }

    private String faultAlias(DeviceDefinition device) {
        return "fault_" + device.id();
    }

    private record AreaInfo(
            String code,
            String name
    ) {
    }

    private record DeviceDefinition(
            Long id,
            String code,
            String name,
            String type,
            int number,
            boolean controllable,
            String dataType,
            String commandTag,
            String stateTag,
            String faultTag
    ) {
    }

    private record BooleanReadResult(
            Boolean value,
            String error
    ) {
    }

    public synchronized AreaStateResponse commandDevice(
        String requestedDeviceCode,
        boolean on) 
        {
                String deviceCode = requestedDeviceCode.trim().toUpperCase();

                CommandDevice device = findCommandDevice(deviceCode);

                if (!device.controllable() || device.commandTag() == null) {
                        throw new ConflictException(
                        "El dispositivo " + deviceCode + " no admite comandos."
                        );
                }

                if (!"BOOL".equalsIgnoreCase(device.dataType())) {
                        throw new ConflictException(
                        "El dispositivo " + deviceCode
                                + " no admite comandos de encendido y apagado."
                        );
                }

                if (!plcProperties.isEnabled()) {
                throw new PlcUnavailableException(
                        "No se pudo ejecutar el comando porque la comunicación "
                        + "con el PLC está deshabilitada."
                );
                }

                if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

                throw new PlcUnavailableException(
                        "No se pudo ejecutar el comando porque no está configurada "
                        + "la dirección de conexión del PLC."
                );
                }

                plcCommunicationService.write(connection -> {

                if (!connection.getMetadata().isWriteSupported()) {
                        throw new IllegalStateException(
                                "La conexión configurada no permite escribir tags."
                        );
                }

                PlcWriteRequest.Builder builder =
                        connection.writeRequestBuilder();

                builder.addTagAddress(
                        "deviceCommand",
                        device.commandTag()
                                + ":"
                                + device.dataType(),
                        on
                );

                PlcWriteResponse response = builder
                        .build()
                        .execute()
                        .get(
                                plcProperties.getTimeout().toMillis(),
                                TimeUnit.MILLISECONDS
                        );

                PlcResponseCode responseCode =
                        response.getResponseCode("deviceCommand");

                if (responseCode != PlcResponseCode.OK) {
                        throw new IllegalStateException(
                                "El PLC respondió "
                                        + responseCode
                                        + " al escribir "
                                        + device.commandTag()
                        );
                }

                return null;
                });

                try {
                        Thread.sleep(100);
                } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();

                        throw new PlcUnavailableException(
                                "La espera de confirmación del PLC fue interrumpida.",
                                exception
                        );
                }

                AreaInfo area = findArea(device.areaCode());
                List<DeviceDefinition> devices =
                        findDevices(device.areaCode());

                return readAreaState(area, devices);
        }
        private CommandDevice findCommandDevice(String deviceCode) {

                List<CommandDevice> devices = jdbcTemplate.query(
                        DEVICE_COMMAND_QUERY,
                        (resultSet, rowNumber) -> new CommandDevice(
                        resultSet.getString("code"),
                        resultSet.getString("area_code"),
                        resultSet.getBoolean("controllable"),
                        resultSet.getString("plc_data_type"),
                        resultSet.getString("plc_command_tag")
                        ),
                        deviceCode
                );

                if (devices.isEmpty()) {
                        throw new ResourceNotFoundException(
                        "No se encontró el dispositivo " + deviceCode + "."
                        );
                }

                return devices.get(0);
        }

        private record CommandDevice(
                String code,
                String areaCode,
                boolean controllable,
                String dataType,
                String commandTag
                ) {
        }
}
