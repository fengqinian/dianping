package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }

        // 6.返回数据给前端
        return Result.ok(shop);
    }

   /* private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        // 1.从redis数据库查询
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);

        // 2.未命中 - 返回数据给前端
        if (StrUtil.isBlank(shopCache)) {
            return null;
        }

        // 3.命中 - 将数据从JSON字符串转化为对象
        RedisData redisDataBean = JSONUtil.toBean(shopCache, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisDataBean.getData(), Shop.class);
        LocalDateTime expireTime = redisDataBean.getExpireTime();

        // 4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期 - 返回数据
            return shop;
        }

        // 6.过期 - 进行数据重建
        // 6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 从redis数据库查询
        key = CACHE_SHOP_KEY + id;
        shopCache = stringRedisTemplate.opsForValue().get(key);
        // 获取锁之后再次判断缓存数据库是否有数据
        if (StrUtil.isBlank(shopCache)) {
            return null;
        }

        // 6.2判断是否获取锁成功
        if (isLock) {
            //获取成功 - 重建缓存数据
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                }catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        // 6.返回过期店铺数据
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        // 1.从redis数据库查询
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);

        // 2.存在 - 返回数据给前端
        if (StrUtil.isNotBlank(shopCache)) {
            return JSONUtil.toBean(shopCache, Shop.class);
        }
        // 判断是否为空字符串
        if (shopCache != null) {
            return null;
        }

        // 3.进行缓存重建
        // 3.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shopDB = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 再次判断一下是否有缓存数据
            shopCache = stringRedisTemplate.opsForValue().get(key);

            // 2.存在 - 返回数据给前端
            if (StrUtil.isNotBlank(shopCache)) {
                return JSONUtil.toBean(shopCache, Shop.class);
            }
            // 判断是否为空字符串
            if (shopCache != null) {
                return null;
            }

            // 3.2获取失败 - 进行休眠
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 3.3获取成功 - 从数据库查询数据
            shopDB = getById(id);

            // 4.不存在 - 返回错误信息 没有该商铺
            if (shopDB == null) {
                // 向redis里面写入null
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL + RandomUtil.randomInt(0, 5), TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }

            // 5.存在 - 将数据写入redis
            String shopJSON = JSONUtil.toJsonStr(shopDB);
            stringRedisTemplate.opsForValue().set(key, shopJSON, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            unLock(lockKey);
        }

        // 6.返回数据给前端
        return shopDB;
    }

    public Shop queryWithPassThrough(Long id) {
        // 1.从redis数据库查询
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);

        // 2.存在 - 返回数据给前端
        if (StrUtil.isNotBlank(shopCache)) {
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return shop;
        }
        // 判断是否为空字符串
        if (shopCache != null) {
            return null;
        }

        // 3.不存在 - 从数据库查询数据
        Shop shopDB = getById(id);

        // 4.不存在 - 返回错误信息 没有该商铺
        if (shopDB == null) {
            // 向redis里面写入null
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL + RandomUtil.randomInt(0, 5), TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }

        // 5.存在 - 将数据写入redis
        String shopJSON = JSONUtil.toJsonStr(shopDB);
        stringRedisTemplate.opsForValue().set(key, shopJSON, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6.返回数据给前端
        return shopDB;
    }

    public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);

        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));

        // 3.保存到redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }*/

    /**
     * 更新商铺信息
     * @return
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 1.更新商铺数据
        if (shop.getId() == null) {
            return Result.fail("商铺信息不存在");
        }
        updateById(shop);

        // 2.删除redis缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}
