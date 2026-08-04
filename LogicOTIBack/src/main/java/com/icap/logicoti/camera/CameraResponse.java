package com.icap.logicoti.camera;

public record CameraResponse(
        Long id,
        String code,
        int channelNumber,
        String name,
        String floorCode,
        String areaCode,
        String sourceName,
        String streamKey,
        boolean active,
        boolean videoAvailable,
        String viewUrl
) {
}
