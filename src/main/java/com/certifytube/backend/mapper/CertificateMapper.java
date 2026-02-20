package com.certifytube.backend.mapper;

import com.certifytube.backend.dto.CertificateResponse;
import com.certifytube.backend.model.Certificate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CertificateMapper {

    @Mapping(target = "verificationLink", ignore = true)
    @Mapping(source = "createdAtUtc", target = "createdAtUtc", dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    CertificateResponse toResponse(Certificate certificate);

    default String mapInstant(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
