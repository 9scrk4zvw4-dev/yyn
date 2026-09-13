package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SetmealMapper extends BaseMapper<Setmeal> {

    /**
     * 分页查询（MP分页：Page为第一个参数，其余参数需用@Param别名引用）
     * @param page
     * @param setmealPageQueryDTO
     * @return
     */
    Page<SetmealVO> pageQuery(Page<SetmealVO> page, @Param("dto") SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    List<DishItemVO> getDishItemBySetmealId(Long id);
}
