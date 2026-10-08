package com.lms.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Bug thật (29/09/2026): DefaultTyping.NON_FINAL bỏ qua gắn "@class" cho record Java (VD
        // CoursePublicDto.DetailRes) khi nó là giá trị GỐC (root) được cache — record luôn final,
        // NON_FINAL coi final ở root là "không cần" gắn type id. Nhưng lúc đọc lại,
        // GenericJackson2JsonRedisSerializer chỉ biết deserialize về Object.class (Spring Cache
        // không truyền type hint), nên thiếu "@class" ở root → luôn ném
        // InvalidTypeIdException/MismatchedInputException, bị CacheErrorHandler nuốt lỗi và âm
        // thầm coi là cache MISS — cache VẪN ghi (PUT) bình thường nên trông như "có hoạt động",
        // chỉ là không BAO GIỜ đọc lại được, vô hiệu hóa hoàn toàn tác dụng giảm tải DB của cache
        // (phát hiện khi verify lại toàn bộ cache courseDetails/publicProfile). EVERYTHING gắn
        // type id cho mọi giá trị kể cả final/record ở root, khắc phục triệt để.
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);

        // Bug thật (08/10/2026): PageImpl không có constructor mặc định/@JsonCreator (và
        // Pageable/PageRequest lồng bên trong cũng vậy, constructor private) — Jackson GHI
        // (PUT) vào Redis bình thường nhưng đọc lại (GET) luôn ném MismatchedInputException,
        // bị CacheErrorHandler bên dưới nuốt lỗi và fallback DB, khiến cache "publicCourseSearch"
        // (CoursePublicService.search, trả Page<SummaryRes>) mất tác dụng hoàn toàn dù trông như
        // vẫn "hoạt động". Đăng ký deserializer tự viết, chỉ áp dụng riêng cho PageImpl.
        SimpleModule pageModule = new SimpleModule();
        pageModule.addDeserializer(PageImpl.class, new PageImplRedisDeserializer());
        mapper.registerModule(pageModule);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("categories", defaultCacheConfig.entryTtl(Duration.ofDays(1)));
        cacheConfigurations.put("courseDetails", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("publicCourseSearch", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        // Ghép dữ liệu từ 4 nguồn (user/enrollments/wishlist/certificates) — TTL ngắn thay vì
        // evict thủ công ở từng service ghi (Enrollment/Wishlist/Certificate), chấp nhận trễ vài
        // phút để tránh nhiều điểm phải nhớ evict đồng thời, dễ sót gây cache "ngầm" (29/09/2026).
        cacheConfigurations.put("publicProfile", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.error("Redis unreachable on GET for cache '{}', key '{}', falling back to DB: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.error("Redis unreachable on PUT for cache '{}', key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("Redis unreachable on EVICT for cache '{}', key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("Redis unreachable on CLEAR for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
