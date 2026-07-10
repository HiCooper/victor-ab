package com.gateflow.victor.pipeline.ingestion;

import com.gateflow.victor.pipeline.config.PipelineProperties;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据管道「入口」段集成测试：EventKafkaProducer → 真实 Kafka → 消费者。
 * <p>
 * 无 Docker 时整个类自动跳过；有 Docker（CI）时验证事件被发到正确 topic、按 userId 作 key 且可反序列化。
 */
@Testcontainers(disabledWithoutDocker = true)
class EventKafkaProducerIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private static final String TOPIC = "victor-events-it";

    @Test
    @DisplayName("EventKafkaProducer 发送的事件可被消费者按 topic 收到")
    void producesEventToKafka() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        KafkaTemplate<String, EventDTO> template =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        PipelineProperties props = new PipelineProperties();
        props.setKafkaTopic(TOPIC);
        EventKafkaProducer producer = new EventKafkaProducer(template, props);

        EventDTO event = new EventDTO();
        event.setEventId("evt-1");
        event.setUserId("u-1");
        event.setTimestamp(System.currentTimeMillis());

        try (KafkaConsumer<String, EventDTO> consumer = newConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500)); // 触发 group join / 分配

            producer.sendEvent(event);
            template.flush();

            ConsumerRecords<String, EventDTO> records = pollUntilNonEmpty(consumer);
            assertTrue(records.count() >= 1, "应至少收到 1 条事件");
            ConsumerRecord<String, EventDTO> record = records.iterator().next();
            assertEquals("u-1", record.key());
            assertEquals("evt-1", record.value().getEventId());
        }
    }

    private KafkaConsumer<String, EventDTO> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EventDTO.class.getName());
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecords<String, EventDTO> pollUntilNonEmpty(KafkaConsumer<String, EventDTO> consumer) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, EventDTO> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records;
            }
        }
        return ConsumerRecords.empty();
    }
}
