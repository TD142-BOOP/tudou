package com.tudou.tudoumianshi.manager;
import cn.dev33.satoken.fun.SaFunction;
import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tudou.tudoumianshi.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
@Slf4j
@Service
public class CounterManager {


     @Resource
    private RedissonClient redissonClient;

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
        counterCache.asMap().compute(redisKey, (k, v) -> {
            return v == null ? 1 : v + 1;
        });
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            Integer count = counterCache.asMap().get(redisKey);
            RAtomicLong atomicLong = redissonClient.getAtomicLong(redisKey);
            if (atomicLong == null) {
                atomicLong.addAndGet(count);
                atomicLong.expire(1, TimeUnit.MINUTES);
            } else {
                atomicLong.addAndGet(count);
            }
            counterCache.invalidateAll();
        }, 0, 5, TimeUnit.SECONDS);

        return redissonClient.getAtomicLong(redisKey).get();
    }
}
