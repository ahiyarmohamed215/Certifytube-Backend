package com.certifytube.backend.mapper;

import com.certifytube.backend.dto.AuthMeResponse;
import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.model.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "token", ignore = true)
    @Mapping(target = "tokenType", ignore = true)
    AuthResponse toAuthResponse(UserAccount user);

    @Mapping(source = "id", target = "userId")
    AuthMeResponse toAuthMeResponse(UserAccount user);

    default String mapRole(com.certifytube.backend.model.Role role) {
        return role == null ? null : role.name();
    }
}
