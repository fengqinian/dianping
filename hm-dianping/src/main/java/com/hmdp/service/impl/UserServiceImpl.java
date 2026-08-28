package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.手机号不正确,返回错误信息
            return Result.fail("手机号不正确");
        }

        // 3.手机号正确,生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.将验证码保存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code);
        stringRedisTemplate.expire(LOGIN_CODE_KEY + phone, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.向用户发送验证码
        log.debug("发送验证码成功: {}", code);

        return Result.ok();
    }

    /**
     * 登录校验
     * @param loginForm
     * @param session
     * @return
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.手机号不正确 - 返回错误信息
            return Result.fail("手机号不正确");
        }

        // 3.手机号正确 - 校验验证码
        String codeCache = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (codeCache == null || !codeCache.toString().equals(code)) {
            // 4.验证码不正确 - 返回错误信息
            return Result.fail("验证码不正确");
        }

        // 5.验证码正确 - 判断是否为新用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 6.用户为新用户 - 为用户注册
            user = createUserWithPhone(phone);
        }

        // 7.用户已注册过 - 将用户信息保存到redis
        // 7.1创建一个token
        String token = UUID.randomUUID().toString(true);
        // 7.2将user对象转换为Map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userDto = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fileName, fileValue) -> fileValue.toString()));
        // 7.3存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userDto);
        // 7.4设置过期时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.将token返回给
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        // 1.创建新用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 2.保存用户
        save(user);
        return user;
    }
}
