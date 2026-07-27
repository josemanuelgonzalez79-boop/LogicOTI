package com.icap.logicoti.plc;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.exception.PlcUnavailableException;
import jakarta.annotation.PreDestroy;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.springframework.stereotype.Service;

@Service
public class PlcCommunicationService {

    private final PlcProperties plcProperties;
    private final Object connectionLock = new Object();

    private PlcConnection connection;

    public PlcCommunicationService(PlcProperties plcProperties) {
        this.plcProperties = plcProperties;
    }

    public <T> T read(PlcOperation<T> operation) {
        return execute(operation, true);
    }

    public <T> T write(PlcOperation<T> operation) {
        return execute(operation, false);
    }

    private <T> T execute(
            PlcOperation<T> operation,
            boolean retryOnce
    ) {
        validateConfiguration();

        synchronized (connectionLock) {
            try {
                return operation.execute(getActiveConnection());

            } catch (Exception firstException) {
                restoreInterruptedState(firstException);
                closeConnection();

                if (retryOnce) {
                    try {
                        return operation.execute(getActiveConnection());

                    } catch (Exception secondException) {
                        restoreInterruptedState(secondException);
                        closeConnection();

                        throw unavailableException(secondException);
                    }
                }

                throw unavailableException(firstException);
            }
        }
    }

    private PlcConnection getActiveConnection() throws Exception {

        if (connection == null || !connection.isConnected()) {
            closeConnection();

            connection = PlcDriverManager
                    .getDefault()
                    .getConnectionManager()
                    .getConnection(
                            plcProperties.getConnectionString()
                    );

            if (!connection.isConnected()) {
                connection.connect();
            }
        }

        return connection;
    }

    private void validateConfiguration() {

        if (!plcProperties.isEnabled()) {
            throw new PlcUnavailableException(
                    "La comunicación con el PLC está deshabilitada."
            );
        }

        if (plcProperties.getConnectionString() == null
                || plcProperties.getConnectionString().isBlank()) {

            throw new PlcUnavailableException(
                    "No se configuró PLC_CONNECTION_STRING."
            );
        }
    }

    private PlcUnavailableException unavailableException(
            Exception exception
    ) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        } else {
            message = exception.getClass().getSimpleName()
                    + ": "
                    + message;
        }

        return new PlcUnavailableException(
                "Error de comunicación con el PLC: " + message,
                exception
        );
    }

    private void restoreInterruptedState(Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeConnection() {

        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (Exception ignored) {
            // La conexión ya se considera inválida.
        } finally {
            connection = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (connectionLock) {
            closeConnection();
        }
    }

    @FunctionalInterface
    public interface PlcOperation<T> {

        T execute(PlcConnection connection) throws Exception;
    }
}