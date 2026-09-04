package com.icap.logicoti.plc;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.exception.PlcUnavailableException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class PlcService {

    private final PlcProperties plcProperties;
    private final PlcCommunicationService plcCommunicationService;
    private static final String CONNECTION_DISABLED = "La comunicación con el PLC está deshabilitada.";
    private static final String LIGHT_COMMAND = "lightCommand";

    public PlcService(
            PlcProperties plcProperties,
            PlcCommunicationService plcCommunicationService
    ) {
        this.plcProperties = plcProperties;
        this.plcCommunicationService = plcCommunicationService;
    }

    public PlcConnectionResponse getConnectionStatus() {

        if (!plcProperties.isEnabled()) {
            return new PlcConnectionResponse(
                    false,
                    false,
                    CONNECTION_DISABLED,
                    Instant.now()
            );
        }

        try {
            return plcCommunicationService.read(connection ->
                    new PlcConnectionResponse(
                            true,
                            connection.isConnected(),
                            "Conexión establecida correctamente con el PLC.",
                            Instant.now()
                    )
            );

        } catch (PlcUnavailableException exception) {
            return new PlcConnectionResponse(
                    true,
                    false,
                    exception.getMessage(),
                    Instant.now()
            );
        }
    }

    public PlcTestResponse readTestTags() {

        if (!plcProperties.isEnabled()) {
            return unavailableTestResponse(
                    false,
                    CONNECTION_DISABLED
            );
        }

        try {
            return plcCommunicationService.read(connection -> {

                if (!connection.getMetadata().isReadSupported()) {
                    throw new IllegalStateException(
                            "La conexión no permite leer tags."
                    );
                }

                PlcReadRequest.Builder builder =
                        connection.readRequestBuilder();

                builder.addTagAddress(
                        LIGHT_COMMAND,
                        "OTI_TEST_LIGHT_CMD"
                );

                builder.addTagAddress(
                        "lightFeedback",
                        "OTI_TEST_LIGHT_FB"
                );

                builder.addTagAddress(
                        "motion",
                        "OTI_TEST_MOTION"
                );

                builder.addTagAddress(
                        "smoke",
                        "OTI_TEST_SMOKE"
                );

                PlcReadResponse response = builder
                        .build()
                        .execute()
                        .get(
                                plcProperties.getTimeout().toMillis(),
                                TimeUnit.MILLISECONDS
                        );

                return new PlcTestResponse(
                        true,
                        true,
                        readBoolean(response, LIGHT_COMMAND),
                        readBoolean(response, "lightFeedback"),
                        readBoolean(response, "motion"),
                        readBoolean(response, "smoke"),
                        "Tags leídos correctamente.",
                        Instant.now()
                );
            });

        } catch (PlcUnavailableException exception) {
            return unavailableTestResponse(
                    true,
                    exception.getMessage()
            );
        }
    }

    public PlcTestResponse writeLightCommand(boolean on) {

        if (!plcProperties.isEnabled()) {
            return unavailableTestResponse(
                    false,
                    CONNECTION_DISABLED
            );
        }

        try {
            plcCommunicationService.write(connection -> {

                if (!connection.getMetadata().isWriteSupported()) {
                    throw new IllegalStateException(
                            "La conexión no permite escribir tags."
                    );
                }

                PlcWriteRequest.Builder builder =
                        connection.writeRequestBuilder();

                builder.addTagAddress(
                        LIGHT_COMMAND,
                        "OTI_TEST_LIGHT_CMD:BOOL",
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
                        response.getResponseCode(LIGHT_COMMAND);

                if (responseCode != PlcResponseCode.OK) {
                    throw new IllegalStateException(
                            "No se pudo escribir OTI_TEST_LIGHT_CMD. "
                                    + "Respuesta: "
                                    + responseCode
                    );
                }

                return null;
            });

            try {
                Thread.sleep(100);

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new PlcUnavailableException(
                        "La espera de confirmación fue interrumpida.",
                        exception
                );
            }

            return readTestTags();

        } catch (PlcUnavailableException exception) {
            return unavailableTestResponse(
                    true,
                    exception.getMessage()
            );
        }
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

    private PlcTestResponse unavailableTestResponse(
            boolean enabled,
            String message
    ) {
        return new PlcTestResponse(
                enabled,
                false,
                null,
                null,
                null,
                null,
                message,
                Instant.now()
        );
    }
}