package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 2.判断是关注还是取消关注
        if (isFollow) {
            // 3.关注 - 在数据库新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);

        }else {
            // 4.取消关注 - 删除数据库中的数据
            remove(new QueryWrapper<Follow>().eq("follow_user_id", followUserId).eq("user_id", userId));
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
}
