package com.gateflow.victor.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流量地图响应
 */
@Data
public class TrafficMapResponse {

    /**
     * 总流量 10000 桶
     */
    private int totalBuckets;

    /**
     * 各域的流量占用
     */
    private List<DomainTraffic> domains;

    @Data
    public static class DomainTraffic {
        private Long domainId;
        private String domainName;
        private int totalBuckets;
        private double trafficPercent;
        private List<LayerOccupancy> layers;
    }

    @Data
    public static class LayerOccupancy {
        private Long layerId;
        private String layerKey;
        private String layerName;
        private String salt;
        private int totalBuckets;
        private double trafficPercent;
        private List<BucketSegment> segments;
        private List<ConflictWarning> conflicts;
    }

    @Data
    public static class BucketSegment {
        private int bucketStart;
        private int bucketEnd;
        private int bucketCount;
        private double percent;
        private String status; // "occupied", "free"
        private String experimentKey;
        private String experimentName;
        private String experimentStatus;
        private List<BucketOccupancy> buckets;
    }

    @Data
    public static class BucketOccupancy {
        private String bucketKey;
        private String bucketName;
        private int bucketStart;
        private int bucketEnd;
        private double percent;
    }

    @Data
    public static class ConflictWarning {
        private String type; // "overlap", "overflow"
        private String message;
        private Long experimentId1;
        private Long experimentId2;
        private int conflictStart;
        private int conflictEnd;
    }

    /**
     * 单层占用详情
     */
    @Data
    public static class LayerDetailResponse {
        private Long layerId;
        private String layerKey;
        private String layerName;
        private String salt;
        private int totalBuckets;
        private int usedBuckets;
        private int freeBuckets;
        private double usagePercent;
        private List<ExperimentOccupancy> experiments;
        private Map<Integer, String> bucketMap; // bucket -> experiment_key mapping (sampled)
    }

    @Data
    public static class ExperimentOccupancy {
        private Long experimentId;
        private String experimentKey;
        private String experimentName;
        private String status;
        private int bucketStart;
        private int bucketEnd;
        private int bucketCount;
        private double trafficPercent;
        private List<BucketDetail> buckets;
    }

    @Data
    public static class BucketDetail {
        private String bucketKey;
        private String bucketName;
        private int bucketStart;
        private int bucketEnd;
        private int bucketCount;
        private double trafficPercent;
    }
}
