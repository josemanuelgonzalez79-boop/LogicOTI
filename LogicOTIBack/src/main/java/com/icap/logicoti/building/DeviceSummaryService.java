package com.icap.logicoti.building;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.exception.PlcUnavailableException;
import com.icap.logicoti.plc.PlcCommunicationService;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
public class DeviceSummaryService {

    private static final int TAGS_PER_REQUEST = 20;
    private static final long CACHE_MILLIS = 3_000;

    private static final String CONTROLLABLE_DEVICES_QUERY = """
            SELECT
                id,
                device_type,
                plc_state_tag
            FROM building_device
            WHERE active = TRUE
              AND controllable = TRUE
              AND plc_data_type = 'BOOL'
              AND device_type IN ('LIGHT', 'MINISPLIT')
            ORDER BY id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PlcProperties plcProperties;
    private final PlcCommunicationService plcCommunicationService;

    private DeviceSummaryResponse cachedSummary;
    private long cachedAtMillis;

    public DeviceSummaryService(
            JdbcTemplate jdbcTemplate,
            PlcProperties plcProperties,
            PlcCommunicationService plcCommunicationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.plcProperties = plcProperties;
        this.plcCommunicationService = plcCommunicationService;
    }

    public synchronized DeviceSummaryResponse getSummary() {
        long now = System.currentTimeMillis();

        if (cachedSummary != null
                && now - cachedAtMillis < CACHE_MILLIS) {

            return cachedSummary;
        }

        cachedSummary = loadSummary();
        cachedAtMillis = System.currentTimeMillis();

        return cachedSummary;
    }

    private DeviceSummaryResponse loadSummary() {
        List<DeviceDefinition> devices =
                findControllableDevices();

        if (!plcProperties.isEnabled()) {
            return unavailableResponse(
                    devices.size(),
                    "La comunicación con el PLC está deshabilitada."
            );
        }

        if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

            return unavailableResponse(
                    devices.size(),
                    "No se configuró PLC_CONNECTION_STRING."
            );
        }

        if (devices.isEmpty()) {
            return emptyResponse();
        }

        try {
            return plcCommunicationService.read(
                    connection ->
                            readSummary(
                                    connection,
                                    devices
                            )
            );

        } catch (PlcUnavailableException exception) {

            return unavailableResponse(
                    devices.size(),
                    exception.getMessage()
            );
        }
    }

    private DeviceSummaryResponse readSummary(
            PlcConnection connection,
            List<DeviceDefinition> devices
    )  {

        validateReadSupport(connection);

        DeviceCounts counts =
                readDeviceCounts(
                        connection,
                        devices
                );

        return new DeviceSummaryResponse(
                true,
                true,
                devices.size(),
                counts.totalOn(),
                counts.lightsOn(),
                counts.minisplitsOn(),
                "Estados confirmados por el PLC.",
                Instant.now()
        );
    }

    private DeviceCounts readDeviceCounts(
            PlcConnection connection,
            List<DeviceDefinition> devices
    )  {

        int lightsOn = 0;
        int minisplitsOn = 0;

        for (
                int start = 0;
                start < devices.size();
                start += TAGS_PER_REQUEST
        ) {

            int end = Math.min(
                    start + TAGS_PER_REQUEST,
                    devices.size()
            );

            List<DeviceDefinition> batch =
                    devices.subList(
                            start,
                            end
                    );

            DeviceCounts batchCounts =
                    readBatch(
                            connection,
                            batch
                    );

            lightsOn += batchCounts.lightsOn();
            minisplitsOn += batchCounts.minisplitsOn();
        }

        return new DeviceCounts(
                lightsOn,
                minisplitsOn
        );
    }

    private DeviceCounts readBatch(
            PlcConnection connection,
            List<DeviceDefinition> batch
    ) {

        PlcReadRequest.Builder builder =
                connection.readRequestBuilder();

        addTagsToRequest(
                builder,
                batch
        );

        PlcReadResponse response =
                executeRead(builder);

        return countActiveDevices(
                response,
                batch
        );
    }

    private void addTagsToRequest(
            PlcReadRequest.Builder builder,
            List<DeviceDefinition> batch
    ) {

        for (DeviceDefinition device : batch) {
            builder.addTagAddress(
                    alias(device),
                    device.stateTag()
            );
        }
    }

        private PlcReadResponse executeRead(
                PlcReadRequest.Builder builder
        ) {
        try {
                return builder
                        .build()
                        .execute()
                        .get(
                                plcProperties
                                        .getTimeout()
                                        .toMillis(),
                                TimeUnit.MILLISECONDS
                        );

        } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new PlcUnavailableException(
                        "La lectura del PLC fue interrumpida.",
                        exception
                );

        } catch (ExecutionException | TimeoutException exception) {
                throw new PlcUnavailableException(
                        "No se pudo completar la lectura del PLC.",
                        exception
                );
        }
        }

    private DeviceCounts countActiveDevices(
            PlcReadResponse response,
            List<DeviceDefinition> batch
    ) {

        int lightsOn = 0;
        int minisplitsOn = 0;

        for (DeviceDefinition device : batch) {

            if (!readBoolean(
                    response,
                    device
            )) {
                continue;
            }

            if ("LIGHT".equals(device.type())) {
                lightsOn++;
            }

            if ("MINISPLIT".equals(device.type())) {
                minisplitsOn++;
            }
        }

        return new DeviceCounts(
                lightsOn,
                minisplitsOn
        );
    }

    private void validateReadSupport(
            PlcConnection connection
    ) {

        if (!connection
                .getMetadata()
                .isReadSupported()) {

            throw new IllegalStateException(
                    "La conexión no permite leer tags."
            );
        }
    }

    private DeviceSummaryResponse emptyResponse() {

        return new DeviceSummaryResponse(
                true,
                true,
                0,
                0,
                0,
                0,
                "No hay luces ni minisplits configurados para control.",
                Instant.now()
        );
    }

    private List<DeviceDefinition> findControllableDevices() {

        return jdbcTemplate.query(
                CONTROLLABLE_DEVICES_QUERY,
                (resultSet, rowNumber) ->
                        new DeviceDefinition(
                                resultSet.getLong("id"),
                                resultSet.getString(
                                        "device_type"
                                ),
                                resultSet.getString(
                                        "plc_state_tag"
                                )
                        )
        );
    }

    private boolean readBoolean(
            PlcReadResponse response,
            DeviceDefinition device
    ) {

        String alias = alias(device);

        PlcResponseCode responseCode =
                response.getResponseCode(alias);

        if (responseCode != PlcResponseCode.OK) {

            throw new IllegalStateException(
                    "No se pudo leer "
                            + device.stateTag()
                            + ". Respuesta: "
                            + responseCode
            );
        }

        if (!response.isValidBoolean(alias)) {

            throw new IllegalStateException(
                    device.stateTag()
                            + " no devolvió un valor BOOL."
            );
        }

        return response.getBoolean(alias);
    }

    private DeviceSummaryResponse unavailableResponse(
            int totalControllable,
            String message
    ) {

        return new DeviceSummaryResponse(
                plcProperties.isEnabled(),
                false,
                totalControllable,
                null,
                null,
                null,
                message,
                Instant.now()
        );
    }

    private String alias(
            DeviceDefinition device
    ) {

        return "state_" + device.id();
    }

    private record DeviceDefinition(
            Long id,
            String type,
            String stateTag
    ) {
    }

    private record DeviceCounts(
            int lightsOn,
            int minisplitsOn
    ) {

        int totalOn() {
            return lightsOn
                    + minisplitsOn;
        }
    }
}