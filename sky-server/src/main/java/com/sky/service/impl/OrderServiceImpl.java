package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper,Orders> implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.amap.key}")
    private String amapKey;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
//        异常情况的处理（收货地址为空、超出配送氛围、购物车为空）
        AddressBook addressBook = addressBookMapper.selectById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

//        检查用户的收货地址是否超出配送范围
        checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        Long currentId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
//        只能查询当前用户数据
        shoppingCart.setUserId(currentId);

//        查询当前用户的购物车数据
        LambdaQueryWrapper<ShoppingCart> wrapper=new LambdaQueryWrapper();
        if(shoppingCart.getName()!=null){
            wrapper.eq(ShoppingCart::getName,shoppingCart.getName());
        };
        if(shoppingCart.getDishId()!=null){
            wrapper.eq(ShoppingCart::getDishId,shoppingCart.getDishId());
        };
        if(shoppingCart.getSetmealId()!=null){
            wrapper.eq(ShoppingCart::getSetmealId,shoppingCart.getSetmealId());
        };
        if(shoppingCart.getDishFlavor()!=null){
            wrapper.eq(ShoppingCart::getDishFlavor,shoppingCart.getDishFlavor());
        };
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.selectList(wrapper);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setUserId(currentId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        orderMapper.insert(order);

//        订单明细数据
        ArrayList<OrderDetail> orderDetailList = new ArrayList<>();
        shoppingCartList.forEach(cart->{
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        });

//        向明细表中插入n条数据
        orderDetailList.forEach(orderDetailMapper::insert);

//        清理购物车中的数据
        LambdaQueryWrapper<ShoppingCart> wrapperr=new LambdaQueryWrapper();
        wrapperr.eq(ShoppingCart::getUserId,shoppingCart.getUserId());
        shoppingCartMapper.delete(wrapper);

//        封装返回结果
        OrderSubmitVO submitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return submitVO;
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     * @throws Exception
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
//        当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.selectById(String.valueOf(userId));

        String orderNumber = ordersPaymentDTO.getOrderNumber();
        Orders orders = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getNumber, orderNumber)
                .eq(Orders::getUserId, userId));

        // TODO: 临时跳过真实微信支付，直接调用paySuccess让订单变为已支付状态
        try {
            JSONObject jsonObject = weChatPayUtil.pay(
                    ordersPaymentDTO.getOrderNumber(),
                    orders.getAmount(),
                    "苍穹外卖订单" + orders.getId(),
                    user.getOpenid()
            );

            if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
                throw new OrderBusinessException("该订单已支付");
            }

            OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
            vo.setPackageStr(jsonObject.getString("package"));
            // 真实支付成功后由微信回调paySuccess，这里仅返回支付参数
            return vo;
        } catch (Exception e) {
            // 微信支付文件不存在等异常，直接模拟支付成功
            log.warn("微信支付调用失败，模拟支付成功: {}", e.getMessage());
            // 直接把订单变为已支付状态
            paySuccess(ordersPaymentDTO.getOrderNumber());

            OrderPaymentVO vo = new OrderPaymentVO();
            vo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
            vo.setNonceStr("mock_nonce_str_123456");
            vo.setSignType("RSA");
            vo.setPaySign("mock_pay_sign");
            vo.setPackageStr("prepay_id=wx_mock_prepay_id");
            return vo;
        }
    }

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    @Override
    public void paySuccess(String outTradeNo) {
//        当前登录用户id
        Long userId = BaseContext.getCurrentId();

//        根据订单号查询当前用户的订单
        Orders orderDB = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getNumber, outTradeNo)
                .eq(Orders::getUserId, userId));

//        根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.updateById(orders);

        HashMap map = new HashMap();
        map.put("type", 1);
        map.put("orderId", orders.getId());
        map.put("content", "订单号：" + outTradeNo);

//        通过WebSocket实现来电提醒，向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

    }

    /**
     * 历史订单查询
     *
     * @param pageNum
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @Override
    public PageResult pageQueryForUser(int pageNum, int pageSize, Integer status) {
//        MP分页
        Page<Orders> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getUserId, BaseContext.getCurrentId());
        wrapper.eq(status != null, Orders::getStatus, status);
        wrapper.orderByDesc(Orders::getOrderTime);

        orderMapper.selectPage(page, wrapper);

        ArrayList<OrderVO> list = new ArrayList<>();

//        查询出订单明细，并封装入OrderVo进行响应
        if (page.getTotal() > 0) {
            page.getRecords().forEach(orders -> {
                Long ordersId = orders.getId();

//                查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, ordersId));

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            });
        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
//        根据id查询订单
        Orders orders = orderMapper.selectById(id);
        if (orders == null) {
            return null;
        }

//        查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, id));

//        将订单及其详情封装到OrderVo并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void userCancelById(Long id) throws Exception {
//        根据id查询订单
        Orders orderDB = orderMapper.selectById(id);

//        校验订单是否存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

//      订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orderDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

//        订单处于待接单的状态下取消，需要进行退款
        if (orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
//            调用微信支付退款接口
            weChatPayUtil.refund(
                    orderDB.getNumber(),
                    orderDB.getNumber(),
                    orders.getAmount(),
                    orders.getAmount()
            );

//            支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

//        更新订单状态，取消原因、时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(orders);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
//        查询当前用户id
        Long userId = BaseContext.getCurrentId();

//        根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, id));

//        将订单详情对象转为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();

//            将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

//        将购物车对象批量添加到购物车
        shoppingCartList.forEach(shoppingCartMapper::insert);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        Page<Orders> page = new Page<>(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ordersPageQueryDTO.getNumber() != null && !ordersPageQueryDTO.getNumber().isEmpty(),
                Orders::getNumber, ordersPageQueryDTO.getNumber());
        wrapper.like(ordersPageQueryDTO.getPhone() != null && !ordersPageQueryDTO.getPhone().isEmpty(),
                Orders::getPhone, ordersPageQueryDTO.getPhone());
        wrapper.eq(ordersPageQueryDTO.getUserId() != null, Orders::getUserId, ordersPageQueryDTO.getUserId());
        wrapper.eq(ordersPageQueryDTO.getStatus() != null, Orders::getStatus, ordersPageQueryDTO.getStatus());
        wrapper.ge(ordersPageQueryDTO.getBeginTime() != null, Orders::getOrderTime, ordersPageQueryDTO.getBeginTime());
        wrapper.le(ordersPageQueryDTO.getEndTime() != null, Orders::getOrderTime, ordersPageQueryDTO.getEndTime());
        wrapper.orderByDesc(Orders::getOrderTime);

        orderMapper.selectPage(page, wrapper);

//        部分订单状态，需要额外返回订单菜品信息，将orders转化为orderVo
        List<OrderVO> orderVoList = getOrderVoList(page.getRecords());
        return new PageResult(page.getTotal(), orderVoList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
//        根据状态，分别查询出接待单，待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.selectCount(new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, Orders.TO_BE_CONFIRMED)).intValue();
        Integer confirmed = orderMapper.selectCount(new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, Orders.CONFIRMED)).intValue();
        Integer deliveryInProgress = orderMapper.selectCount(new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, Orders.DELIVERY_IN_PROGRESS)).intValue();

//        将查询出的数据封装
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersCancelDTO
     */
    @Override
    public void confirm(OrdersCancelDTO ordersCancelDTO) {
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.updateById(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
//        根据id查询订单
        Orders ordersDB = orderMapper.selectById(ordersRejectionDTO.getId());

//        订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

//        支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
//            用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount()
            );
            log.info("申请退款：{}", refund);
        }

//        拒单需要退款，根据订单id更新订单状态，拒单原因，取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.updateById(orders);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
//        根据id查询订单
        Orders orderDB = orderMapper.selectById(ordersCancelDTO.getId());

//        支付状态
        Integer payStatus = orderDB.getPayStatus();
        if (payStatus == 1) {
//            用于已支付，需要退款
            String refund = weChatPayUtil.refund(
                    orderDB.getNumber(),
                    orderDB.getNumber(),
                    orderDB.getAmount(),
                    orderDB.getAmount()
            );
            log.info("申请退款：{}", refund);
        }

//      管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(orders);
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
//        根据id查询订单
        Orders orderDB = orderMapper.selectById(id);

//        校验订单是否存在，并且状态为3
        if (orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

//        更新订单状态，状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.updateById(orders);
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
//        根据id查询订单
        Orders orderDB = orderMapper.selectById(id);

//        校验订单是否存在，并且状态为4
        if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(id);

//        更新订单状态，状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.updateById(orders);
    }

    /**
     * 用户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
//        查询订单是否存在
        Orders orders = orderMapper.selectById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

//        基于WebSocket实现催单
        HashMap map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号：" + orders.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 部分订单状态，需要额外返回订单菜品信息，将orders转化为orderVo
     * @param page
     * @return
     */
    private List<OrderVO> getOrderVoList(List<Orders> ordersList) {
//        需要返回订单菜品信息，自定义OrderVo响应结果
        ArrayList<OrderVO> orderVOArrayList = new ArrayList<>();

        if (!CollectionUtils.isEmpty(ordersList)) {
            ordersList.forEach(orders -> {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                String orderDishStr = getOrderDishStr(orders);
                orderVO.setOrderDishes(orderDishStr);
                orderVOArrayList.add(orderVO);
            });
        }
        return orderVOArrayList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishStr(Orders orders) {
//        查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orders.getId()));

//        将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> ordewrDishList = orderDetailList.stream().map(orderDetail -> orderDetail.getName() + "*" + orderDetail.getNumber() + ";").collect(Collectors.toList());

//        将该订单对应的所有菜品信息拼接在一起
        return String.join("", ordewrDishList);
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        // TODO: 临时跳过地图校验，测试完后恢复
        /*
        HashMap map = new HashMap();
        map.put("address", shopAddress);
        map.put("key", amapKey);

//        获取店铺的经纬度坐标（高德地理编码）
        String shopCoordinate = HttpClientUtil.doGet("https://restapi.amap.com/v3/geocode/geo", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (!"1".equals(jsonObject.getString("status"))) {
            throw new OrderBusinessException("店铺地址解析失败: " + jsonObject.getString("info"));
        }

//        数据解析：高德 location 返回格式为 "lng,lat"
        JSONArray geocodes = jsonObject.getJSONArray("geocodes");
        String shopLocation = geocodes.getJSONObject(0).getString("location");

        map.put("address", address);

//        获取用户地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://restapi.amap.com/v3/geocode/geo", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if (!"1".equals(jsonObject.getString("status"))) {
            throw new OrderBusinessException("用户地址解析失败: " + jsonObject.getString("info"));
        }

        geocodes = jsonObject.getJSONArray("geocodes");
        String userLocation = geocodes.getJSONObject(0).getString("location");

//        路线规划（高德驾车路径规划）
        HashMap routeMap = new HashMap();
        routeMap.put("origin", shopLocation);
        routeMap.put("destination", userLocation);
        routeMap.put("key", amapKey);

        String routeJson = HttpClientUtil.doGet("https://restapi.amap.com/v3/direction/driving", routeMap);

        JSONObject routeObject = JSON.parseObject(routeJson);
        if (!"1".equals(routeObject.getString("status"))) {
            throw new OrderBusinessException("配送线路规划失败: " + routeObject.getString("info"));
        }

//        数据解析
        JSONObject route = routeObject.getJSONObject("route");
        JSONArray paths = route.getJSONArray("paths");
        Integer distance = Integer.parseInt(paths.getJSONObject(0).getString("distance"));

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
        */
    }
}
