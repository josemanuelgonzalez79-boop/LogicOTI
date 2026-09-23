package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraPtzProperties;
import com.icap.logicoti.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;

class CameraPtzServiceTests {

    private final CameraService cameras = mock(CameraService.class);
    private final PtzCommandGateway gateway = mock(PtzCommandGateway.class);
    private final CameraPtzProperties properties = new CameraPtzProperties();
    private CameraPtzService service;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://192.0.2.18"));
        properties.setUsername("operator");
        properties.setPassword("example-only");
        properties.setChannels(List.of(25, 26, 27, 28));
        properties.setPulseDuration(Duration.ofMillis(100));
        service = new CameraPtzService(cameras, properties, gateway);
    }

    @Test
    void sendsStopAfterMoving() {
        when(cameras.findByCode("CAM-025")).thenReturn(camera(25));

        service.pulse("CAM-025", CameraPtzDirection.LEFT);

        var commands = inOrder(gateway);
        commands.verify(gateway).send(25, CameraPtzDirection.LEFT);
        commands.verify(gateway).send(25, CameraPtzDirection.STOP);
    }

    @Test
    void attemptsStopEvenWhenFirstCommandFails() {
        when(cameras.findByCode("CAM-025")).thenReturn(camera(25));
        doThrow(new CameraPtzUnavailableException("NVR no disponible"))
                .when(gateway).send(25, CameraPtzDirection.UP);

        assertThrows(CameraPtzUnavailableException.class,
                () -> service.pulse("CAM-025", CameraPtzDirection.UP));
        verify(gateway).send(25, CameraPtzDirection.STOP);
    }

    @Test
    void retriesStopOnceAfterACommunicationFailure() {
        when(cameras.findByCode("CAM-025")).thenReturn(camera(25));
        doThrow(new CameraPtzUnavailableException("Sin respuesta"))
                .doNothing().when(gateway).send(25, CameraPtzDirection.STOP);

        service.pulse("CAM-025", CameraPtzDirection.UP);

        verify(gateway, times(2)).send(25, CameraPtzDirection.STOP);
    }

    @Test
    void rejectsOtherChannelsWithoutContactingNvr() {
        when(cameras.findByCode("CAM-024")).thenReturn(camera(24));

        assertThrows(BadRequestException.class,
                () -> service.pulse("CAM-024", CameraPtzDirection.LEFT));
        verifyNoInteractions(gateway);
    }

    private CameraResponse camera(int channel) {
        return new CameraResponse((long) channel, "CAM-%03d".formatted(channel),
                channel, "Cámara " + channel, "EXT", null, "NVR", "oti-cam-25",
                true, true, "http://video.local", channel == 25);
    }
}
