package com.cgcpms.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson configuration.
 * <p>
 * Serializes all Long/long values as JSON strings to prevent JavaScript
 * precision loss for Snowflake / bigint IDs (> 2^53-1).
 * Complements field-level {@code @JsonSerialize(using = ToStringSerializer.class)}
 * annotations already present on 29 entities.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(long.class, ToStringSerializer.instance);
        };
    }
}
