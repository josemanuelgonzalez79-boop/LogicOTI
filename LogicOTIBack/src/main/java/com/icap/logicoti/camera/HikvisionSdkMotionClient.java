package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraHistoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
class HikvisionSdkMotionClient implements NvrMotionClient {
    private static final Logger log = LoggerFactory.getLogger(HikvisionSdkMotionClient.class);
    private static final int MAX_OUTPUT_BYTES = 2_000_000;

    private final CameraHistoryProperties properties;
    private final JsonMapper mapper;

    HikvisionSdkMotionClient(CameraHistoryProperties properties, JsonMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public boolean enabled() {
        return properties.isMotionSdkEnabled();
    }

    @Override
    public List<NvrMotionInterval> search(int channel, LocalDateTime start, LocalDateTime end) {
        Path output = null;
        try {
            Path script = Path.of(properties.getMotionSdkScript()).toAbsolutePath();
            Path dll = Path.of(properties.getMotionSdkDll()).toAbsolutePath();
            if (!Files.isRegularFile(script) || !Files.isRegularFile(dll)
                    || properties.getMotionSdkPort() < 1 || properties.getMotionSdkPort() > 65535
                    || properties.getMotionSdkTimeout() == null
                    || properties.getMotionSdkTimeout().isNegative()
                    || properties.getMotionSdkTimeout().isZero()) {
                throw new IOException("Revise CAMERA_MOTION_SDK_SCRIPT, CAMERA_MOTION_SDK_DLL, puerto y timeout.");
            }
            output = Files.createTempFile("logicoti-motion-", ".json");
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString(), "-Json", "-SdkDll", dll.toString(),
                    "-NvrHost", properties.getBaseUrl().getHost(),
                    "-Port", Integer.toString(properties.getMotionSdkPort()),
                    "-Channel", Integer.toString(channel),
                    "-Start", start.toString(), "-End", end.toString()
            );
            builder.environment().put("LOGICOTI_SDK_NVR_USERNAME", properties.getUsername());
            builder.environment().put("LOGICOTI_SDK_NVR_PASSWORD", properties.getPassword());
            builder.redirectErrorStream(true);
            builder.redirectOutput(output.toFile());
            Process process = builder.start();
            if (!process.waitFor(properties.getMotionSdkTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("La búsqueda de movimiento excedió el tiempo límite.");
            }
            if (Files.size(output) > MAX_OUTPUT_BYTES) {
                throw new IOException("La respuesta del SDK excedió el límite de tamaño.");
            }
            String body = Files.readString(output, StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) {
                throw new IOException("El SDK terminó con código " + process.exitValue()
                        + "; revise la ruta del SDK, el puerto y los permisos de la cuenta NVR.");
            }
            Map<?, ?> root = mapper.readValue(body, Map.class);
            if (root == null || !Boolean.TRUE.equals(root.get("complete"))
                    || !(root.get("channel") instanceof Number resultChannel)
                    || resultChannel.intValue() != channel
                    || !(root.get("events") instanceof List<?> events)) {
                throw new IOException("El SDK devolvió una respuesta de movimiento incompleta.");
            }
            List<NvrMotionInterval> intervals = new ArrayList<>();
            for (Object item : events) {
                if (!(item instanceof Map<?, ?> event)
                        || !(event.get("channel") instanceof Number eventChannel)
                        || eventChannel.intValue() != channel) {
                    throw new IOException("El SDK devolvió eventos de otro canal.");
                }
                LocalDateTime from = LocalDateTime.parse((String) event.get("start"));
                LocalDateTime until = LocalDateTime.parse((String) event.get("end"));
                if (until.isAfter(from)) {
                    intervals.add(new NvrMotionInterval(from, until));
                }
            }
            return List.copyOf(intervals);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("No fue posible consultar movimiento por el SDK para D{}: {}", channel,
                    exception.getMessage());
            throw new CameraHistoryUnavailableException("No fue posible consultar los eventos de movimiento del NVR.", exception);
        } finally {
            if (output != null) {
                try { Files.deleteIfExists(output); }
                catch (IOException exception) { log.debug("No se pudo borrar la salida temporal del SDK.", exception); }
            }
        }
    }
}
