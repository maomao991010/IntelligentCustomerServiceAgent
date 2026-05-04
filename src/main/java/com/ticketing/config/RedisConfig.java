package com.ticketing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        
        // 创建Jackson2JsonRedisSerializer
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        // 配置ObjectMapper，添加JavaTimeModule支持
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // 注册JavaTimeModule，支持Java 8日期时间类型
        objectMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL, 
                                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        
        serializer.setObjectMapper(objectMapper);
        
        // 设置键的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // 设置哈希键的序列化器
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        // 设置值的序列化器
        redisTemplate.setValueSerializer(serializer);
        // 设置哈希值的序列化器
        redisTemplate.setHashValueSerializer(serializer);
        
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
