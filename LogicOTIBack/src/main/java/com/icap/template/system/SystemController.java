package com.icap.template.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatusService service;

    public SystemController(SystemStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return service.getStatus();
    }
}
