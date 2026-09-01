package com.hmdp.utils;

public interface ILock {

    /**
     * 获取锁
     * @param timeoutSet 设置锁的超时时间
     * @return 返回true则获取锁成功，反之失败
     */
    boolean tryLock(long timeoutSet);

    /**
     * 释放锁
     */
    void unlock();
}
