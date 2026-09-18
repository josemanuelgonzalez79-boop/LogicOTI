package com.icap.logicoti.intrusion;

public record SecurityActionResponse(
        SecurityStatusResponse status,
        SecurityPrecheckResponse precheck
) {
}
