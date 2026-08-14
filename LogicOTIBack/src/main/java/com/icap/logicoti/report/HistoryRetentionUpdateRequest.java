package com.icap.logicoti.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HistoryRetentionUpdateRequest(
        @NotNull(message = "Indica si la retención automática estará habilitada.")
        Boolean enabled,

        @NotNull(message = "Indica cuántos meses de históricos se conservarán.")
        @Min(value = 6, message = "La retención mínima es de 6 meses.")
        @Max(value = 120, message = "La retención máxima es de 120 meses.")
        Integer retentionMonths
) {
}

