package com.gateflow.victor.pipeline.consumer;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.pipeline.storage.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ClickHouse Writer - 批量写入优化
 * 使用批量插入提升性能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickHouseWriter {
    
    private final EventRepository eventRepository;

    /**
     * 批量写入事件 - 使用高性能批量插入
     */
    public void writeBatch(List<EventDTO> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        try {
            int count = eventRepository.insertBatch(events);
            log.info("Successfully wrote {} events to ClickHouse", count);
        } catch (Exception e) {
            log.error("Failed to write batch of {} events to ClickHouse", events.size(), e);
            fallbackToSingleInsert(events);
        }
    }
    
    /**
     * 回退策略：逐条写入（当批量写入失败时）
     */
    private void fallbackToSingleInsert(List<EventDTO> events) {
        int success = 0;
        int failed = 0;

        for (EventDTO event : events) {
            try {
                eventRepository.insert(event);
                success++;
            } catch (Exception e) {
                log.error("Failed to write event: {}", event.getEventId(), e);
                failed++;
            }
        }

        if (failed > 0) {
            throw new RuntimeException(
                String.format("Batch write failed, fallback also incomplete: success=%d, failed=%d", success, failed));
        }

        log.warn("Fallback insert completed: all {} events written individually", success);
    }
}