package com.icap.logicoti.device;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceCommandController {

    private final AreaStateService areaStateService;

    public DeviceCommandController(AreaStateService areaStateService) {
        this.areaStateService = areaStateService;
    }

    @PutMapping("/{deviceCode}/command")
    public AreaStateResponse command(
        @PathVariable String deviceCode,
        @Valid @RequestBody DeviceCommandRequest request
    ) {
        return areaStateService.commandDevice(deviceCode, request.on());
    }
}