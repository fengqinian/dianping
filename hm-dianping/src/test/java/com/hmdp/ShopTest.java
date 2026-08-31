package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class ShopTest {

    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                System.out.println(order);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(runnable);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - begin);
    }
}
