package com.tudou.tudoumianshi.manager;
import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.tudou.tudoumianshi.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
@Slf4j
@Service
public class CounterManager {

    @Resource
    private RedissonClient redissonClient;

    // 使用LongAdder作为高效计数器
    private static final LongAdder requestCounter = new LongAdder();

    //@NacosValue(value = "${timeInterval}", autoRefreshed = true)
    private Integer timeInterval;
    @Resource
    private RedisTemplate redisTemplate;

//    private final Cache<String, Integer> answerCacheMap =
//            Caffeine.newBuilder().initialCapacity(1024)
//                    // 缓存180秒移除
//                    .expireAfterAccess(60L, TimeUnit.SECONDS)
//                    .build();



    /**
     * 增加并返回计数，默认统计一分钟内的计数结果
     * @param key 缓存键
     * @return
     */
    @SentinelResource(value = "incrAndGetCounter", blockHandler = "handleBlock")
    public long incrAndGetCounter(String key) {
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }
    public long handleBlock() {
        requestCounter.increment();
        // 降级处理逻辑
        return requestCounter.longValue();
    }

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
//        Integer count = answerCacheMap.getIfPresent(redisKey);
//        if (count == null) {
//            answerCacheMap.put(redisKey, 0);
//        }
//        count = answerCacheMap.getIfPresent(redisKey);
//        count+=1;
//        answerCacheMap.put(redisKey,count);
        requestCounter.increment();

        Date lastTime = new Date(System.currentTimeMillis() - 5000); // 设置为5秒前的时间

        // 获取当前时间
        Date currentTime = new Date();
        // 使用DateUtil工具类检查当前时间与上一段时间是否超过5秒
        int count_=0;
        if (DateUtil.secondsBetween(lastTime, currentTime) > 2) {
            // 保存计数器到 Redis
            count_=saveByRedis(redisKey, requestCounter.intValue(), expirationTimeInSeconds);
        }
        return count_;
    }

    private int saveByRedis(String key, Integer count, int expirationTimeInSeconds) {
        if(count==null){
            return 0;
        }
        redisTemplate.opsForValue().set(key,count,expirationTimeInSeconds, TimeUnit.SECONDS);
        return (int) redisTemplate.opsForValue().get(key);
    }
}
