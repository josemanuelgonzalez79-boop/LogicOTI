package com.icap.logicoti.building;

import java.util.List;

public record BuildingResponse(
        String code,
        String name,
        List<FloorResponse> floors,
        TotalsResponse totals
) {

    public record FloorResponse(
            Long id,
            String code,
            String name,
            int displayOrder,
            List<AreaResponse> areas
    ) {
    }

    public record AreaResponse(
            Long id,
            String code,
            String name,
            String type,
            int displayOrder,
            InventoryResponse inventory
    ) {
    }

    public record InventoryResponse(
            int lamps,
            int motionSensors,
            int doorSensors,
            int smokeSensors,
            int outlets,
            int switches,
            int minisplits
    ) {
    }

    public record TotalsResponse(
            int floors,
            int areas,
            int lamps,
            int motionSensors,
            int doorSensors,
            int smokeSensors,
            int outlets,
            int switches,
            int minisplits
    ) {
    }
}