package com.gateflow.victor.service.traffic;

import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.domain.dto.TrafficMapResponse;
import com.gateflow.victor.domain.entity.Domain;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.DomainMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流量地图服务 - 可视化展示域/层/桶的占用情况
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrafficMapService {

    private static final int TOTAL_BUCKETS = 10000;

    private final LayerMapper layerMapper;
    private final DomainMapper domainMapper;
    private final ExperimentMapper experimentMapper;
    private final BucketMapper bucketMapper;

    /**
     * 从实验的分桶中推导实验的桶范围
     * 返回 [minStart, maxEnd]，如果没有分桶则返回 null
     */
    private int[] deriveExperimentBucketRange(Experiment exp) {
        List<Bucket> buckets = bucketMapper.selectActiveBuckets(exp.getExpId());
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }

        int minStart = Integer.MAX_VALUE;
        int maxEnd = Integer.MIN_VALUE;
        for (Bucket v : buckets) {
            if (v.getBucketStart() != null && v.getBucketStart() < minStart) {
                minStart = v.getBucketStart();
            }
            if (v.getBucketEnd() != null && v.getBucketEnd() > maxEnd) {
                maxEnd = v.getBucketEnd();
            }
        }

        if (minStart == Integer.MAX_VALUE || maxEnd == Integer.MIN_VALUE) {
            return null;
        }

        return new int[]{minStart, maxEnd};
    }

    /**
     * 计算实验占用的桶总数（所有分桶桶的并集）
     */
    private int calculateExperimentBucketCount(Experiment exp) {
        List<Bucket> buckets = bucketMapper.selectActiveBuckets(exp.getExpId());
        if (buckets == null || buckets.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Bucket v : buckets) {
            if (v.getBucketStart() != null && v.getBucketEnd() != null) {
                total += (v.getBucketEnd() - v.getBucketStart() + 1);
            }
        }
        return total;
    }

    /**
     * 获取全局流量地图
     */
    public TrafficMapResponse getTrafficMap() {
        TrafficMapResponse response = new TrafficMapResponse();
        response.setTotalBuckets(TOTAL_BUCKETS);

        List<Domain> domains = domainMapper.selectList(null);
        List<TrafficMapResponse.DomainTraffic> domainTraffics = new ArrayList<>();

        for (Domain domain : domains) {
            TrafficMapResponse.DomainTraffic dt = buildDomainTraffic(domain);
            domainTraffics.add(dt);
        }

        response.setDomains(domainTraffics);
        return response;
    }

    /**
     * 获取单层占用详情
     */
    public TrafficMapResponse.LayerDetailResponse getLayerOccupancy(Long layerId) {
        Layer layer = layerMapper.selectById(layerId);
        if (layer == null) {
            throw new IllegalArgumentException("Layer not found: " + layerId);
        }

        TrafficMapResponse.LayerDetailResponse detail = new TrafficMapResponse.LayerDetailResponse();
        detail.setLayerId(layer.getId());
        detail.setLayerKey(layer.getLayerId());
        detail.setLayerName(layer.getName());
        detail.setSalt(layer.getSalt());
        detail.setTotalBuckets(TOTAL_BUCKETS);

        List<Experiment> experiments = experimentMapper.selectByLayerId(layerId);
        List<TrafficMapResponse.ExperimentOccupancy> expOccupancies = new ArrayList<>();
        int usedBuckets = 0;
        Map<Integer, String> bucketMap = new HashMap<>();

        for (Experiment exp : experiments) {
            int[] bucketRange = deriveExperimentBucketRange(exp);
            if (bucketRange == null) {
                continue;
            }

            int bucketCount = calculateExperimentBucketCount(exp);

            TrafficMapResponse.ExperimentOccupancy eo = new TrafficMapResponse.ExperimentOccupancy();
            eo.setExperimentId(exp.getId());
            eo.setExperimentKey(exp.getExpId());
            eo.setExperimentName(exp.getName());
            eo.setStatus(exp.getStatus());
            eo.setBucketStart(bucketRange[0]);
            eo.setBucketEnd(bucketRange[1]);
            eo.setBucketCount(bucketCount);
            eo.setTrafficPercent(bucketCount * 100.0 / TOTAL_BUCKETS);

            List<Bucket> buckets = bucketMapper.selectActiveBuckets(exp.getExpId());
            List<TrafficMapResponse.BucketDetail> bucketDetails = new ArrayList<>();

            for (Bucket v : buckets) {
                TrafficMapResponse.BucketDetail vd = new TrafficMapResponse.BucketDetail();
                vd.setBucketKey(v.getBucketId());
                vd.setBucketName(v.getName());
                vd.setBucketStart(v.getBucketStart());
                vd.setBucketEnd(v.getBucketEnd());
                vd.setBucketCount(v.getBucketEnd() - v.getBucketStart() + 1);
                vd.setTrafficPercent(vd.getBucketCount() * 100.0 / TOTAL_BUCKETS);
                bucketDetails.add(vd);

                // 采样记录 bucketMap (每 100 桶记录一次)
                for (int b = v.getBucketStart(); b <= v.getBucketEnd(); b += 100) {
                    bucketMap.put(b, exp.getExpId() + ":" + v.getBucketId());
                }
            }

            eo.setBuckets(bucketDetails);
            expOccupancies.add(eo);
            usedBuckets += eo.getBucketCount();
        }

        detail.setExperiments(expOccupancies);
        detail.setUsedBuckets(usedBuckets);
        detail.setFreeBuckets(TOTAL_BUCKETS - usedBuckets);
        detail.setUsagePercent(usedBuckets * 100.0 / TOTAL_BUCKETS);
        detail.setBucketMap(bucketMap);

        return detail;
    }

    /**
     * 检测层内桶冲突
     */
    public List<TrafficMapResponse.ConflictWarning> detectLayerConflicts(Long layerId) {
        List<Experiment> experiments = experimentMapper.selectByLayerId(layerId);
        List<TrafficMapResponse.ConflictWarning> conflicts = new ArrayList<>();

        List<Experiment> activeExperiments = experiments.stream()
            .filter(e -> {
                ExperimentStatus s = ExperimentStatus.fromCode(e.getStatus());
                return s == ExperimentStatus.RUNNING;
            })
            .filter(e -> deriveExperimentBucketRange(e) != null)
            .collect(Collectors.toList());

        for (int i = 0; i < activeExperiments.size(); i++) {
            for (int j = i + 1; j < activeExperiments.size(); j++) {
                Experiment exp1 = activeExperiments.get(i);
                Experiment exp2 = activeExperiments.get(j);

                int[] range1 = deriveExperimentBucketRange(exp1);
                int[] range2 = deriveExperimentBucketRange(exp2);

                if (range1[0] <= range2[1] && range1[1] >= range2[0]) {
                    TrafficMapResponse.ConflictWarning conflict = new TrafficMapResponse.ConflictWarning();
                    conflict.setType("overlap");
                    conflict.setMessage(String.format("实验 '%s' 和 '%s' 的桶范围重叠", exp1.getName(), exp2.getName()));
                    conflict.setExperimentId1(exp1.getId());
                    conflict.setExperimentId2(exp2.getId());
                    conflict.setConflictStart(Math.max(range1[0], range2[0]));
                    conflict.setConflictEnd(Math.min(range1[1], range2[1]));
                    conflicts.add(conflict);
                }
            }
        }

        return conflicts;
    }

    private TrafficMapResponse.DomainTraffic buildDomainTraffic(Domain domain) {
        TrafficMapResponse.DomainTraffic dt = new TrafficMapResponse.DomainTraffic();
        dt.setDomainId(domain.getId());
        dt.setDomainName(domain.getName());

        List<Layer> layers = layerMapper.selectByDomainId(domain.getId());
        List<TrafficMapResponse.LayerOccupancy> layerOccupancies = new ArrayList<>();
        int totalDomainBuckets = 0;

        for (Layer layer : layers) {
            TrafficMapResponse.LayerOccupancy lo = buildLayerOccupancy(layer);
            layerOccupancies.add(lo);
            totalDomainBuckets += lo.getTotalBuckets();
        }

        dt.setTotalBuckets(totalDomainBuckets);
        dt.setTrafficPercent(totalDomainBuckets * 100.0 / TOTAL_BUCKETS);
        dt.setLayers(layerOccupancies);

        return dt;
    }

    private TrafficMapResponse.LayerOccupancy buildLayerOccupancy(Layer layer) {
        TrafficMapResponse.LayerOccupancy lo = new TrafficMapResponse.LayerOccupancy();
        lo.setLayerId(layer.getId());
        lo.setLayerKey(layer.getLayerId());
        lo.setLayerName(layer.getName());
        lo.setSalt(layer.getSalt());

        List<Experiment> experiments = experimentMapper.selectByLayerId(layer.getId());
        List<TrafficMapResponse.BucketSegment> segments = new ArrayList<>();
        List<TrafficMapResponse.ConflictWarning> conflicts = new ArrayList<>();
        int usedBuckets = 0;

        for (Experiment exp : experiments) {
            int[] bucketRange = deriveExperimentBucketRange(exp);
            if (bucketRange == null) {
                continue;
            }

            int bucketCount = calculateExperimentBucketCount(exp);

            TrafficMapResponse.BucketSegment segment = new TrafficMapResponse.BucketSegment();
            segment.setBucketStart(bucketRange[0]);
            segment.setBucketEnd(bucketRange[1]);
            segment.setBucketCount(bucketCount);
            segment.setPercent(bucketCount * 100.0 / TOTAL_BUCKETS);
            segment.setStatus("occupied");
            segment.setExperimentKey(exp.getExpId());
            segment.setExperimentName(exp.getName());
            segment.setExperimentStatus(exp.getStatus());

            List<Bucket> buckets = bucketMapper.selectActiveBuckets(exp.getExpId());
            List<TrafficMapResponse.BucketOccupancy> bucketOccupancies = new ArrayList<>();

            for (Bucket v : buckets) {
                TrafficMapResponse.BucketOccupancy vo = new TrafficMapResponse.BucketOccupancy();
                vo.setBucketKey(v.getBucketId());
                vo.setBucketName(v.getName());
                vo.setBucketStart(v.getBucketStart());
                vo.setBucketEnd(v.getBucketEnd());
                vo.setPercent((v.getBucketEnd() - v.getBucketStart() + 1) * 100.0 / TOTAL_BUCKETS);
                bucketOccupancies.add(vo);
            }

            segment.setBuckets(bucketOccupancies);
            segments.add(segment);
            usedBuckets += segment.getBucketCount();
        }

        // 添加空闲桶段
        if (usedBuckets < TOTAL_BUCKETS) {
            TrafficMapResponse.BucketSegment freeSegment = new TrafficMapResponse.BucketSegment();
            freeSegment.setBucketStart(0);
            freeSegment.setBucketEnd(TOTAL_BUCKETS - 1);

            // 排除已占用的区间
            List<int[]> occupiedRanges = segments.stream()
                .map(s -> new int[]{s.getBucketStart(), s.getBucketEnd()})
                .collect(Collectors.toList());

            TrafficMapResponse.BucketSegment detailedFree = calculateFreeSegments(occupiedRanges);
            if (detailedFree != null) {
                segments.add(detailedFree);
            }
        }

        // 检测冲突
        conflicts = detectLayerConflicts(layer.getId());

        lo.setTotalBuckets(usedBuckets);
        lo.setTrafficPercent(usedBuckets * 100.0 / TOTAL_BUCKETS);
        lo.setSegments(segments);
        lo.setConflicts(conflicts);

        return lo;
    }

    private TrafficMapResponse.BucketSegment calculateFreeSegments(List<int[]> occupiedRanges) {
        if (occupiedRanges.isEmpty()) {
            TrafficMapResponse.BucketSegment free = new TrafficMapResponse.BucketSegment();
            free.setBucketStart(0);
            free.setBucketEnd(TOTAL_BUCKETS - 1);
            free.setBucketCount(TOTAL_BUCKETS);
            free.setPercent(100.0);
            free.setStatus("free");
            return free;
        }

        // 简化实现：计算总空闲桶数
        int totalOccupied = occupiedRanges.stream()
            .mapToInt(r -> r[1] - r[0] + 1)
            .sum();

        int freeBuckets = TOTAL_BUCKETS - totalOccupied;
        if (freeBuckets <= 0) {
            return null;
        }

        TrafficMapResponse.BucketSegment free = new TrafficMapResponse.BucketSegment();
        free.setBucketStart(-1); // 表示分散的空闲桶
        free.setBucketEnd(-1);
        free.setBucketCount(freeBuckets);
        free.setPercent(freeBuckets * 100.0 / TOTAL_BUCKETS);
        free.setStatus("free");
        return free;
    }
}
