package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /***
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO  ordersSubmitDTO){
        log.info("用户下单，参数为：{}",ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO= orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付（模拟微信支付版本）
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("【模拟支付】接收到支付请求，订单号为：{}", ordersPaymentDTO.getOrderNumber());

        // 1. 核心偷梁换柱：直接调用后端原生的“支付成功”业务方法
        // 该方法会把数据库订单状态改为 2 (待接单)，更新支付时间，并通过 WebSocket 向商家后台弹窗播报语音
        orderService.paySuccess(ordersPaymentDTO.getOrderNumber());
        log.info("【模拟支付】已成功将订单 {} 的状态修改为：待接单", ordersPaymentDTO.getOrderNumber());

        // 2. 伪造一个饱满的 OrderPaymentVO 对象返回给前端小程序，防止前端因为拿到空字段或 null 而闪退报错
        OrderPaymentVO orderPaymentVO = new OrderPaymentVO();
        orderPaymentVO.setNonceStr("mock_nonce_str_123456");
        orderPaymentVO.setPaySign("mock_pay_sign_abcdefg");
        orderPaymentVO.setSignType("MD5");
        orderPaymentVO.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
        orderPaymentVO.setPackageStr("prepay_id=mock_prepay_id_99999");

        log.info("【模拟支付】伪造的支付参数封装完毕，准备下发给前端：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单查询
     * @param page 页码
     * @param pageSize 每页记录数
     * @param status 订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return 分页结果封装
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        PageResult pageResult = orderService.pageQuery4User(page, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 根据订单id查询订单详情
     *
     * @param id 订单id
     * @return 包含订单主表及明细的VO对象
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }
}
