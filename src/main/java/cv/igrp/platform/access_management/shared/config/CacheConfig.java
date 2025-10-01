package cv.igrp.platform.access_management.shared.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Collection;
import java.util.concurrent.Callable;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var serializer = new GenericJackson2JsonRedisSerializer();

        var config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                );

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();

        return new FallbackCacheManager(redisCacheManager);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    // ---- Safe wrapper classes ----
    public static class FallbackCacheManager implements CacheManager {
        private final CacheManager delegate;

        public FallbackCacheManager(CacheManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public Cache getCache(@NotNull String name) {
            Cache cache = delegate.getCache(name);
            return (cache != null) ? new SafeCache(cache) : null;
        }

        @NotNull
        @Override
        public Collection<String> getCacheNames() {
            return delegate.getCacheNames();
        }
    }

    public static class SafeCache implements Cache {
        private final Cache delegate;

        public SafeCache(Cache delegate) {
            this.delegate = delegate;
        }

        @NotNull
        @Override
        public String getName() {
            return delegate.getName();
        }

        @NotNull
        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(@NotNull Object key) {
            try {
                return delegate.get(key);
            } catch (RedisConnectionFailureException e) {
                return null; // fallback to DB (cache miss)
            }
        }

        @Override
        public <T> T get(@NotNull Object key, Class<T> type) {
            try {
                return delegate.get(key, type);
            } catch (RedisConnectionFailureException e) {
                return null;
            }
        }

        @Override
        public <T> T get(@NotNull Object key, @NotNull Callable<T> valueLoader) {
            try {
                return delegate.get(key, valueLoader);
            } catch (RedisConnectionFailureException e) {
                try {
                    return valueLoader.call(); // run DB loader directly
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        @Override
        public void put(@NotNull Object key, Object value) {
            try {
                delegate.put(key, value);
            } catch (RedisConnectionFailureException e) {
                // log and ignore (skip caching)
            }
        }

        @Override
        public void evict(@NotNull Object key) {
            try {
                delegate.evict(key);
            } catch (RedisConnectionFailureException e) {
                // ignore
            }
        }

        @Override
        public void clear() {
            try {
                delegate.clear();
            } catch (RedisConnectionFailureException e) {
                // ignore
            }
        }
    }
}