package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraHistoryProperties;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
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
import java.time.LocalDateTime;
import java.util.List;

@Component
class HikvisionNvrClient implements NvrRecordingClient {

    private static final String SEARCH_PATH =
            "/ISAPI/ContentMgmt/search";

    private final CameraHistoryProperties properties;

    HikvisionNvrClient(CameraHistoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<NvrRecordingSegment> search(
            int trackId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        ensureConfigured();
        URI endpoint = searchEndpoint();
        String requestBody = HikvisionNvrXmlCodec.buildSearchRequest(
                trackId,
                startTime,
                endTime,
                normalizedMaxResults()
        );

        BasicCredentialsProvider credentialsProvider =
                new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(endpoint.getHost(), effectivePort(endpoint)),
                new UsernamePasswordCredentials(
                        properties.getUsername(),
                        properties.getPassword().toCharArray()
                )
        );

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(
                        properties.getConnectTimeout().toMillis()
                ))
                .setResponseTimeout(Timeout.ofMilliseconds(
                        properties.getResponseTimeout().toMillis()
                ))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpPost request = new HttpPost(endpoint);
            request.setEntity(new StringEntity(
                    requestBody,
                    ContentType.APPLICATION_XML
            ));

            String responseBody = client.execute(request, response -> {
                int statusCode = response.getCode();
                String body = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity());

                if (statusCode < 200 || statusCode >= 300) {
                    throw new CameraHistoryUnavailableException(
                            "El NVR no autorizó o no pudo procesar la búsqueda."
                    );
                }

                return body;
            });

            return HikvisionNvrXmlCodec.parseSearchResponse(responseBody);
        } catch (CameraHistoryUnavailableException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CameraHistoryUnavailableException(
                    "No fue posible comunicarse con el NVR.",
                    exception
            );
        }
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new CameraHistoryUnavailableException(
                    "El histórico de cámaras no está configurado."
            );
        }
    }

    private URI searchEndpoint() {
        URI baseUrl = properties.getBaseUrl();
        String scheme = baseUrl.getScheme();

        if ((scheme == null
                || !("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)))
                || baseUrl.getHost() == null
                || baseUrl.getUserInfo() != null) {
            throw new CameraHistoryUnavailableException(
                    "La dirección configurada para el NVR no es válida."
            );
        }

        String value = baseUrl.toString();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        return URI.create(value + SEARCH_PATH);
    }

    private int effectivePort(URI endpoint) {
        if (endpoint.getPort() > 0) {
            return endpoint.getPort();
        }

        return "https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80;
    }

    private int normalizedMaxResults() {
        return Math.max(1, Math.min(properties.getMaxResults(), 200));
    }
}
