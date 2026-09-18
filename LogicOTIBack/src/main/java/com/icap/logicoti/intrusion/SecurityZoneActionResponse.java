package com.icap.logicoti.intrusion;

public record SecurityZoneActionResponse(
        SecurityZoneListResponse status,
        SecurityPrecheckResponse precheck
) {
}
