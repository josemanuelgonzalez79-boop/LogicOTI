package com.icap.logicoti.camera;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HikvisionNvrXmlCodecTests {

    @Test
    void buildsTheFirmwareSpecificLocalTimeSearch() {
        String xml = HikvisionNvrXmlCodec.buildSearchRequest(
                101,
                LocalDateTime.of(2026, 9, 22, 9, 30),
                LocalDateTime.of(2026, 9, 22, 9, 45),
                20
        );

        assertThat(xml)
                .contains("<trackID>101</trackID>")
                .contains("<startTime>2026-09-22T09:30:00Z</startTime>")
                .contains("<endTime>2026-09-22T09:45:00Z</endTime>")
                .contains(
                        "<metadataDescriptor>/recordType.meta.std-cgi.com</metadataDescriptor>"
                )
                .doesNotContain("//recordType.meta.std-cgi.com");
    }

    @Test
    void parsesTheRealNvrSearchResponseWithoutExposingCredentials() {
        List<NvrRecordingSegment> segments =
                HikvisionNvrXmlCodec.parseSearchResponse("""
                        <?xml version="1.0" encoding="UTF-8" ?>
                        <CMSearchResult version="1.0"
                         xmlns="http://www.hikvision.com/ver20/XMLSchema">
                          <responseStatus>true</responseStatus>
                          <responseStatusStrg>OK</responseStatusStrg>
                          <numOfMatches>1</numOfMatches>
                          <matchList>
                            <searchMatchItem>
                              <trackID>101</trackID>
                              <timeSpan>
                                <startTime>2026-09-22T09:07:21Z</startTime>
                                <endTime>2026-09-22T09:44:09Z</endTime>
                              </timeSpan>
                              <mediaSegmentDescriptor>
                                <contentType>video</contentType>
                                <codecType>H.264-BP</codecType>
                                <playbackURI>rtsp://192.0.2.18/Streaming/tracks/101/?starttime=20260922T090721Z&amp;endtime=20260922T094409Z</playbackURI>
                              </mediaSegmentDescriptor>
                              <metadataMatches>
                                <metadataDescriptor>recordType.meta.hikvision.com/timing</metadataDescriptor>
                              </metadataMatches>
                            </searchMatchItem>
                          </matchList>
                        </CMSearchResult>
                        """);

        assertThat(segments).hasSize(1);
        NvrRecordingSegment segment = segments.getFirst();
        assertThat(segment.startTime()).isEqualTo(
                LocalDateTime.of(2026, 9, 22, 9, 7, 21)
        );
        assertThat(segment.endTime()).isEqualTo(
                LocalDateTime.of(2026, 9, 22, 9, 44, 9)
        );
        assertThat(segment.codecType()).isEqualTo("H.264-BP");
        assertThat(segment.recordingType()).isEqualTo("timing");
    }

    @Test
    void returnsAnEmptyListWhenTheNvrHasNoMatches() {
        List<NvrRecordingSegment> segments =
                HikvisionNvrXmlCodec.parseSearchResponse("""
                        <?xml version="1.0" encoding="UTF-8" ?>
                        <CMSearchResult version="1.0"
                         xmlns="http://www.hikvision.com/ver20/XMLSchema">
                          <responseStatus>true</responseStatus>
                          <responseStatusStrg>NO MATCHES</responseStatusStrg>
                          <numOfMatches>0</numOfMatches>
                        </CMSearchResult>
                        """);

        assertThat(segments).isEmpty();
    }

    @Test
    void refusesXmlWithADoctype() {
        assertThrows(
                CameraHistoryUnavailableException.class,
                () -> HikvisionNvrXmlCodec.parseSearchResponse("""
                        <?xml version="1.0"?>
                        <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                        <CMSearchResult>
                          <responseStatusStrg>&xxe;</responseStatusStrg>
                        </CMSearchResult>
                        """)
        );
    }
}
