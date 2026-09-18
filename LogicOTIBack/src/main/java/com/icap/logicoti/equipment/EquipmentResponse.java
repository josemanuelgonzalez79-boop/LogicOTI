package com.icap.logicoti.equipment;

import java.time.Instant;

public record EquipmentResponse(
        Long id,
        String code,
        String name,
        boolean active,
        Instant createdAt
) {
    static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                equipment.isActive(),
                equipment.getCreatedAt()
        );
    }
}
