package com.insight.gateway.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.JacksonProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author 宣炳刚
 * @date 2019/9/21
 * @remark LocalDateTime类型序列化配置
 */
@Configuration
@ConditionalOnClass({Jackson2ObjectMapperBuilder.class, LocalDateTime.class})
public class LocalDateTimeSerializerConfig {

    /**
     * 获取序列化组件
     *
     * @param jacksonProperties JacksonProperties
     * @return SimpleModule
     */
    @Bean
    public SimpleModule localDateTimeSerializationModule(JacksonProperties jacksonProperties) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(LocalDateTime.class, jacksonProperties.getDateFormat()));

        return module;
    }

    public static class LocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
        private static final long serialVersionUID = 1L;
        private String dataFormat;

        /**
         * 构造方法
         *
         * @param type       LocalDateTime
         * @param dateFormat 日期格式
         */
        LocalDateTimeSerializer(Class<LocalDateTime> type, String dateFormat) {
            super(type);
            this.dataFormat = dateFormat;
        }

        /**
         * 序列化
         *
         * @param value    时间
         * @param gen      JsonGenerator
         * @param provider SerializerProvider
         */
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(DateTimeFormatter.ofPattern(dataFormat).format(value));
        }
    }
}
