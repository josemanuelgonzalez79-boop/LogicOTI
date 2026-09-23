package com.icap.logicoti.camera;

import java.time.LocalDateTime;
import java.util.List;

public interface NvrMotionClient {
    boolean enabled();
    List<NvrMotionInterval> search(int channel, LocalDateTime start, LocalDateTime end);
}
