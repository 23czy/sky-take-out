package com.sky.controller.user;

import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    /***
     * 获取店铺营业状态
     */
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        // 🛠️ 修正：用 String 强转接收
        String statusStr = (String) redisTemplate.opsForValue().get(KEY);

        // 🛠️ 防御性编程：防空指针保护
        if (statusStr == null) {
            return Result.success(0);
        }

        Integer status = Integer.valueOf(statusStr);
        log.info("用户端获取店铺的营业状态：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}