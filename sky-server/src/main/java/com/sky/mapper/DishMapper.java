package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 菜品分页查询
     * @param page MP分页对象
     * @param dishPageQueryDTO 查询条件
     * @return
     */
    Page<DishVO> pageQuery(Page<DishVO> page, @Param("dto") DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据套餐id查询菜品
     * @param id
     * @return
     */
    List<Dish> getBySetmealId(Long id);
}
