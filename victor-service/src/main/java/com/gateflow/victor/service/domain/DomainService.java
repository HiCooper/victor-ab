package com.gateflow.victor.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Domain;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.DomainMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 域管理服务
 */
@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainMapper domainMapper;
    private final LayerMapper layerMapper;

    /**
     * 创建域
     */
    @Transactional(rollbackFor = Exception.class)
    public Domain createDomain(Domain domain) {
        if (domain.getTrafficRatio() == null) {
            domain.setTrafficRatio(BigDecimal.ONE);
        }
        if (domain.getIsExclusive() == null) {
            domain.setIsExclusive(false);
        }
        domainMapper.insert(domain);
        return domain;
    }

    /**
     * 根据ID查询域
     */
    public Domain getDomainById(Long id) {
        return domainMapper.selectById(id);
    }

    /**
     * 根据业务标识查询域
     */
    public Domain getDomainByKey(String domainKey) {
        LambdaQueryWrapper<Domain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Domain::getDomainId, domainKey);
        return domainMapper.selectOne(wrapper);
    }

    /**
     * 更新域
     */
    @Transactional(rollbackFor = Exception.class)
    public Domain updateDomain(Long id, Domain domain) {
        Domain existing = domainMapper.selectById(id);
        if (existing == null) {
            throw new VictorException(ErrorCode.DOMAIN_NOT_FOUND, String.valueOf(id));
        }
        domain.setId(id);
        domain.setDomainId(existing.getDomainId()); // 不允许修改业务标识
        domainMapper.updateById(domain);
        return domainMapper.selectById(id);
    }

    /**
     * 删除域（没有层的域才能删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDomain(Long id) {
        Domain domain = domainMapper.selectById(id);
        if (domain == null) {
            throw new VictorException(ErrorCode.DOMAIN_NOT_FOUND, String.valueOf(id));
        }
        // 检查是否有层
        LambdaQueryWrapper<Layer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Layer::getDomainId, id);
        long layerCount = layerMapper.selectCount(wrapper);

        if (layerCount > 0) {
            throw new VictorException(ErrorCode.DOMAIN_HAS_LAYERS);
        }

        domainMapper.deleteById(id);
    }

    /**
     * 查询所有域
     */
    public List<Domain> listDomains() {
        return domainMapper.selectList(null);
    }
}
