package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺类型
     * @return
     */
    @Override
    public List<ShopType> queryByList() {
        // 1.从redis中查询商铺类型
        String key = CACHE_SHOP_KEY + "type";
        String shopTypeCache = stringRedisTemplate.opsForValue().get(key);

        // 2.存在 - 返回数据给前端
        if (shopTypeCache != null && !shopTypeCache.isEmpty()) {
            return JSONUtil.toList(shopTypeCache, ShopType.class);
        }

        // 3.不存在 - 从数据库查询数据
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 4.不存在 - 返回错误信息给前端
        if (shopTypes == null || shopTypes.isEmpty()) {
            return null;
        }

        // 5.存在 - 将数据写入redis
        String shopTypeJSON = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(key, shopTypeJSON);
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL + RandomUtil.randomInt(0, 5), TimeUnit.MINUTES);

        // 6.返回数据给前端
        return shopTypes;
    }
}
