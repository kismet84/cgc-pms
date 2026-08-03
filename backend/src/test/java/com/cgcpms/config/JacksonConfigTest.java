package com.cgcpms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonConfigTest {

    private final ObjectMapper mapper = mapper();

    @Test
    void normalizesAllIncomingDecimalsToTwoPlaces() throws Exception {
        assertEquals(new BigDecimal("1.20"), mapper.readValue("\"1.2\"", BigDecimal.class));
        assertEquals(new BigDecimal("10.00"), mapper.readValue("10.0000", BigDecimal.class));
        assertEquals(new BigDecimal("100.00"), mapper.readValue("1e2", BigDecimal.class));
    }

    @Test
    void rejectsNonZeroPrecisionBeyondTwoPlaces() {
        assertThrows(MismatchedInputException.class,
                () -> mapper.readValue("\"1.234\"", BigDecimal.class));
    }

    private static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jsonNumberCustomizer().customize(builder);
        return builder.build();
    }
}
