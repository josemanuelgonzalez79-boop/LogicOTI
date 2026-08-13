package com.icap.logicoti.device;

import com.icap.logicoti.signal.SignalQuality;

import java.time.Instant;
import java.util.List;

public record AreaStateResponse(
        String areaCode,
        String areaName,
        boolean plcEnabled,
        boolean connected,
        List<DeviceStateResponse> devices,
        String message,
        Instant timestamp
) {

    public record DeviceStateResponse(
            Long id,
            String code,
            String name,
            String type,
            int number,
            boolean controllable,
            Boolean command,
            Boolean state,
            Boolean fault,
            SignalQuality quality,
            Instant lastUpdatedAt,
            String qualityDetail
    ) {
    }
}
