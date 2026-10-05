package com.jscreator.social.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 时间字段的序列化对齐原版 mysql2 + JSON.stringify。
 *
 * <p>原版把 DATETIME 交给 mysql2（timezone='local'）变成 JS Date（按本机时区理解墙上时间），
 * 再 {@code JSON.stringify} 成 UTC 的 ISO 串：{@code 2026-09-03T03:38:23.000Z}
 * （库里是 {@code 2026-09-03 11:38:23}，本机 Asia/Shanghai）。Java 这边默认输出
 * {@code 2026-09-03T11:38:23}，会让 follow_time / favorited_at 这类非常见键的对照失败，
 * 所以在这里统一按同一规则渲染（created_at / updated_at 等键对照时本就跳过，值也一并保持一致）。
 */
@Configuration
public class SocialJsonConfig {

    private static final DateTimeFormatter NODE_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer mysql2DatetimeCustomizer() {
        JsonSerializer<LocalDateTime> localDateTime = new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(nodeIso(value.atZone(ZoneId.systemDefault()).toInstant()));
            }
        };
        JsonSerializer<Timestamp> timestamp = new JsonSerializer<>() {
            @Override
            public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(nodeIso(value.toInstant()));
            }
        };
        return builder -> {
            builder.serializerByType(LocalDateTime.class, localDateTime);
            builder.serializerByType(Timestamp.class, timestamp);
        };
    }

    private static String nodeIso(Instant instant) {
        return NODE_ISO.format(instant);
    }
}
