package com.lms.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionHelper {

    private final CacheManager cacheManager;

    /**
     * Clear the cache for a specific course after the transaction successfully commits.
     * Use this when updating lessons, chapters, or courses to prevent ghost evictions on rollback.
     */
    public void evictCourseCacheAfterCommit(String courseSlug) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(courseSlug);
                }
            });
        } else {
            // No transaction active, evict immediately
            evictNow(courseSlug);
        }
    }

    private void evictNow(String courseSlug) {
        try {
            var detailsCache = cacheManager.getCache("courseDetails");
            if (detailsCache != null && courseSlug != null) {
                detailsCache.evict(courseSlug);
                log.debug("Evicted courseDetails cache for slug: {}", courseSlug);
            }

            var searchCache = cacheManager.getCache("publicCourseSearch");
            if (searchCache != null) {
                searchCache.clear();
                log.debug("Cleared publicCourseSearch cache");
            }
        } catch (Exception e) {
            log.error("Failed to evict cache for course {}: {}", courseSlug, e.getMessage());
        }
    }
}
