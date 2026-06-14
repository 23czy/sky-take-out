package com.sky.controller.admin;

import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    /***
     * 设置店铺营业状态
     */
    @PutMapping("/{status}")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置店铺的营业状态为：{}", status == 1 ? "营业中" : "打烊中");

        // 🛠️ 修正：转成 String 存入，迎合 StringRedisSerializer 翻译官
        redisTemplate.opsForValue().set(KEY, status.toString());
        return Result.success();
    }



    /***
     * 获取店铺营业状态
     */
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        // 🛠️ 修正：用 String 强转接收，决不能用 (Integer)
        String statusStr = (String) redisTemplate.opsForValue().get(KEY);

        // 🛠️ 防御性编程：防空指针垫底保护
        if (statusStr == null) {
            log.info("Redis中未检测到状态，默认返回打烊中");
            return Result.success(0);
        }

        // 安全转回数字返回给前端
        Integer status = Integer.valueOf(statusStr);
        log.info("获取店铺的营业状态：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}