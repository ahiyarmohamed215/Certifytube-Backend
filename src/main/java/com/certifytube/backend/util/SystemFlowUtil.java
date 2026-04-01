package com.certifytube.backend.util;

import com.certifytube.backend.dto.SystemFlowDto;
import com.certifytube.backend.dto.SystemFlowStepDto;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SystemFlowUtil {

    private SystemFlowUtil() {
    }

    public static SystemFlowDto flow(String feature, List<SystemFlowStepDto> steps) {
        return SystemFlowDto.builder()
                .feature(feature)
                .requestId(MDC.get("rid"))
                .steps(steps == null ? List.of() : new ArrayList<>(steps))
                .build();
    }

    public static SystemFlowStepDto step(String step, String status, String message, Map<String, Object> data) {
        return SystemFlowStepDto.builder()
                .step(step)
                .status(status)
                .message(message)
                .data(data == null ? Map.of() : data)
                .build();
    }

    public static Map<String, Object> data(Object... keyValues) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (keyValues == null) {
            return out;
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("System flow data requires key/value pairs");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            Object rawKey = keyValues[i];
            String key = rawKey == null ? "" : rawKey.toString();
            out.put(key, keyValues[i + 1]);
        }
        return out;
    }
}
