package com.icap.logicoti.event;

public record SensorDefinition(
        Long id,
        String code,
        String name,
        String areaCode,
        String areaName,
        String type,
        String stateTag
) {
}