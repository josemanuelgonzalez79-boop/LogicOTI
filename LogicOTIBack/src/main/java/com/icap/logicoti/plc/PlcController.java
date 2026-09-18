package com.icap.logicoti.plc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/plc")
public class PlcController {

    private final PlcService plcService;

    public PlcController(PlcService plcService) {
        this.plcService = plcService;
    }

    @GetMapping("/status")
    public PlcConnectionResponse status() {
        return plcService.getConnectionStatus();
    }

    @GetMapping("/test")
    public PlcTestResponse test() {
        return plcService.readTestTags();
    }

    @PutMapping("/test/light")
    public PlcTestResponse writeLightCommand(
            @Valid @RequestBody LightCommandRequest request
    ) {
        return plcService.writeLightCommand(request.on());
    }

}