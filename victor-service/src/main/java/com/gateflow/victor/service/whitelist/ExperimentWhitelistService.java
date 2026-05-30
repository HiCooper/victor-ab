package com.gateflow.victor.service.whitelist;

import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.ExperimentWhitelist;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.ExperimentWhitelistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实验白名单服务
 */
@Service
@RequiredArgsConstructor
public class ExperimentWhitelistService {

    private final ExperimentWhitelistMapper whitelistMapper;
    private final ExperimentMapper experimentMapper;
    private final BucketMapper bucketMapper;

    /**
     * 添加白名单用户（指定分桶）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExperimentWhitelist addUsers(String expId, String bucketId, String userIds) {
        // 验证实验是否存在
        Experiment experiment = experimentMapper.selectByExpId(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, expId);
        }

        // 验证分桶是否存在
        List<Bucket> buckets = bucketMapper.selectActiveBuckets(expId);
        boolean bucketExists = buckets.stream()
                .anyMatch(v -> v.getBucketId().equals(bucketId));
        if (!bucketExists) {
            throw new VictorException(ErrorCode.VER_NOT_FOUND, bucketId);
        }

        // 解析用户列表
        List<String> userIdList = Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (userIdList.isEmpty()) {
            throw new VictorException(ErrorCode.SYS_PARAM_INVALID, "userIds不能为空");
        }

        // 检查是否已存在该实验+分桶的白名单
        List<ExperimentWhitelist> existing = whitelistMapper.selectByExpIdAndBucketId(expId, bucketId);
        if (!existing.isEmpty()) {
            // 追加用户到现有记录
            ExperimentWhitelist whitelist = existing.get(0);
            String existingUsers = whitelist.getUserIds();
            String newUsers = existingUsers + "," + userIds;
            whitelist.setUserIds(newUsers);
            whitelistMapper.updateById(whitelist);
            return whitelist;
        }

        // 创建新白名单记录
        ExperimentWhitelist whitelist = new ExperimentWhitelist();
        whitelist.setExpId(expId);
        whitelist.setBucketId(bucketId);
        whitelist.setUserIds(userIds);
        whitelistMapper.insert(whitelist);
        return whitelist;
    }

    /**
     * 移除白名单用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUsers(String expId, String bucketId, String userIds) {
        List<ExperimentWhitelist> records = whitelistMapper.selectByExpIdAndBucketId(expId, bucketId);
        if (records.isEmpty()) {
            return;
        }

        List<String> removeUsers = Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        ExperimentWhitelist whitelist = records.get(0);
        List<String> currentUsers = Arrays.stream(whitelist.getUserIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 移除指定用户
        currentUsers.removeAll(removeUsers);

        if (currentUsers.isEmpty()) {
            whitelistMapper.deleteById(whitelist.getId());
        } else {
            whitelist.setUserIds(String.join(",", currentUsers));
            whitelistMapper.updateById(whitelist);
        }
    }

    /**
     * 获取实验白名单（所有分桶）
     */
    public List<ExperimentWhitelist> getWhitelist(String expId) {
        return whitelistMapper.selectByExpId(expId);
    }

    /**
     * 获取实验指定分桶的白名单
     */
    public List<ExperimentWhitelist> getWhitelistByBucket(String expId, String bucketId) {
        return whitelistMapper.selectByExpIdAndBucketId(expId, bucketId);
    }

    /**
     * 检查用户是否在白名单中
     */
    public boolean isUserInWhitelist(String expId, String userId) {
        List<ExperimentWhitelist> records = whitelistMapper.selectByExpId(expId);
        if (records.isEmpty()) {
            return false;
        }

        for (ExperimentWhitelist record : records) {
            String[] userIdArray = record.getUserIds().split(",");
            for (String uid : userIdArray) {
                if (uid.trim().equals(userId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取用户在白名单中所在的分桶ID
     */
    public String getBucketIdForWhitelistedUser(String expId, String userId) {
        List<ExperimentWhitelist> records = whitelistMapper.selectByExpId(expId);
        if (records.isEmpty()) {
            return null;
        }

        for (ExperimentWhitelist record : records) {
            String[] userIdArray = record.getUserIds().split(",");
            for (String uid : userIdArray) {
                if (uid.trim().equals(userId)) {
                    return record.getBucketId();
                }
            }
        }
        return null;
    }
}
