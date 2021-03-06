package com.coy.l2cache.load;

import com.coy.l2cache.cache.Level2Cache;
import com.coy.l2cache.CacheSyncPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 自定义CacheLoader
 * <p>
 * 目的：主要是为了在使用refreshAfterWrite策略的特性：仅加载数据的线程阻塞，其他线程返回旧值
 * 结合@Cacheable(sync=true)，在高并发场景下可提供更佳的性能。
 *
 * @author chenck
 * @date 2020/5/9 14:28
 */
public class CustomCacheLoader implements CacheLoader<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(CustomCacheLoader.class);

    /**
     * <key, Callable>
     * 用于保证并发场景下对于不同的key找到对应的Callable进行数据加载
     * 注：ConcurrentReferenceHashMap是一个实现软/弱引用的map，防止OOM出现
     */
    private static final Map<Object, Callable<?>> VALUE_LOADER_CACHE = new ConcurrentReferenceHashMap<>();
    private String instanceId;
    private String cacheType;
    private String cacheName;
    private Level2Cache level2Cache;
    private CacheSyncPolicy cacheSyncPolicy;

    private CustomCacheLoader(String instanceId, String cacheType, String cacheName) {
        this.instanceId = instanceId;
        this.cacheType = cacheType;
        this.cacheName = cacheName;
    }

    /**
     * create CacheLoader instance
     */
    public static CustomCacheLoader newInstance(String instanceId, String cacheType, String cacheName) {
        return new CustomCacheLoader(instanceId, cacheType, cacheName);
    }

    @Override
    public void setLevel2Cache(Level2Cache level2Cache) {
        this.level2Cache = level2Cache;
    }

    @Override
    public void setCacheSyncPolicy(CacheSyncPolicy cacheSyncPolicy) {
        this.cacheSyncPolicy = cacheSyncPolicy;
    }

    @Override
    public void addValueLoader(Object key, Callable<?> valueLoader) {
        if (!VALUE_LOADER_CACHE.containsKey(key)) {
            VALUE_LOADER_CACHE.put(key, valueLoader);
        }
    }

    @Override
    public Object load(Object key) throws Exception {
        // 直接返回null，目的是使spring cache后续逻辑去执行具体的加载数据方法，然后put到缓存
        Callable<?> valueLoader = VALUE_LOADER_CACHE.get(key);
        /*if (null == valueLoader) {
            logger.debug("[CustomCacheLoader] valueLoader is null direct return null, key={}", key);
            return null;
        }*/

        LoadFunction loadFunction = new LoadFunction(this.instanceId, this.cacheType, cacheName, level2Cache, cacheSyncPolicy, valueLoader);
        return loadFunction.apply(key);
    }

}
