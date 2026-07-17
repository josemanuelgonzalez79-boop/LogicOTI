package com.icap.logicoti.device;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.device.AreaStateResponse.DeviceStateResponse;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
public class AreaStateService {

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

    private final JdbcTemplate jdbcTemplate;
    private final PlcProperties plcProperties;

    public AreaStateService(
            JdbcTemplate jdbcTemplate,
            PlcProperties plcProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.plcProperties = plcProperties;
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

        try (PlcConnection connection = PlcDriverManager.getDefault()
                .getConnectionManager()
                .getConnection(plcProperties.getConnectionString())) {

            if (!connection.getMetadata().isReadSupported()) {
                throw new IllegalStateException(
                        "La conexión no permite leer tags."
                );
            }

            PlcReadRequest.Builder builder =
                    connection.readRequestBuilder();

            for (DeviceDefinition device : devices) {

                if (device.commandTag() != null) {
                    builder.addTagAddress(
                            commandAlias(device),
                            device.commandTag()
                    );
                }

                builder.addTagAddress(
                        stateAlias(device),
                        device.stateTag()
                );

                if (device.faultTag() != null) {
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
                    .map(device -> new DeviceStateResponse(
                            device.id(),
                            device.code(),
                            device.name(),
                            device.type(),
                            device.number(),
                            device.controllable(),

                            device.commandTag() == null
                                    ? null
                                    : readBoolean(
                                            response,
                                            commandAlias(device)
                                    ),

                            readBoolean(
                                    response,
                                    stateAlias(device)
                            ),

                            device.faultTag() == null
                                    ? null
                                    : readBoolean(
                                            response,
                                            faultAlias(device)
                                    )
                    ))
                    .toList();

            return new AreaStateResponse(
                    area.code(),
                    area.name(),
                    true,
                    true,
                    states,
                    "Estados leídos correctamente.",
                    Instant.now()
            );

        } catch (Exception exception) {

            return unavailableResponse(
                    area,
                    devices,
                    exception.getClass().getSimpleName()
                            + ": "
                            + exception.getMessage()
            );
        }
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

    private Boolean readBoolean(
            PlcReadResponse response,
            String alias
    ) {
        PlcResponseCode responseCode =
                response.getResponseCode(alias);

        if (responseCode != PlcResponseCode.OK) {
            throw new IllegalStateException(
                    "No se pudo leer "
                            + alias
                            + ". Respuesta: "
                            + responseCode
            );
        }

        if (!response.isValidBoolean(alias)) {
            throw new IllegalStateException(
                    alias + " no devolvió un valor BOOL."
            );
        }

        return response.getBoolean(alias);
    }

    private AreaStateResponse unavailableResponse(
            AreaInfo area,
            List<DeviceDefinition> devices,
            String message
    ) {
        List<DeviceStateResponse> unavailableDevices = devices.stream()
                .map(device -> new DeviceStateResponse(
                        device.id(),
                        device.code(),
                        device.name(),
                        device.type(),
                        device.number(),
                        device.controllable(),
                        null,
                        null,
                        null
                ))
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
}