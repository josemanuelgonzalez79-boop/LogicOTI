package com.icap.logicoti.audit;

import java.util.List;

public record DeviceCommandHistoryPageResponse(
        List<DeviceCommandHistoryResponse> items,
        long total,
        int limit,
        int offset
) {
}