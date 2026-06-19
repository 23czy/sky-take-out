package com.sky.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.Page;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    /**
     * 订单支付（模拟版本）
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("【模拟支付-Service】生成预支付假数据：{}", ordersPaymentDTO.getOrderNumber());

        // 构造一个飽滿的假VO对象，确保在任何调用 Service 的场景下前端都不会报错
        OrderPaymentVO orderPaymentVO = new OrderPaymentVO();
        orderPaymentVO.setNonceStr("mock_nonce_str_123456");
        orderPaymentVO.setPaySign("mock_pay_sign_abcdefg");
        orderPaymentVO.setSignType("MD5");
        orderPaymentVO.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
        orderPaymentVO.setPackageStr("prepay_id=mock_prepay_id_99999");

        return orderPaymentVO;
    }

    @Autowired
    private WebSocketServer webSocketServer;
    /**
     * 支付成功，修改订单状态（模拟支付的核心落地点）
     *
     * @param outTradeNo 订单号（即你下单时生成的 orders.getNumber()）
     */
    @Override
    @Transactional
    public void paySuccess(String outTradeNo) {
        log.info("【模拟支付-Service】正在推进订单 {} 的数据库状态...", outTradeNo);

        // 1. 根据订单号查询当前数据库中的订单
        // 🚨 避坑提示：请确保你的 orderMapper 里实现了根据订单号查询订单的方法，通常黑马自带的叫 getByNumber
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 2. 只有当订单存在且为“待付款”状态时，才去推进状态（防止重复触发）
        if (ordersDB != null && ordersDB.getStatus().equals(Orders.PENDING_PAYMENT)) {

            Orders orders = new Orders();
            orders.setId(ordersDB.getId());
            // 更新订单状态为：2. 待接单
            orders.setStatus(Orders.TO_BE_CONFIRMED);
            // 更新支付状态为：1. 已支付
            orders.setPayStatus(Orders.PAID);
            // 更新真实的支付完成时间
            orders.setCheckoutTime(LocalDateTime.now());

            // 更新到数据库
            orderMapper.update(orders);
            log.info("【模拟支付-Service】订单 {} 数据库状态已成功更新为：【已支付/待接单】", outTradeNo);

            /* 💡 顺便提醒（后面章节会学到）：
               黑马在后面的“WebSocket 商家通知”和“客户催单”章节中，
               就是在此处（paySuccess方法内部）向商家推送 WebSocket 语音消息的。
               现在我们把它架设好了，后面学到 WebSocket 时，直接把推送代码贴在下面即可！
            */
            // 🔥【核心追加】：通过 WebSocket 向商家端大屏推送“来单提醒”
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("type", 1); // 1 代表来单提醒
            map.put("orderId", ordersDB.getId());
            map.put("content", "订单号：" + outTradeNo);

            // 转换成 JSON 字符串群发给商家端前端（前端收到后会触发语音播报）
            String json = com.alibaba.fastjson.JSON.toJSONString(map);
            webSocketServer.sendToAllClient(json);
            log.info("【WebSocket】已成功群发“来单提醒”消息：{}", json);
        }
    }

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper  addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    /***
     * 用户下单
     * @param ordersSubmitDTO
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常问题（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //查询当前用户购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList=shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList==null||shoppingCartList.size()==0){
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        // 在包含 status, number 等设置的下方，添加地址字符串拼接
        String address = addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail();
        orders.setAddress(address); // 👈 这一行极其重要，必须要加！

        // 随后再执行插入
        orderMapper.insert(orders);


        List<OrderDetail> orderDetailList=new ArrayList<>();
        //向订单明细表插入n条数据
        for(ShoppingCart cart:shoppingCartList ){
            OrderDetail orderDetail=new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //提交订单后清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);
        //封装VO返回结果
        OrderSubmitVO orderSubmitVO=OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 用户端订单分页查询
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 1. 设置 MyBatis PageHelper 分页
        PageHelper.startPage(pageNum, pageSize);

        // 2. 构造查询条件 DTO，强制加入当前登录用户的 ID 隔离数据
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 3. 调用 Mapper 分页查询订单主表 orders
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();

        // 4. 循环订单列表，查询出每个订单对应的菜品明细，并封装入 OrderVO
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId(); // 获取订单主键id

                // 查询该订单下的菜品明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                // 对象拷贝，将 Orders 属性复制到 OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails); // 注入明细列表

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 根据订单id查询订单详情
     */
    @Override
    public OrderVO details(Long id) {
        // 1. 根据id查询订单主表数据
        Orders orders = orderMapper.getById(id);

        // 2. 安全检查：如果订单不存在，直接返回空或报错
        if (orders == null) {
            return null;
        }

        // 3. 根据订单id查询对应的菜品/套餐明细列表
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 4. 将主表数据拷贝到 VO 对象中
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        // 5. 将明细列表注入 VO
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     */
    @Override
    public void userCancelById(Long id) throws Exception {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 2. 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 3. 校验订单状态：只有待付款(1) 和 待接单(2) 状态下用户才能取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException("订单已在处理中，无法取消，请联系商家");
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 4. 判断是否已经付过钱（状态2 或者是 状态1但由于特殊模拟机制已支付）
        // 在苍穹外卖的标准逻辑中，只要 status == 2 (待接单)，就代表一定付过钱了
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            // 原本需要调用微信退款接口:
            // weChatPayUtil.refund(ordersDB.getNumber(), ordersDB.getNumber(), new BigDecimal(0.01), new BigDecimal(0.01));
            // 💡 模拟支付/退款绕过法：直接打印日志，并在数据库中强行把支付状态改为已退款
            log.info("模拟微信退款成功，订单号：{}", ordersDB.getNumber());
            orders.setPayStatus(Orders.REFUND); // 设置支付状态为：2已退款
        }

        // 5. 更新订单状态机：更新状态为已取消(6)，并记录取消原因和取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户主动取消");
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 再来一单
     */
    @Override
    public void repetition(Long id) {
        // 1. 获取当前登录用户id
        Long userId = BaseContext.getCurrentId();

        // 2. 根据旧订单id查询出所有的订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 3. 将订单明细对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单明细中的名字、图片、菜品/套餐ID、口味、金额拷贝过去
            BeanUtils.copyProperties(x, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 4. 将购物车对象批量插入到数据库中
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 商家端订单条件分页查询
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 1. 开启 PageHelper 分页机制
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        // 2. 复用我们在用户端写好的 orderMapper.pageQuery 动态SQL
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 3. 部分重组：因为部分前端页面需要展示“订单菜品信息简写”（例如：宫保鸡丁*2, 可乐*1）
        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                // 获取当前订单的商品明细简写字符串（提取成私有方法，使代码更美观）
                String orderDishes = getOrderDishesStr(orders);
                orderVO.setOrderDishes(orderDishes);

                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 私有辅助方法：根据订单id，将所有明细拼接成：宫保鸡丁*2, 鱼香肉丝*1 这样的字符串
     */
    private String getOrderDishesStr(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        List<String> orderDishList = orderDetailList.stream().map(x -> {
            return x.getName() + "*" + x.getNumber();
        }).collect(Collectors.toList());

        // 用逗号拼接
        return String.join(",", orderDishList);
    }

    /**
     * 各个状态的订单数量统计
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 1. 分别查询出 待接单、待派送（已接单）、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED); // 2
        Integer confirmed = orderMapper.countByStatus(Orders.CONFIRMED);           // 3
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS); // 4

        // 2. 将数量塞入统计对象中
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 商家接单业务实现
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 1. 构造一个用于更新的 Orders 对象，仅传入需要修改的字段
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED) // 将状态强行推进到：3 已接单
                .build();

        // 2. 调用通用的动态更新方法更新数据库
        orderMapper.update(orders);
    }

    /**
     * 商家拒单业务实现
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 2. 状态校验：只有待接单(2)的订单才可以被拒绝
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException("当前订单状态异常，无法拒单");
        }

        // 3. 模拟微信退款
        log.info("拒单触发退款逻辑，订单号：{}，模拟退款金额：{}元", ordersDB.getNumber(), ordersDB.getAmount());

        // 4. 更新订单状态机
        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)               // 状态变更为: 6 已取消
                .payStatus(Orders.REFUND)                // 支付状态变更为: 2 已退款
                .rejectionReason(ordersRejectionDTO.getRejectionReason()) // 记录拒绝原因
                .cancelTime(LocalDateTime.now())         // 记录操作时间
                .build();

        orderMapper.update(orders);
    }

    /**
     * 商家取消订单业务实现
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        // 2. 模拟微信退款判定：如果用户付过钱，强制退款
        if (ordersDB != null && ordersDB.getPayStatus().equals(Orders.PAID)) {
            log.info("商家取消订单，触发模拟退款。订单号：{}", ordersDB.getNumber());
        }

        // 3. 组装更新字段
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)             // 状态变更为: 6 已取消
                .payStatus(Orders.REFUND)              // 支付状态变更为: 2 已退款
                .cancelReason(ordersCancelDTO.getCancelReason()) // 记录取消原因
                .cancelTime(LocalDateTime.now())       // 记录取消时间
                .build();

        orderMapper.update(orders);
    }

    /**
     * 订单派送业务实现
     */
    @Override
    public void delivery(Long id) {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 2. 状态校验：只有状态为 3（已接单）的订单，商户才能点派送
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException("订单状态异常，无法进行派送操作");
        }

        // 3. 组装更新字段：推进状态为 4（派送中）
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 完成订单业务实现
     */
    @Override
    public void complete(Long id) {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 2. 状态校验：只有状态为 4（派送中）的订单才能改成 5（已完成）
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException("订单状态异常，无法完成该订单");
        }

        // 3. 组装更新字段：修改状态为 5，并打上当下的送达时间戳
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now()) // 核心细节：记录送达时间！
                .build();

        orderMapper.update(orders);
    }

    /**
     * 用户端订单催单功能实现
     * * @param id 订单主键ID
     */
    @Override
    public void reminder(Long id) {
        // 1. 根据id查询当前的订单数据
        Orders ordersDB = orderMapper.getById(id);

        // 2. 业务状态校验：订单不存在则抛出异常
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 3. 🔥【核心实现】：通过 WebSocket 向商家端大屏推送“催单提醒”
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("type", 2); // 2 代表催单提醒
        map.put("orderId", id);
        map.put("content", "订单号：" + ordersDB.getNumber());

        // 将 Map 转换为 JSON 字符串群发
        String json = com.alibaba.fastjson.JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
        log.info("【WebSocket】已成功群发“用户催单”消息：{}", json);
    }
}
