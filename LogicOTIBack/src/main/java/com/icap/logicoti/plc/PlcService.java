package com.icap.logicoti.plc;

import com.icap.logicoti.config.PlcProperties;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.springframework.stereotype.Service;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;

import java.util.concurrent.TimeUnit;

import java.time.Instant;

@Service
public class PlcService {

    private final PlcProperties plcProperties;

    public PlcService(PlcProperties plcProperties) {
        this.plcProperties = plcProperties;
    }

    public PlcConnectionResponse getConnectionStatus() {

        if (!plcProperties.isEnabled()) {
            return new PlcConnectionResponse(
                    false,
                    false,
                    "La comunicación con el PLC está deshabilitada.",
                    Instant.now()
            );
        }

        if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

            return new PlcConnectionResponse(
                    true,
                    false,
                    "No se configuró PLC_CONNECTION_STRING.",
                    Instant.now()
            );
        }

        try (PlcConnection ignored = PlcDriverManager.getDefault()
                .getConnectionManager()
                .getConnection(plcProperties.getConnectionString())) {

            return new PlcConnectionResponse(
                    true,
                    true,
                    "Conexión establecida correctamente con el PLC.",
                    Instant.now()
            );

        } catch (Exception exception) {

            return new PlcConnectionResponse(
                    true,
                    false,
                    exception.getClass().getSimpleName()
                            + ": "
                            + exception.getMessage(),
                    Instant.now()
            );
        }
    }

    public PlcTestResponse readTestTags() {

        if (!plcProperties.isEnabled()) {
            return new PlcTestResponse(
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    "La comunicación con el PLC está deshabilitada.",
                    Instant.now()
            );
        }

        if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

            return new PlcTestResponse(
                    true,
                    false,
                    null,
                    null,
                    null,
                    null,
                    "No se configuró PLC_CONNECTION_STRING.",
                    Instant.now()
            );
        }

        try (PlcConnection connection = PlcDriverManager.getDefault()
                .getConnectionManager()
                .getConnection(plcProperties.getConnectionString())) {

            if (!connection.getMetadata().isReadSupported()) {
                throw new IllegalStateException(
                        "El controlador o el driver no permite leer tags."
                );
            }

            PlcReadRequest.Builder builder = connection.readRequestBuilder();

            builder.addTagAddress(
                    "lightCommand",
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

            PlcReadRequest request = builder.build();

            PlcReadResponse response = request.execute().get(
                    plcProperties.getTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );

            return new PlcTestResponse(
                    true,
                    true,
                    readBoolean(response, "lightCommand"),
                    readBoolean(response, "lightFeedback"),
                    readBoolean(response, "motion"),
                    readBoolean(response, "smoke"),
                    "Tags leídos correctamente.",
                    Instant.now()
            );

        } catch (Exception exception) {

            return new PlcTestResponse(
                    true,
                    false,
                    null,
                    null,
                    null,
                    null,
                    exception.getClass().getSimpleName()
                            + ": "
                            + exception.getMessage(),
                    Instant.now()
            );
        }
    }

    private Boolean readBoolean(
            PlcReadResponse response,
            String tagName
    ) {

        PlcResponseCode responseCode =
                response.getResponseCode(tagName);

        if (responseCode != PlcResponseCode.OK) {
            throw new IllegalStateException(
                    "No se pudo leer "
                            + tagName
                            + ". Respuesta del PLC: "
                            + responseCode
            );
        }

        if (!response.isValidBoolean(tagName)) {
            throw new IllegalStateException(
                    "El tag "
                            + tagName
                            + " no devolvió un valor BOOL."
            );
        }

        return response.getBoolean(tagName);
    }

    public PlcTestResponse writeLightCommand(boolean on) {

        if (!plcProperties.isEnabled()) {
            return new PlcTestResponse(
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    "La comunicación con el PLC está deshabilitada.",
                    Instant.now()
            );
        }

        if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

            return new PlcTestResponse(
                    true,
                    false,
                    null,
                    null,
                    null,
                    null,
                    "No se configuró PLC_CONNECTION_STRING.",
                    Instant.now()
            );
        }

        try (PlcConnection connection = PlcDriverManager.getDefault()
                .getConnectionManager()
                .getConnection(plcProperties.getConnectionString())) {

            if (!connection.getMetadata().isWriteSupported()) {
                throw new IllegalStateException(
                        "El controlador o el driver no permite escribir tags."
                );
            }

            PlcWriteRequest.Builder builder =
                    connection.writeRequestBuilder();

            builder.addTagAddress(
                    "lightCommand",
                    "OTI_TEST_LIGHT_CMD:BOOL",
                    on
            );

            PlcWriteRequest request = builder.build();

            PlcWriteResponse response = request.execute().get(
                    plcProperties.getTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );

            PlcResponseCode responseCode =
                    response.getResponseCode("lightCommand");

            if (responseCode != PlcResponseCode.OK) {
                throw new IllegalStateException(
                        "No se pudo escribir OTI_TEST_LIGHT_CMD. "
                                + "Respuesta del PLC: "
                                + responseCode
                );
            }

        } catch (Exception exception) {

            return new PlcTestResponse(
                    true,
                    false,
                    null,
                    null,
                    null,
                    null,
                    exception.getClass().getSimpleName()
                            + ": "
                            + exception.getMessage(),
                    Instant.now()
            );
        }

        // Después de escribir, vuelve a leer los cuatro tags.
        return readTestTags();
    }
}