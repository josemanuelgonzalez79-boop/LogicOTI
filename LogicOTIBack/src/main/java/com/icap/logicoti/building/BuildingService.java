package com.icap.logicoti.building;

import com.icap.logicoti.building.BuildingResponse.AreaResponse;
import com.icap.logicoti.building.BuildingResponse.FloorResponse;
import com.icap.logicoti.building.BuildingResponse.InventoryResponse;
import com.icap.logicoti.building.BuildingResponse.TotalsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class BuildingService {

    private static final String BUILDING_QUERY = """
            SELECT
                floor.id AS floor_id,
                floor.code AS floor_code,
                floor.name AS floor_name,
                floor.display_order AS floor_order,

                area.id AS area_id,
                area.code AS area_code,
                area.name AS area_name,
                area.area_type,
                area.display_order AS area_order,

                COALESCE(inventory.lamps, 0) AS lamps,
                COALESCE(inventory.motion_sensors, 0) AS motion_sensors,
                COALESCE(inventory.door_sensors, 0) AS door_sensors,
                COALESCE(inventory.smoke_sensors, 0) AS smoke_sensors,
                COALESCE(inventory.outlets, 0) AS outlets,
                COALESCE(inventory.switches, 0) AS switches,
                COALESCE(inventory.minisplits, 0) AS minisplits

            FROM building_floor floor

            INNER JOIN building_area area
                ON area.floor_id = floor.id

            LEFT JOIN area_inventory inventory
                ON inventory.area_id = area.id

            WHERE floor.active = TRUE
              AND area.active = TRUE

            ORDER BY
                floor.display_order,
                area.display_order
            """;

    private final JdbcTemplate jdbcTemplate;

    public BuildingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BuildingResponse getBuilding() {

        List<BuildingRow> rows = jdbcTemplate.query(
                BUILDING_QUERY,
                (resultSet, rowNumber) -> new BuildingRow(
                        resultSet.getLong("floor_id"),
                        resultSet.getString("floor_code"),
                        resultSet.getString("floor_name"),
                        resultSet.getInt("floor_order"),

                        resultSet.getLong("area_id"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name"),
                        resultSet.getString("area_type"),
                        resultSet.getInt("area_order"),

                        resultSet.getInt("lamps"),
                        resultSet.getInt("motion_sensors"),
                        resultSet.getInt("door_sensors"),
                        resultSet.getInt("smoke_sensors"),
                        resultSet.getInt("outlets"),
                        resultSet.getInt("switches"),
                        resultSet.getInt("minisplits")
                )
        );

        Map<Long, FloorAccumulator> floors = new LinkedHashMap<>();

        int totalLamps = 0;
        int totalMotionSensors = 0;
        int totalDoorSensors = 0;
        int totalSmokeSensors = 0;
        int totalOutlets = 0;
        int totalSwitches = 0;
        int totalMinisplits = 0;

        for (BuildingRow row : rows) {

            FloorAccumulator floor = floors.computeIfAbsent(
                    row.floorId(),
                    floorId -> new FloorAccumulator(
                            row.floorId(),
                            row.floorCode(),
                            row.floorName(),
                            row.floorOrder()
                    )
            );

            InventoryResponse inventory = new InventoryResponse(
                    row.lamps(),
                    row.motionSensors(),
                    row.doorSensors(),
                    row.smokeSensors(),
                    row.outlets(),
                    row.switches(),
                    row.minisplits()
            );

            floor.areas.add(
                    new AreaResponse(
                            row.areaId(),
                            row.areaCode(),
                            row.areaName(),
                            row.areaType(),
                            row.areaOrder(),
                            inventory
                    )
            );

            totalLamps += row.lamps();
            totalMotionSensors += row.motionSensors();
            totalDoorSensors += row.doorSensors();
            totalSmokeSensors += row.smokeSensors();
            totalOutlets += row.outlets();
            totalSwitches += row.switches();
            totalMinisplits += row.minisplits();
        }

        List<FloorResponse> floorResponses = floors.values()
                .stream()
                .map(FloorAccumulator::toResponse)
                .toList();

        TotalsResponse totals = new TotalsResponse(
                floorResponses.size(),
                rows.size(),
                totalLamps,
                totalMotionSensors,
                totalDoorSensors,
                totalSmokeSensors,
                totalOutlets,
                totalSwitches,
                totalMinisplits
        );

        return new BuildingResponse(
                "OTI",
                "Edificio OTI",
                floorResponses,
                totals
        );
    }

    private record BuildingRow(
            Long floorId,
            String floorCode,
            String floorName,
            int floorOrder,

            Long areaId,
            String areaCode,
            String areaName,
            String areaType,
            int areaOrder,

            int lamps,
            int motionSensors,
            int doorSensors,
            int smokeSensors,
            int outlets,
            int switches,
            int minisplits
    ) {
    }

    private static final class FloorAccumulator {

        private final Long id;
        private final String code;
        private final String name;
        private final int displayOrder;
        private final List<AreaResponse> areas = new ArrayList<>();

        private FloorAccumulator(
                Long id,
                String code,
                String name,
                int displayOrder
        ) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.displayOrder = displayOrder;
        }

        private FloorResponse toResponse() {
            return new FloorResponse(
                    id,
                    code,
                    name,
                    displayOrder,
                    List.copyOf(areas)
            );
        }
    }
}