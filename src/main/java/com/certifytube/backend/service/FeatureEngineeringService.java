package com.certifytube.backend.service;

import java.util.Map;

public interface FeatureEngineeringService {
    Map<String, Object> computeFeaturesForSession(String sessionId);
}
