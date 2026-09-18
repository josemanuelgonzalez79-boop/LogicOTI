package com.icap.logicoti.intrusion;

import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada compatible con el contrato de seguridad anterior.
 *
 * El estado real se administra por zona. Los endpoints heredados arman,
 * desarman y consultan las cuatro zonas para no romper clientes existentes.
 */
@Service
public class IntrusionAlarmService {

    private final SecurityZoneService zoneService;

    public IntrusionAlarmService(SecurityZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @Transactional(readOnly = true)
    public SecurityStatusResponse getStatus() {
        return zoneService.getAggregateStatus();
    }

    @Transactional(readOnly = true)
    public SecurityPrecheckResponse precheck() {
        return zoneService.precheck(zoneService.allZoneCodes());
    }

    @Transactional
    public SecurityStatusResponse activateFromMotion(
            SensorEventHistoryResponse event
    ) {
        return zoneService.activateFromMotion(event);
    }

    @Transactional
    public SecurityActionResponse armManually(String username) {
        SecurityZoneActionResponse response = zoneService.armManually(
                zoneService.allZoneCodes(),
                username
        );

        return new SecurityActionResponse(
                zoneService.getAggregateStatus(),
                response.precheck()
        );
    }

    @Transactional
    public SecurityActionResponse disarmManually(String username) {
        zoneService.disarmManually(
                zoneService.allZoneCodes(),
                username
        );

        return new SecurityActionResponse(
                zoneService.getAggregateStatus(),
                null
        );
    }

    @Transactional
    public void evaluateAutomaticSchedule() {
        zoneService.evaluateAutomaticSchedule();
    }

    @Transactional
    public void completeArmingIfDue() {
        zoneService.completeArmingIfDue();
    }
}
