package com.icap.logicoti.report;

import jakarta.validation.constraints.AssertTrue;

public record HistoryRetentionRunRequest(
        @AssertTrue(message = "Confirma explícitamente la limpieza de históricos vencidos.")
        boolean confirmed
) {
}

