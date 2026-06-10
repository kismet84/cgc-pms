package com.cgcpms.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    private String userId;
    private String username;
    private String realName;
    private String avatar;
    private String phone;
    private String email;
    private List<String> roles;
    private List<String> permissions;
    private String roleName;
}
