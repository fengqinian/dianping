package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券数据
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2.判断是否已经开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动未开始!");
        }

        // 3.判断是否已经结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束!");
        }

        // 4.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.判断订单表是否有该用户的下单
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("已下单，无法再次下单");
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1").gt("stock", 0)
                .eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("库存不足!");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1获取订单唯一id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        // 7.2设置用户id
        UserDTO user = UserHolder.getUser();
        voucherOrder.setUserId(user.getId());
        //voucherOrder.setUserId(1L); //测试使用，输入用户id

        // 7.3设置优惠卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
