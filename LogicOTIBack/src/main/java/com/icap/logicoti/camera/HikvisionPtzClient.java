package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraPtzProperties;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

@Component
class HikvisionPtzClient implements PtzCommandGateway {

    private static final Pattern STATUS_CODE = Pattern.compile(
            "<(?:[A-Za-z0-9_]+:)?statusCode>\\s*([0-9]+)\\s*</(?:[A-Za-z0-9_]+:)?statusCode>"
    );

    private final CameraPtzProperties properties;

    HikvisionPtzClient(CameraPtzProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(int channel, CameraPtzDirection direction) {
        if (!properties.isConfigured() || !properties.isChannelAllowed(channel)) {
            throw new CameraPtzUnavailableException("El control PTZ no está configurado.");
        }

        URI endpoint = endpoint(channel);
        BasicCredentialsProvider credentials = new BasicCredentialsProvider();
        credentials.setCredentials(
                new AuthScope(endpoint.getHost(), endpoint.getPort() > 0
                        ? endpoint.getPort()
                        : ("https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80)),
                new UsernamePasswordCredentials(
                        properties.getUsername(), properties.getPassword().toCharArray()
                )
        );
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout().toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getResponseTimeout().toMillis()))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(credentials)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build()) {
            HttpPut request = new HttpPut(endpoint);
            request.setEntity(new StringEntity(body(direction, properties.getSpeed()), ContentType.APPLICATION_XML));
            client.execute(request, response -> {
                int status = response.getCode();
                String responseBody = response.getEntity() == null
                        ? "" : EntityUtils.toString(response.getEntity());
                if (status < 200 || status >= 300) {
                    throw new CameraPtzUnavailableException(
                            "El NVR rechazó el comando PTZ (HTTP " + status + ")."
                    );
                }
                var result = STATUS_CODE.matcher(responseBody);
                if (result.find() && !"1".equals(result.group(1))) {
                    throw new CameraPtzUnavailableException("El NVR no pudo ejecutar el comando PTZ.");
                }
                return null;
            });
        } catch (CameraPtzUnavailableException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CameraPtzUnavailableException(
                    "No fue posible comunicarse con el NVR para controlar la cámara.", exception
            );
        }
    }

    URI endpoint(int channel) {
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        String path = "proxy".equalsIgnoreCase(properties.getEndpointMode())
                ? "/ISAPI/ContentMgmt/PTZCtrlProxy/channels/"
                : "/ISAPI/PTZCtrl/channels/";
        return URI.create(base + path + channel + "/continuous");
    }

    static String body(CameraPtzDirection direction, int speed) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PTZData version="2.0" xmlns="http://www.isapi.org/ver20/XMLSchema">
                  <pan>%d</pan>
                  <tilt>%d</tilt>
                  <zoom>%d</zoom>
                </PTZData>
                """.formatted(direction.pan(speed), direction.tilt(speed), direction.zoom(speed));
    }
}
