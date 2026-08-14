package com.icap.logicoti.report;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history/retention")
public class HistoryRetentionController {

    private final HistoryRetentionService service;

    public HistoryRetentionController(HistoryRetentionService service) {
        this.service = service;
    }

    @GetMapping
    public HistoryRetentionResponse get() {
        return service.getPolicy();
    }

    @PutMapping
    public HistoryRetentionResponse update(
            @Valid @RequestBody HistoryRetentionUpdateRequest request,
            Authentication authentication
    ) {
        return service.updatePolicy(request, authentication.getName());
    }

    @PostMapping("/run")
    public HistoryRetentionRunResponse run(
            @Valid @RequestBody HistoryRetentionRunRequest request,
            Authentication authentication
    ) {
        return service.runManually(authentication.getName());
    }
}

