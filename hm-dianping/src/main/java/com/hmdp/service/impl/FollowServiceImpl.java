package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 2.判断是关注还是取消关注
        String key = "follow:" + userId;
        if (isFollow) {
            // 3.关注 - 在数据库新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);

            // 将关注用户缓存到redis
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else {
            // 4.取消关注 - 删除数据库中的数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("follow_user_id", followUserId).eq("user_id", userId));
            // 将redis关注的用户删除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 2.查询数据库是否有关注
        Integer isFollow = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();

        return Result.ok(isFollow > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 1，获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 2.查询redis中的共同关注信息
        String key1 = "follow:" + userId;
        String key2 = "follow:" + id;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 3.解析数据
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 4.查询用户信息
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        // 5.返回数据给前端
        return Result.ok(users);
    }
}
