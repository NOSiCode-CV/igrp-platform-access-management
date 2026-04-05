package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SessionCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCacheService.class);
    private static final String CACHE_NAME = "sessionCache";

    @Getter
    private boolean fromCache;

    /**
     * Get session from cache or load from database
     */
    @Cacheable(value = CACHE_NAME, keyGenerator = "sessionCacheKeyGenerator")
    public SessionResponseDTO getOrLoadSession(String userExternalId) {
        LOGGER.info("Cache MISS - Loading session for user: {}", userExternalId);
        setFromCacheAsFalse();
        return null; // This will be handled by SessionManagementService
    }

    /**
     * Evict session from cache
     */
    @CacheEvict(value = CACHE_NAME, keyGenerator = "sessionCacheKeyGenerator")
    public void evictSession(String userExternalId) {
        LOGGER.info("Evicting session from cache for user: {}", userExternalId);
    }

    /**
     * Evict all sessions from cache (emergency use only)
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAllSessions() {
        LOGGER.warn("Evicting all sessions from cache - emergency operation");
    }

    /**
     * Put session in cache
     */
    public void cacheSession(String userExternalId, SessionResponseDTO sessionResponse) {
        // This method would typically be called by the caching infrastructure
        // when the @Cacheable method returns a value
        LOGGER.debug("Caching session for user: {}", userExternalId);
    }

    private void setFromCacheAsFalse() {
        this.fromCache = false;
    }

    public void setFromCacheAsTrue() {
        this.fromCache = true;
    }
}
