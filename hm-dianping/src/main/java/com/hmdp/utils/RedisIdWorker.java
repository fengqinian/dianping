package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1767225600;
    /**
     * 偏移的位数
     */
    private static final long COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String prefixKey) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long stampSecond = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        LocalDate dateNow = LocalDate.now();
        Long count = stringRedisTemplate.opsForValue().increment("icr" + prefixKey + ":" + dateNow.format(DateTimeFormatter.ofPattern("yyyy:MM:dd")));

        // 3.拼接时间戳和序列号
        return stampSecond << COUNT_BITS | count;
    }
}
