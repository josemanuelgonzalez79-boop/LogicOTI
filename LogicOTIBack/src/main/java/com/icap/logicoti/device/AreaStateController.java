package com.icap.logicoti.device;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/areas")
public class AreaStateController {

    private final AreaStateService areaStateService;
    

    public AreaStateController(
            AreaStateService areaStateService
    ) {
        this.areaStateService = areaStateService;
    }

    @GetMapping("/{areaCode}/state")
    public AreaStateResponse getAreaState(
            @PathVariable String areaCode
    ) {
        return areaStateService.getAreaState(areaCode);
    }
}