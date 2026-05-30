package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RBAC 角色列表响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RbacRolesResponse {

    private List<Map<String, String>> roles;
}
