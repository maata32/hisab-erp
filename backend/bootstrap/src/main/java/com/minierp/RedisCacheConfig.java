package com.minierp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
class RedisCacheConfig {

    /**
     * Use JSON instead of JDK serialization so we don't need every cached value
     * to implement {@link java.io.Serializable}. Records work fine; LocalDate/Instant
     * are handled by the JavaTimeModule.
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        // Records (Java 21) are final classes, so DefaultTyping.NON_FINAL doesn't tag them.
        // Use EVERYTHING so the @class marker is written for every value, including records.
        ObjectMapper om = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY)
                .build();

        Duration ttl = cacheProperties.getRedis().getTimeToLive() == null
                ? Duration.ofMinutes(5)
                : cacheProperties.getRedis().getTimeToLive();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(om)));
    }
}
