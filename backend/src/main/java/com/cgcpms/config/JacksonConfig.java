package com.cgcpms.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Global Jackson configuration.
 * <p>
 * Serializes all Long/long values as JSON strings to prevent JavaScript
 * precision loss for Snowflake / bigint IDs (> 2^53-1), and normalizes
 * inbound business decimals to two fractional digits.
 * Complements field-level {@code @JsonSerialize(using = ToStringSerializer.class)}
 * annotations already present on 29 entities.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonNumberCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(long.class, ToStringSerializer.instance);
            builder.deserializerByType(BigDecimal.class, new TwoDecimalBigDecimalDeserializer());
        };
    }

    static final class TwoDecimalBigDecimalDeserializer extends StdDeserializer<BigDecimal> {
        private TwoDecimalBigDecimalDeserializer() {
            super(BigDecimal.class);
        }

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken token = parser.currentToken();
            if (token != JsonToken.VALUE_NUMBER_INT
                    && token != JsonToken.VALUE_NUMBER_FLOAT
                    && token != JsonToken.VALUE_STRING) {
                return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
            }
            String raw = parser.getText().trim();
            if (raw.isEmpty()) return null;
            try {
                BigDecimal value = new BigDecimal(raw);
                if (value.stripTrailingZeros().scale() > 2) {
                    return (BigDecimal) context.handleWeirdStringValue(
                            BigDecimal.class, raw, "小数最多保留2位");
                }
                return value.setScale(2);
            } catch (NumberFormatException ex) {
                return (BigDecimal) context.handleWeirdStringValue(
                        BigDecimal.class, raw, "不是有效小数");
            }
        }
    }
}
