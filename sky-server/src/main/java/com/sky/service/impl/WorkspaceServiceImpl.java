package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.entity.User;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        /**
         * 营业额：当日已完成订单的总金额
         * 有效订单：当日已完成订单的数量
         * 订单完成率：有效订单数 / 总订单数
         * 平均客单价：营业额 / 有效订单数
         * 新增用户：当日新增用户的数量
         */

        //查询总订单数（时间段内所有订单）
        Long totalOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .ge(Orders::getOrderTime, begin)
                .le(Orders::getOrderTime, end));

        //营业额（时间段内已完成订单的总金额）
        Map map = new HashMap();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status", Orders.COMPLETED);
        Double turnover = orderMapper.sumByMap(map);
        turnover = turnover == null? 0.0 : turnover;

        //有效订单数（时间段内已完成订单）
        Long validOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, Orders.COMPLETED)
                .ge(Orders::getOrderTime, begin)
                .le(Orders::getOrderTime, end));

        Double unitPrice = 0.0;

        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0 && validOrderCount != 0){
            //订单完成率
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            //平均客单价
            unitPrice = turnover / validOrderCount;
        }

        //新增用户数
        Integer newUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, begin)
                .le(User::getCreateTime, end)).intValue();

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount.intValue())
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据
     *
     * @return
     */
    public OrderOverViewVO getOrderOverView() {
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);

        //待接单
        Integer waitingOrders = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, Orders.TO_BE_CONFIRMED)
                .ge(Orders::getOrderTime, begin)).intValue();

        //待派送
        Integer deliveredOrders = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, Orders.CONFIRMED)
                .ge(Orders::getOrderTime, begin)).intValue();

        //已完成
        Integer completedOrders = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, Orders.COMPLETED)
                .ge(Orders::getOrderTime, begin)).intValue();

        //已取消
        Integer cancelledOrders = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, Orders.CANCELLED)
                .ge(Orders::getOrderTime, begin)).intValue();

        //全部订单（今日）
        Integer allOrders = orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .ge(Orders::getOrderTime, begin)).intValue();

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览
     *
     * @return
     */
    public DishOverViewVO getDishOverView() {
        Integer sold = dishMapper.selectCount(new LambdaQueryWrapper<Dish>().eq(Dish::getStatus, StatusConstant.ENABLE)).intValue();
        Integer discontinued = dishMapper.selectCount(new LambdaQueryWrapper<Dish>().eq(Dish::getStatus, StatusConstant.DISABLE)).intValue();

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    /**
     * 查询套餐总览
     *
     * @return
     */
    public SetmealOverViewVO getSetmealOverView() {
        Integer sold = setmealMapper.selectCount(new LambdaQueryWrapper<Setmeal>().eq(Setmeal::getStatus, StatusConstant.ENABLE)).intValue();
        Integer discontinued = setmealMapper.selectCount(new LambdaQueryWrapper<Setmeal>().eq(Setmeal::getStatus, StatusConstant.DISABLE)).intValue();

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}