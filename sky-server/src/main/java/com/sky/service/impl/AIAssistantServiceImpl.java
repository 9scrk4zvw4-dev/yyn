package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.entity.Dish;
import com.sky.service.AIAssistantService;
import com.sky.service.CategoryService;
import com.sky.service.DishService;
import com.sky.service.OrderService;
import com.sky.service.WorkspaceService;
import com.sky.utils.DashScopeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI管家服务实现：基于DashScope通义千问，支持Function Calling调用业务接口
 */
@Service
@Slf4j
public class AIAssistantServiceImpl implements AIAssistantService {

    @Autowired
    private DashScopeClient dashScopeClient;
    @Autowired
    private DishService dishService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private WorkspaceService workspaceService;

    // 会话历史：sessionId -> 消息列表
    private final Map<String, List<JSONObject>> sessionHistory = new ConcurrentHashMap<>();

    @Override
    public String chat(String sessionId, String userMessage) {
        // 1. 取出会话历史
        List<JSONObject> messages = sessionHistory.computeIfAbsent(
                sessionId, k -> new ArrayList<>());

        // 2. 首次对话加入系统提示词（定义AI管家人设）
        if (messages.isEmpty()) {
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是苍穹外卖的AI管家，服务于商家管理端。"
                    + "你可以帮商家查询今日经营数据（营业额、订单数、客单价、新增用户）、"
                    + "查询订单状态统计（待接单、待派送、派送中数量）、"
                    + "查询菜品和套餐的起停售情况、"
                    + "查询菜品分类列表、查询所有菜品、按分类ID查询菜品列表。"
                    + "请用简洁的中文回答。需要查询数据时调用相应工具。");
            messages.add(systemMsg);
        }

        // 3. 追加用户消息
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // 4. 构建请求（含工具定义）
        JSONObject requestBody = buildRequestBody(messages);

        // 5. 调用大模型
        String responseStr = dashScopeClient.chatCompletion(requestBody.toJSONString());
        JSONObject response = JSON.parseObject(responseStr);
        JSONObject assistantMsg = response.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message");
        messages.add(assistantMsg);

        // 6. 处理工具调用循环（模型可能连续调用多个工具）
        while (assistantMsg.containsKey("tool_calls")) {
            JSONArray toolCalls = assistantMsg.getJSONArray("tool_calls");
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                JSONObject function = toolCall.getJSONObject("function");
                String funcName = function.getString("name");
                String arguments = function.getString("arguments");

                log.info("AI调用工具: {} 参数: {}", funcName, arguments);

                // 执行工具
                String toolResult = executeTool(funcName, arguments);

                // 工具结果回传
                JSONObject toolMsg = new JSONObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCall.getString("id"));
                toolMsg.put("content", toolResult);
                messages.add(toolMsg);
            }
            // 带工具结果再次请求模型，生成最终回答
            requestBody = buildRequestBody(messages);
            responseStr = dashScopeClient.chatCompletion(requestBody.toJSONString());
            response = JSON.parseObject(responseStr);
            assistantMsg = response.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message");
            messages.add(assistantMsg);
        }

        // 7. 返回最终回答
        return assistantMsg.getString("content");
    }

    /**
     * 构建请求体，含工具定义
     */
    private JSONObject buildRequestBody(List<JSONObject> messages) {
        JSONObject body = new JSONObject();
        body.put("model", dashScopeClient.getModel());
        body.put("messages", messages);
        body.put("tools", buildTools());
        return body;
    }

    /**
     * 定义AI可调用的工具（Function Calling）
     */
    private JSONArray buildTools() {
        JSONArray tools = new JSONArray();

        // 查询今日经营数据
        tools.add(buildTool("getBusinessData",
                "查询今日经营数据，包括营业额、有效订单数、订单完成率、客单价、新增用户数",
                "{\"type\":\"object\",\"properties\":{}}"));

        // 查询今日订单概览
        tools.add(buildTool("getOrderOverview",
                "查询今日各状态订单数量（待接单、待派送、派送中、已完成、已取消）",
                "{\"type\":\"object\",\"properties\":{}}"));

        // 查询菜品总览
        tools.add(buildTool("getDishOverview",
                "查询菜品起售和停售的数量统计",
                "{\"type\":\"object\",\"properties\":{}}"));

        // 查询套餐总览
        tools.add(buildTool("getSetmealOverview",
                "查询套餐起售和停售的数量统计",
                "{\"type\":\"object\",\"properties\":{}}"));

        // 按分类查菜品
        tools.add(buildTool("listDishByCategory",
                "根据分类ID查询该分类下的菜品列表",
                "{\"type\":\"object\",\"properties\":{\"categoryId\":{\"type\":\"integer\",\"description\":\"菜品分类ID\"}},\"required\":[\"categoryId\"]}"));

        // 获取所有菜品分类
        tools.add(buildTool("listAllCategories",
                "查询所有菜品分类列表，返回分类ID和名称",
                "{\"type\":\"object\",\"properties\":{}}"));

        // 查询所有菜品
        tools.add(buildTool("listAllDishes",
                "查询所有菜品列表（不分分类），包含菜品名称、价格、状态等信息",
                "{\"type\":\"object\",\"properties\":{}}"));

        // 查订单状态统计
        tools.add(buildTool("getOrderStatistics",
                "查询订单状态统计：待接单、待派送、派送中的数量",
                "{\"type\":\"object\",\"properties\":{}}"));

        return tools;
    }

    private JSONObject buildTool(String name, String desc, String paramsJson) {
        JSONObject tool = new JSONObject();
        JSONObject function = new JSONObject();
        function.put("name", name);
        function.put("description", desc);
        function.put("parameters", JSON.parseObject(paramsJson));
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    /**
     * 执行工具——调用已有业务方法
     */
    private String executeTool(String funcName, String arguments) {
        JSONObject args = JSON.parseObject(arguments);
        try {
            switch (funcName) {
                case "getBusinessData":
                    return JSON.toJSONString(workspaceService.getBusinessData(
                            LocalDateTime.now().with(LocalTime.MIN),
                            LocalDateTime.now().with(LocalTime.MAX)));
                case "getOrderOverview":
                    return JSON.toJSONString(workspaceService.getOrderOverView());
                case "getDishOverview":
                    return JSON.toJSONString(workspaceService.getDishOverView());
                case "getSetmealOverview":
                    return JSON.toJSONString(workspaceService.getSetmealOverView());
                case "listDishByCategory":
                    return JSON.toJSONString(dishService.list(args.getLong("categoryId")));
                case "listAllCategories":
                    return JSON.toJSONString(categoryService.list(1));
                case "listAllDishes":
                    return JSON.toJSONString(dishService.listWithFlavor(new Dish()));
                case "getOrderStatistics":
                    return JSON.toJSONString(orderService.statistics());
                default:
                    return "未知工具：" + funcName;
            }
        } catch (Exception e) {
            log.error("工具执行失败: {}", funcName, e);
            return "工具执行失败：" + e.getMessage();
        }
    }
}
