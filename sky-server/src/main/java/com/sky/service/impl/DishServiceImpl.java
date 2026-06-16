package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {
    /***
     * 菜品起售停售
     * @param status
     * @param id
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        // 1. 更新当前菜品的状态
        // 利用带有 @Builder 注解的 Dish 实体类，快速构建出只包含 id 和 status 的对象
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        // 直接调用你现有的通用 update 动态 SQL 方法修改状态
        dishMapper.update(dish);

        // 2. 核心业务联动：如果是“停售(0)”操作，需要把包含这个菜品的套餐也一起停售
        if (status == StatusConstant.DISABLE) { // StatusConstant.DISABLE 的值就是 0

            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id); // 把当前修改的菜品id存入集合

            // 调用你之前在批量删除时就已经写好的 getSetmealIdsByDishIds 方法
            // 通过当前菜品 id，去 setmeal_dish 关系表里找出所有关联的套餐 id
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);

            // 如果存在关联的套餐，遍历并把它们也一起停售
            if (setmealIds != null && setmealIds.size() > 0) {
                for (Long setmealId : setmealIds) {
                    // 构建套餐对象，强行将状态也改为停售状态(0)
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();
                    // 调用你现有的通用套餐 update 动态 SQL 修改套餐状态
                    setmealMapper.update(setmeal);
                }
            }
        }
    }

    /***
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> list(Long categoryId) {
        Dish dish=Dish.builder().categoryId(categoryId).status(StatusConstant.ENABLE).build();
        return dishMapper.list(dish);
    }

    /***
     * 根据id修改菜品基本信息和对应口味信息
     * @param dishDTO
     */
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        //修改菜品表基本信息
        Dish dish=new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.update(dish);

        //删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dish.getId());

        //重新插入口味数据
        List<DishFlavor> flavors=dishDTO.getFlavors();
        if(flavors!=null&&flavors.size()>0){
             flavors.forEach(dishFlavor->{
                 dishFlavor.setDishId(dishDTO.getId());
             });
             //向口味表插入多条数据
             dishFlavorMapper.insertBatch(flavors);
        }
    }

    /***
     * 根据id查询对应菜品和口味数据
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        //根据id查询菜品数据
        Dish dish=dishMapper.getById(id);

        //根据id查询口味数据
        List<DishFlavor> dishFlavors= dishFlavorMapper.getByDishId(id);
        //将查询到的数据封装到VO
        DishVO dishVO=new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /***
     * 菜品批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能够删除--是否存在起售中的菜品
        for(Long id:ids){
            Dish dish=dishMapper.getById(id);
            if(dish.getStatus()== StatusConstant.ENABLE){
                //当前菜品处于起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断当前菜品是否能够删除--是否被套餐关联

        List<Long> setmealIds=setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds!=null&&setmealIds.size()>0){
            //当前菜品被套餐关联，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }
        //删除菜品表数据
        /*for(Long id:ids){
            dishMapper.deleteById(id);
            //删除菜品关联的口味数据
            dishFlavorMapper.deleteByDishId(id);
        }
        */
        //根据菜品id集合批量删除菜品数据
        dishMapper.deleteByIds(ids);
        //根据菜品id集合批量删除关联的口味数据
        dishFlavorMapper.deleteByDishIds(ids);
    }

    /***
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
         PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
         Page<DishVO> page=dishMapper.pageQuery(dishPageQueryDTO);
         return new PageResult(page.getTotal(),page.getResult());

    }

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    /***
     * 新增菜品和对应口味
     * @param dishDTO
     */
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        //向菜品表添加1条数据
        dishMapper.insert(dish);
        //获取insert语句生成的主键值
        long dishId=dish.getId();
        //向口味表添加多条数据
        List<DishFlavor> flavors=dishDTO.getFlavors();
        if(flavors!=null&&flavors.size()>0){
            flavors.forEach(dishFlavor->{
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入多条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }


    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
