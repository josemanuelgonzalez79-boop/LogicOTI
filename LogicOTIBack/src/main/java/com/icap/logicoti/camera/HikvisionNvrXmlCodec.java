package com.icap.logicoti.camera;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class HikvisionNvrXmlCodec {

    private static final DateTimeFormatter NVR_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    Locale.ROOT
            );

    private HikvisionNvrXmlCodec() {
    }

    static String buildSearchRequest(
            int trackId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int maxResults
    ) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <CMSearchDescription version="1.0"
                 xmlns="http://www.hikvision.com/ver20/XMLSchema">
                  <searchID>{%s}</searchID>
                  <trackList>
                    <trackID>%d</trackID>
                  </trackList>
                  <timeSpanList>
                    <timeSpan>
                      <startTime>%s</startTime>
                      <endTime>%s</endTime>
                    </timeSpan>
                  </timeSpanList>
                  <maxResults>%d</maxResults>
                  <searchResultPostion>0</searchResultPostion>
                  <metadataList>
                    <metadataDescriptor>/recordType.meta.std-cgi.com</metadataDescriptor>
                  </metadataList>
                </CMSearchDescription>
                """.formatted(
                UUID.randomUUID(),
                trackId,
                NVR_TIME_FORMAT.format(startTime),
                NVR_TIME_FORMAT.format(endTime),
                maxResults
        );
    }

    static List<NvrRecordingSegment> parseSearchResponse(String xml) {
        Document document = parseDocument(xml);
        String responseStatus = text(document, "responseStatusStrg");

        if ("NO MATCHES".equalsIgnoreCase(responseStatus)) {
            return List.of();
        }

        if (!"OK".equalsIgnoreCase(responseStatus)
                && !"MORE".equalsIgnoreCase(responseStatus)) {
            throw new CameraHistoryUnavailableException(
                    "El NVR rechazó la búsqueda de grabaciones."
            );
        }

        NodeList matches = document.getElementsByTagNameNS(
                "*",
                "searchMatchItem"
        );
        List<NvrRecordingSegment> segments = new ArrayList<>();

        for (int index = 0; index < matches.getLength(); index++) {
            Element match = (Element) matches.item(index);
            Element timeSpan = firstElement(match, "timeSpan");
            Element descriptor = firstElement(
                    match,
                    "mediaSegmentDescriptor"
            );

            if (timeSpan == null || descriptor == null) {
                continue;
            }

            String playbackUri = text(descriptor, "playbackURI");

            if (playbackUri.isBlank()) {
                continue;
            }

            segments.add(new NvrRecordingSegment(
                    parseNvrTime(text(timeSpan, "startTime")),
                    parseNvrTime(text(timeSpan, "endTime")),
                    text(descriptor, "codecType"),
                    recordingType(match),
                    playbackUri
            ));
        }

        return List.copyOf(segments);
    }

    private static Document parseDocument(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new CameraHistoryUnavailableException(
                    "El NVR devolvió una respuesta vacía."
            );
        }

        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            return factory.newDocumentBuilder().parse(
                    new InputSource(new StringReader(xml))
            );
        } catch (Exception exception) {
            throw new CameraHistoryUnavailableException(
                    "El NVR devolvió una respuesta inválida.",
                    exception
            );
        }
    }

    private static LocalDateTime parseNvrTime(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            throw new CameraHistoryUnavailableException(
                    "El NVR devolvió una fecha de grabación inválida.",
                    exception
            );
        }
    }

    private static String recordingType(Element match) {
        Element metadataMatches = firstElement(
                match,
                "metadataMatches"
        );

        if (metadataMatches == null) {
            return "UNKNOWN";
        }

        String descriptor = text(
                metadataMatches,
                "metadataDescriptor"
        );
        int separator = descriptor.lastIndexOf('/');

        return separator >= 0
                ? descriptor.substring(separator + 1)
                : descriptor;
    }

    private static Element firstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String text(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0
                ? ""
                : normalize(nodes.item(0));
    }

    private static String text(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0
                ? ""
                : normalize(nodes.item(0));
    }

    private static String normalize(Node node) {
        String value = node.getTextContent();
        return value == null ? "" : value.trim();
    }
}
