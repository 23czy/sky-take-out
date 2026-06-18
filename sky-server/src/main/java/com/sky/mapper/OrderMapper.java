package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;


@Mapper
public interface OrderMapper {
    /***
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    // 在 OrderMapper 中确保有此方法
    @Select("select * from orders where number = #{outTradeNo}")
    Orders getByNumber(String outTradeNo);

    /**
     * 根据 id 动态修改订单数据
     * @param orders
     */
    void update(Orders orders);
}
