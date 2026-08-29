package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ShopTest {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void testShopSave() throws InterruptedException {

    }
}
