package com.onemount.javahexagonal.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Hỗ trợ Java 8 Date/Time (LocalDateTime, LocalDate...)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 2. Không báo lỗi khi gặp field lạ trong JSON (giúp hệ thống ổn định khi update model)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 3. Không serialize các trường bị null (tiết kiệm dung lượng bộ nhớ Redis/Network)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 4. Hỗ trợ đọc các interface/abstract types (như Page, List...)
        // Lưu ý: Nếu vẫn lỗi Page, hãy dùng giải pháp RestPage tôi đã hướng dẫn trước đó.

        return mapper;
    }
}