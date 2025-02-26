package com.tudou.tudoumianshi.manager;
import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
@Slf4j
@Service
public class CounterManager {


     @Resource
    private RedissonClient redissonClient;

    // 全局线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 使用LongAdder作为高效计数器
    //private static final LongAdder requestCounter = new LongAdder();

    //@NacosValue(value = "${timeInterval}", autoRefreshed = true)


    private Cache<String, Integer> counterCache = Caffeine.newBuilder()
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
        // Lua 脚本
//        String luaScript =
//                "if redis.call('exists', KEYS[1]) == 1 then " +
//                        "  return redis.call('incr', KEYS[1]); " +
//                        "else " +
//                        "  redis.call('set', KEYS[1], 1); " +
//                        "  redis.call('expire', KEYS[1], ARGV[1]); " +
//                        "  return 1; " +
//                        "end";
//
//        // 执行 Lua 脚本
//        RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
//        Object countObj = script.eval(
//                RScript.Mode.READ_WRITE,
//                luaScript,
//                RScript.ReturnType.INTEGER,
//                Collections.singletonList(key), expirationTimeInSeconds);
//        return (long) countObj;
//


//        counterCache.asMap().compute(redisKey, (k, v) -> {
//            return v == null ? 1 : v + 1;
//        });
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//        scheduler.scheduleAtFixedRate(() -> {
//            Integer count = counterCache.asMap().get(redisKey);
//            try {
//                RAtomicLong atomicLong = redissonClient.getAtomicLong(redisKey);
//                if (atomicLong.isExists()) {
//                    // 键已存在，仅增加计数
//                    atomicLong.set(count);
//                } else {
//                    // 键不存在，设置初始值并设置过期时间
//                    atomicLong.set(count);
//                    atomicLong.expire(1, TimeUnit.MINUTES);
//                }
//            }catch (Exception ex) {
//                log.error("Failed to sync local cache to Redis for key: {}", redisKey, ex);
//            }         
//        }, 0, 15, TimeUnit.SECONDS);

        // 更新本地缓存中的计数
        //int count = counterCache.asMap().compute(redisKey, (k, v) -> v == null ? 1 : v + 1);

        // 使用 CompletableFuture 异步同步到 Redis
        //CompletableFuture<Long> future = new CompletableFuture<>();

        // 启动定时任务异步同步到 Redis
//        scheduleSyncToRedis(redisKey, count, future);
//
//        try {
//            // 阻塞等待异步任务完成，并返回 Redis 中的值
//            return future.get(); // 阻塞直到任务完成
//        } catch (InterruptedException | ExecutionException ex) {
//            log.error("Failed to get result from Redis for key: {}", redisKey, ex);
//            // 如果异步任务失败，返回本地缓存中的值作为降级处理
//            return count;
        // 更新本地缓存中的计数
        int count = counterCache.asMap().compute(redisKey, (k, v) -> v == null ? 1 : v + 1);

        // 启动定时任务异步同步到 Redis
        scheduleSyncToRedis(redisKey, count);

        // 返回本地缓存中的计数值
        return redissonClient.getAtomicLong(redisKey).get();
        }
    private void scheduleSyncToRedis(String redisKey, int count) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String luaScript =
                        "if redis.call('exists', KEYS[1]) == 1 then " +
                                "  redis.call('set', KEYS[1], ARGV[1]); " +
                                "  redis.call('expire', KEYS[1], ARGV[2]); " +
                                "  return ARGV[1]; " +
                                "else " +
                                "  redis.call('set', KEYS[1], ARGV[1]); " +
                                "  redis.call('expire', KEYS[1], ARGV[2]); " +
                                "  return ARGV[1]; " +
                                "end";
                RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
                script.eval(
                        RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(redisKey),
                        count, // ARGV[1]: 替换的计数值
                        60     // ARGV[2]: 过期时间（秒）
                );
            } catch (Exception ex) {
                log.error("Failed to sync local cache to Redis for key: {}", redisKey, ex);
            }
        }, 0,15,TimeUnit.SECONDS); // 立即执行
    }

//    private void scheduleSyncToRedis(String redisKey, int count, CompletableFuture<Long> future) {
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//        scheduler.scheduleAtFixedRate(() -> {
//            try {
//                String luaScript =
//                        "if redis.call('exists', KEYS[1]) == 1 then " +
//                                "  return redis.call('incrby', KEYS[1], ARGV[1]); " +
//                                "else " +
//                                "  redis.call('set', KEYS[1], ARGV[1]); " +
//                                "  redis.call('expire', KEYS[1], ARGV[2]); " +
//                                "  return ARGV[1]; " +
//                                "end";
//
//                RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
//                Object result = script.eval(
//                        RScript.Mode.READ_WRITE,
//                        luaScript,
//                        RScript.ReturnType.INTEGER,
//                        Collections.singletonList(redisKey),
//                        count, // ARGV[1]: 增加的计数值
//                        60     // ARGV[2]: 过期时间（秒）
//                );
//
//                // 完成 CompletableFuture，返回 Redis 中的值
//                future.complete((Long) result);
//            } catch (Exception ex) {
//                log.error("Failed to sync local cache to Redis for key: {}", redisKey, ex);
//                // 完成 CompletableFuture，返回本地缓存中的值作为降级处理
//                future.complete((long) count);
//            } finally {
//                // 清空当前键的本地缓存
//                counterCache.invalidate(redisKey);
//            }
//        }, 0,5,TimeUnit.SECONDS); // 立即执行
//    }

}
