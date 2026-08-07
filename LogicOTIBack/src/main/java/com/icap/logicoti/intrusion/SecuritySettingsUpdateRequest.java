package com.icap.logicoti.intrusion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public record SecuritySettingsUpdateRequest(
        boolean automaticScheduleEnabled,

        @NotBlank(message = "La zona horaria es obligatoria.")
        String timezone,

        @Min(value = 0, message = "El tiempo de salida no puede ser negativo.")
        @Max(value = 600, message = "El tiempo de salida no puede superar 600 segundos.")
        int exitDelaySeconds,

        @Min(value = 1, message = "El apagado de luces debe ser de al menos 1 minuto.")
        @Max(value = 1440, message = "El apagado de luces no puede superar 1440 minutos.")
        int lightInactivityMinutes,

        @Min(value = 1, message = "El apagado de minisplits debe ser de al menos 1 minuto.")
        @Max(value = 1440, message = "El apagado de minisplits no puede superar 1440 minutos.")
        int minisplitInactivityMinutes,

        @Min(value = 30, message = "El diagnóstico debe durar al menos 30 segundos.")
        @Max(value = 600, message = "El diagnóstico no puede superar 600 segundos.")
        int diagnosticTimeoutSeconds,

        @Min(value = 1, message = "La vigencia del diagnóstico debe ser de al menos 1 mes.")
        @Max(value = 24, message = "La vigencia del diagnóstico no puede superar 24 meses.")
        int diagnosticValidityMonths,

        @NotNull(message = "Los horarios por día son obligatorios.")
        @Size(min = 7, max = 7, message = "Se deben enviar los 7 días de la semana.")
        List<@Valid ScheduleDay> days
) {

    public record ScheduleDay(
            @Min(value = 1, message = "El día debe estar entre 1 y 7.")
            @Max(value = 7, message = "El día debe estar entre 1 y 7.")
            int dayOfWeek,

            boolean enabled,
            boolean allDayArmed,

            @NotNull(message = "La hora de armado es obligatoria.")
            LocalTime armTime,

            @NotNull(message = "La hora de desarmado es obligatoria.")
            LocalTime disarmTime
    ) {
    }
}
