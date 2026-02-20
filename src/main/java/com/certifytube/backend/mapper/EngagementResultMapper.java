package com.certifytube.backend.mapper;

import com.certifytube.backend.dto.SessionAnalyzeResponse;
import com.certifytube.backend.model.EngagementResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EngagementResultMapper {

    @Mapping(source = "modelUsed", target = "model")
    @Mapping(target = "topPositive", ignore = true)
    @Mapping(target = "topNegative", ignore = true)
    SessionAnalyzeResponse toResponse(EngagementResult result);
}
