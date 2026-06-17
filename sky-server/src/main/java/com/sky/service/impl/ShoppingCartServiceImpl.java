package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 1. 创建一个空购物车对象，用于封装查询条件
        ShoppingCart shoppingCart = new ShoppingCart();

        // 2. 将前端传来的 DTO 属性（dishId, setmealId, dishFlavor）拷贝到实体中
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        // 3. 关键安全细节：强制绑定当前登录用户的 ID
        shoppingCart.setUserId(BaseContext.getCurrentId());

        // 4. 去数据库里查：当前用户这个唯一的菜品/套餐记录
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        // 5. 防御性编程：确保查出来的确有这条购物车数据
        if(list != null && list.size() > 0){
            // 获取这一条唯一的购物车商品数据
            shoppingCart = list.get(0);

            // 6. 核心业务分水岭：检查当前购物车的数量
            Integer number = shoppingCart.getNumber();

            if(number == 1){
                // 情况 A：数量刚好等于 1，再减就变成 0 了。
                // 此时应该直接在数据库中“抹除（DELETE）”这条商品记录！
                shoppingCartMapper.deleteById(shoppingCart.getId());
            } else {
                // 情况 B：数量大于 1（比如原先购物车里有 3 份米饭）。
                // 此时不能删记录，只需要执行“数量 - 1”并修改（UPDATE）数据库即可
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            }
        }
    }

    /***
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        //获取当前微信用户的id
        Long userId=BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /***
     * 查看购物车
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        //获取当前微信用户的id
        Long userId=BaseContext.getCurrentId();
        ShoppingCart shoppingCart=ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> list= shoppingCartMapper.list(shoppingCart);
        return list;
    }

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper  dishMapper;

    @Autowired
    private SetmealMapper  setmealMapper;


    /***
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //判断当前加入购物车中的商品是否已经存在
        ShoppingCart  shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId= BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list= shoppingCartMapper.list(shoppingCart);
        //若已存在，只需将数量+1
        if(list!=null&&list.size()>0){
            ShoppingCart cart=list.get(0);
            cart.setNumber(cart.getNumber()+1);
            shoppingCartMapper.updateNumberById(cart);
        }else{
            //若不存在，需插入一条数据

            //判断本次添加到购物车的是菜品还是套餐
            Long dishId=shoppingCartDTO.getDishId();
            if(dishId!=null){
                //本次添加到购物车的是菜品
                Dish dish=dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }else{
                //本次添加到购物车是套餐
                Long setmealId=shoppingCartDTO.getSetmealId();

                Setmeal setmeal=setmealMapper.getById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }

    }
}
