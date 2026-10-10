package com.sky.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.entity.Dish;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.mapper.OrderMapper;
import com.sky.service.DishService;
import com.sky.service.OrderService;
import com.sky.service.SetmealService;
import com.sky.vo.DishVO;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Component
public class AiTools {
    @Autowired
    private DishService dishService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private SetmealService setmealService;

    @Tool("查询本店菜品")
    public String listAllDishes () {
        // 复用现有 Service，条件按你想要的范围设置
        Dish condition = new Dish ();
        // condition.setStatus(1);
        // 只看起售菜品就放开这行
        List<DishVO> list = dishService.listWithFlavor(condition);
        if (list.isEmpty()) {
            return "当前没有任何菜品" ;
        }
        return list.stream()
                .map(d -> d.getName() + "  价格:" + d.getPrice()
                        + "  状态:" + (d.getStatus() == 1 ? "在售" : "停售" ))
                .collect(Collectors.joining( "\n" ));
    }

    @Tool("查询本店今日营业额，即今天已完成订单的金额合计，返回金额数字")
    public String todayTurnover() {
        LocalDate today = LocalDate.now();

        Map<String, Object> map = new HashMap<>();
        map.put("status", Orders.COMPLETED);   // 只统计已完成订单
        map.put("begin", LocalDateTime.of(today, LocalTime.MIN));
        map.put("end", LocalDateTime.of(today, LocalTime.MAX));

        Double turnover = orderMapper.sumByMap(map);
        turnover = turnover == null ? 0.0 : turnover;
        return "今日营业额为 " + turnover + " 元";
    }

    @Tool("查询本店今日订单情况，返回今日订单总数以及各状态（待付款、待接单、派送中、已完成、已取消）的订单数量")
    public String todayOrders() {
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .between(Orders::getOrderTime,
                        LocalDateTime.of(today, LocalTime.MIN),
                        LocalDateTime.of(today, LocalTime.MAX));
        List<Orders> orders = orderMapper.selectList(wrapper);

        if (orders.isEmpty()) {
            return "今日暂无订单";
        }

        Map<Integer, Long> countByStatus = new LinkedHashMap<>();
        for (Orders o : orders) {
            countByStatus.merge(o.getStatus(), 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("今日订单总数：").append(orders.size()).append(" 单\n");
        appendCount(sb, "待付款", Orders.PENDING_PAYMENT, countByStatus);
        appendCount(sb, "待接单", Orders.TO_BE_CONFIRMED, countByStatus);
        appendCount(sb, "已确认", Orders.CONFIRMED, countByStatus);
        appendCount(sb, "派送中", Orders.DELIVERY_IN_PROGRESS, countByStatus);
        appendCount(sb, "已完成", Orders.COMPLETED, countByStatus);
        appendCount(sb, "已取消", Orders.CANCELLED, countByStatus);
        return sb.toString().trim();
    }

    private void appendCount(StringBuilder sb, String label, Integer status,
                             Map<Integer, Long> countByStatus) {
        sb.append(label).append("：")
                .append(countByStatus.getOrDefault(status, 0L)).append(" 单\n");
    }

    @Tool("查询本店所有套餐，返回每个套餐的名称、价格和在售状态")
    public String listSetmeals() {
        List<Setmeal> list = setmealService.list(new Setmeal());

        if (list.isEmpty()) {
            return "当前没有任何套餐";
        }

        return list.stream()
                .map(s -> s.getName() + "  价格:" + s.getPrice() + "元"
                        + "  状态:" + (s.getStatus() == 1 ? "在售" : "停售"))
                .collect(Collectors.joining("\n"));
    }
}
