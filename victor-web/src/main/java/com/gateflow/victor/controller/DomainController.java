package com.gateflow.victor.controller;

import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Domain;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.DomainMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
@Tag(name = "Domain API", description = "域管理接口")
public class DomainController {

    private final DomainMapper domainMapper;
    private final LayerMapper layerMapper;

    @PostMapping
    @Operation(summary = "创建域", description = "创建新的流量域")
    public ResponseEntity<Domain> createDomain(@RequestBody Domain domain) {
        if (domain.getTrafficRatio() == null) {
            domain.setTrafficRatio(java.math.BigDecimal.ONE);
        }
        if (domain.getIsExclusive() == null) {
            domain.setIsExclusive(false);
        }
        domainMapper.insert(domain);
        return ResponseEntity.ok(domain);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询域详情", description = "根据ID查询域信息")
    public ResponseEntity<Domain> getDomain(
            @Parameter(description = "域ID") @PathVariable Long id) {
        Domain domain = domainMapper.selectById(id);
        if (domain == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(domain);
    }

    @GetMapping("/key/{domainKey}")
    @Operation(summary = "根据业务标识查询域", description = "根据domainId查询域信息")
    public ResponseEntity<Domain> getDomainByKey(
            @Parameter(description = "域业务标识") @PathVariable String domainKey) {
        LambdaQueryWrapper<Domain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Domain::getDomainId, domainKey);
        Domain domain = domainMapper.selectOne(wrapper);
        if (domain == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(domain);
    }

    @GetMapping
    @Operation(summary = "查询所有域", description = "查询所有流量域列表")
    public ResponseEntity<List<Domain>> listAllDomains() {
        List<Domain> domains = domainMapper.selectList(null);
        return ResponseEntity.ok(domains);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新域", description = "更新域信息")
    public ResponseEntity<Domain> updateDomain(
            @Parameter(description = "域ID") @PathVariable Long id,
            @RequestBody Domain domain) {
        Domain existing = domainMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        domain.setId(id);
        domain.setDomainId(existing.getDomainId()); // 不允许修改业务标识
        domainMapper.updateById(domain);
        return ResponseEntity.ok(domainMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除域", description = "删除没有层的域")
    public ResponseEntity<Void> deleteDomain(
            @Parameter(description = "域ID") @PathVariable Long id) {
        Domain domain = domainMapper.selectById(id);
        if (domain == null) {
            return ResponseEntity.notFound().build();
        }
        // 检查是否有层
        LambdaQueryWrapper<Layer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Layer::getDomainId, id);
        long layerCount = layerMapper.selectCount(wrapper);
        
        if (layerCount > 0) {
            throw new VictorException(ErrorCode.DOMAIN_HAS_LAYERS);
        }
        
        domainMapper.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
