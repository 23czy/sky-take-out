package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.ShoppingCartMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public interface ShoppingCartService {

    /***
     * 添加购物车
     * @param shoppingCartDTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /***
     * 查看购物车
     * @return
     */
    List<ShoppingCart> showShoppingCart();

    /***
     * 清空购物车
     */
    void cleanShoppingCart();
}
