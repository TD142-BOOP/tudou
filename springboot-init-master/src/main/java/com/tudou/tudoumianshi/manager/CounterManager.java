package com.tudou.tudoumianshi.manager;
import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
@Slf4j
@Service
public class CounterManager {


    @Resource
    private RedissonClient redissonClient;

    private static final String LUA_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 1 then " +
                    "  redis.call('set', KEYS[1], ARGV[1]); " +
                    "else " +
                    "  redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2]); " +
                    "end " +
                    "return ARGV[1];";

    private static final ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
            5,             // 核心线程数
            10,                        // 最大线程数
            60L,                       // 线程空闲存活时间
            TimeUnit.SECONDS,           // 存活时间单位
            new LinkedBlockingQueue<>(10000),  // 阻塞队列容量
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程处理任务
    );

    // 全局线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 使用LongAdder作为高效计数器
    //private static final LongAdder requestCounter = new LongAdder();

    //@NacosValue(value = "${timeInterval}", autoRefreshed = true)


    private final Cache<String, Integer> counterCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    // 定时任务执行间隔（毫秒），这里设置为 2 秒
    private static final long INTERVAL = 2 * 1000;

    /**
     * 增加并返回计数，默认统计一分钟内的计数结果
     * @param key 缓存键
     * @return
     */
    @SentinelResource(value = "incrAndGetCounter", blockHandler = "handleBlock")
    public long incrAndGetCounter(String key) {
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }
//    public long handleBlock() {
//        requestCounter.increment();
//        // 降级处理逻辑
//        return requestCounter.longValue();
//    }

    /**
     * 增加并返回计数
     *
     * @param key          缓存键
     * @param timeInterval 时间间隔
     * @param timeUnit     时间间隔单位
     * @return
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit) {
        int expirationTimeInSeconds;
        switch (timeUnit) {
            case SECONDS:
                expirationTimeInSeconds = timeInterval;
                break;
            case MINUTES:
                expirationTimeInSeconds = timeInterval * 60;
                break;
            case HOURS:
                expirationTimeInSeconds = timeInterval * 60 * 60;
                break;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit. Use SECONDS, MINUTES, or HOURS.");
        }

        return incrAndGetCounter(key, timeInterval, timeUnit, expirationTimeInSeconds);
    }

    /**
     * 增加并返回计数
     *
     * @param key                     缓存键
     * @param timeInterval            时间间隔
     * @param timeUnit                时间间隔单位
     * @param expirationTimeInSeconds 计数器缓存过期时间
     * @return
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit, int expirationTimeInSeconds) {
        if (StrUtil.isBlank(key)) {
            return 0;
        }

        // 根据时间粒度生成 redisKey
        long timeFactor;
        switch (timeUnit) {
            case SECONDS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval;
                break;
            case MINUTES:
                timeFactor = Instant.now().getEpochSecond() / 60 / timeInterval;
                break;
            case HOURS:
                timeFactor = Instant.now().getEpochSecond() / 3600 / timeInterval;
                break;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit. Use SECONDS, MINUTES, or HOURS.");
        }

        String redisKey = key + ":" + timeFactor;

        // 更新本地缓存中的计数
        counterCache.asMap().compute(redisKey, (k, v) -> v == null ? 1 : v + 1);

        // 返回本地缓存中的计数值
        return redissonClient.getAtomicLong(redisKey).get();
    }

    @PostConstruct
    public void init() {
        scheduleSyncToRedis();
    }
    // 创建一个固定大小的线程池
    // 自定义线程池（IO 密集型线程池）

    private void scheduleSyncToRedis() {
        scheduler.scheduleAtFixedRate(() -> {
            // 遍历 counterCache 中的所有键值对
            counterCache.asMap().forEach((key, value) -> {
                // 提交同步任务到线程池
                CompletableFuture.runAsync(() -> {
                    try {
                        // Lua 脚本：如果键存在则更新值，如果键不存在则设置值并设置过期时间

                        // 获取 Redis 脚本对象
                        RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);

                        // 执行 Lua 脚本
                        script.eval(
                                RScript.Mode.READ_WRITE,
                                LUA_SCRIPT,
                                RScript.ReturnType.INTEGER,
                                Collections.singletonList(key),  // 使用本地缓存的 key 作为 Redis 的 key
                                value,  // ARGV[1]: 本地缓存中的值
                                60      // ARGV[2]: 过期时间（秒）
                        );

                        log.info("Synced key: {}, value: {} to Redis", key, value);
                    } catch (Exception ex) {
                        log.error("Failed to sync key: {} to Redis", key, ex);
                    }
                }, customExecutor).exceptionally(ex -> {
                    // 捕获并处理异常
                    log.error("Exception occurred while syncing key: {}", key, ex);
                    return null;
                });
            });
        }, 0, 5, TimeUnit.SECONDS); // 立即执行，每隔 15 秒执行一次
    }
    @PreDestroy
    public void destroy() {
        customExecutor.shutdown();
        scheduler.shutdown();
    }


}
