package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 封装数据到RedisDate
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 将数据写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 1.从redis数据库查询
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.存在 - 返回数据给前端
        if (StrUtil.isNotBlank(json)) {
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        // 判断是否为空字符串
        if (json != null) {
            return null;
        }

        // 3.不存在 - 从数据库查询数据
        R r = dbFallBack.apply(id);

        // 4.不存在 - 返回错误信息 没有该商铺
        if (r == null) {
            // 向redis里面写入null
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL + RandomUtil.randomInt(0, 5), TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }

        // 5.存在 - 将数据写入redis
        this.set(key, r, time, unit);

        // 6.返回数据给前端
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 1.从redis数据库查询
        String key = keyPrefix + id;
        String cache = stringRedisTemplate.opsForValue().get(key);

        // 2.未命中 - 返回数据给前端
        if (StrUtil.isBlank(cache)) {
            return null;
        }

        // 3.命中 - 将数据从JSON字符串转化为对象
        RedisData redisDataBean = JSONUtil.toBean(cache, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisDataBean.getData(), type);
        LocalDateTime expireTime = redisDataBean.getExpireTime();

        // 4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期 - 返回数据
            return r;
        }

        // 6.过期 - 进行数据重建
        // 6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 从redis数据库查询
        key = keyPrefix + id;
        cache = stringRedisTemplate.opsForValue().get(key);
        // 获取锁之后再次判断缓存数据库是否有数据
        if (StrUtil.isBlank(cache)) {
            return null;
        }

        // 6.2判断是否获取锁成功
        if (isLock) {
            //获取成功 - 重建缓存数据
            String finalKey = key;
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallBack.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(finalKey, r1, time, unit);
                }catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        // 6.返回过期店铺数据
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
