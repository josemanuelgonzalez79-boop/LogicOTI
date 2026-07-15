package com.icap.template.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EquipmentRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 150) String name,
        boolean active
) {
}
