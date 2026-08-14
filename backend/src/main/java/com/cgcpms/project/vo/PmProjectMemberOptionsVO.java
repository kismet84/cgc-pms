package com.cgcpms.project.vo;

import java.util.List;

public record PmProjectMemberOptionsVO(
        List<RoleOption> roles,
        List<UserOption> users,
        boolean usersTruncated) {

    public record RoleOption(String roleCode, String roleName) {
    }

    public record UserOption(String userId, String username, String realName, List<String> roleCodes) {
    }
}
