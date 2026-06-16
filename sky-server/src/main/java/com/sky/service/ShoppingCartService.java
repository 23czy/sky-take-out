package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.mapper.ShoppingCartMapper;
import org.springframework.beans.factory.annotation.Autowired;

public interface ShoppingCartService {

    /***
     * 添加购物车
     * @param shoppingCartDTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

}
