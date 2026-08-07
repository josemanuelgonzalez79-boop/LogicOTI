package com.icap.logicoti.intrusion;

public enum AlarmMode {
    DISARMED,
    ARMING,
    ARMED,
    ARMED_WITH_BYPASS,
    REJECTED,
    ALARM;

    public boolean isArmed() {
        return this == ARMED
                || this == ARMED_WITH_BYPASS
                || this == ALARM;
    }

    public boolean isAlarmActive() {
        return this == ALARM;
    }
}
