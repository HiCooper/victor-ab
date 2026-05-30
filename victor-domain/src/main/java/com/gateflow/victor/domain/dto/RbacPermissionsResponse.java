package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RBAC 权限查询响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RbacPermissionsResponse {

    private Long userId;
    private List<String> permissions;
    private boolean isAdmin;
}
