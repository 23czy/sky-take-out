package com.sky.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.Page;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
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
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
}
