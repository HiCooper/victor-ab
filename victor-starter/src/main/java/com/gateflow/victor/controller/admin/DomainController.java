package com.gateflow.victor.controller.admin;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.entity.Domain;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.domain.DomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 域管理 API
 */
@RestController
@RequestMapping("/api/v1/admin/domains")
@RequiredArgsConstructor
@Tag(name = "Domain API", description = "域管理接口")
public class DomainController {

    private final DomainService domainService;

    @PostMapping
    @Operation(summary = "创建域", description = "创建新的流量域")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Domain> createDomain(@RequestBody Domain domain) {
        Domain created = domainService.createDomain(domain);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询域详情", description = "根据ID查询域信息")
    public ResponseEntity<Domain> getDomain(
            @Parameter(description = "域ID") @PathVariable Long id) {
        Domain domain = domainService.getDomainById(id);
        if (domain == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(domain);
    }

    @GetMapping("/key/{domainKey}")
    @Operation(summary = "根据业务标识查询域", description = "根据domainId查询域信息")
    public ResponseEntity<Domain> getDomainByKey(
            @Parameter(description = "域业务标识") @PathVariable String domainKey) {
        Domain domain = domainService.getDomainByKey(domainKey);
        if (domain == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(domain);
    }

    @GetMapping
    @Operation(summary = "查询所有域", description = "查询所有流量域列表")
    public ResponseEntity<List<Domain>> listAllDomains() {
        List<Domain> domains = domainService.listDomains();
        return ResponseEntity.ok(domains);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新域", description = "更新域信息")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Domain> updateDomain(
            @Parameter(description = "域ID") @PathVariable Long id,
            @RequestBody Domain domain) {
        Domain updated = domainService.updateDomain(id, domain);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除域", description = "删除没有层的域")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Void> deleteDomain(
            @Parameter(description = "域ID") @PathVariable Long id) {
        domainService.deleteDomain(id);
        return ResponseEntity.noContent().build();
    }
}
