package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        //1.保存套餐数据
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.insert(setmeal);

        //2.保存套餐和菜品的关联关系
        Long setmealId = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if(setmealDishes != null && !setmealDishes.isEmpty()) {
            for (SetmealDish setmealDish : setmealDishes) {
                setmealDish.setSetmealId(setmealId);
            }
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 分页查询套餐
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> setmealVOPage = setmealMapper.pageQuery(setmealPageQueryDTO);

        return new PageResult(setmealVOPage.getTotal(),setmealVOPage.getResult());
    }

    /**
     * 批量删除套餐
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //1.判断当前套餐是否处于启售状态，是则无法删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if(Objects.equals(setmeal.getStatus(), StatusConstant.ENABLE)) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //2.删除套餐
        setmealMapper.deleteBatch(ids);

        //3.删除套餐和菜品的关联关系
        setmealDishMapper.deleteBySetmealId(ids);
    }


    /**
     * 根据id查询套餐
     */
    @Override
    public SetmealVO getById(Long id) {
        //1.根据id查询套餐数据
        Setmeal setmeal = setmealMapper.getById(id);

        //2.根据套餐id查询套餐和菜品的关联关系
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);

        //3.将两个数据封装到VO中并返回
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐
     */
    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);

        //删除套餐和菜品的关联关系
        setmealDishMapper.deleteBySetmealId(Collections.singletonList(setmealDTO.getId()));

        //重新插入新的关联关系
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if(setmealDishes != null && !setmealDishes.isEmpty()) {
            for (SetmealDish setmealDish : setmealDishes) {
                setmealDish.setSetmealId(setmealDTO.getId());
            }
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    @Override
    @Transactional
    public void SetmealstartOrStop(Integer status, Long id) {
        //- 可以对状态为起售的套餐进行停售操作，可以对状态为停售的套餐进行起售操作
        //- 起售的套餐可以展示在用户端，停售的套餐不能展示在用户端
        //- 起售套餐时，如果套餐内包含停售的菜品，则不能起售
        //1.先判断是否允许起售（如果是起售操作）
        if(Objects.equals(status, StatusConstant.ENABLE)) {
            //如果将套餐设置为起售状态，则需要判断套餐内包含的菜品是否都为起售状态
            //获得套餐内包含的菜品的 id
            List<Dish> dishIds = setmealDishMapper.getDishBySetmealId(id);
            if(dishIds != null && !dishIds.isEmpty()) {
                for (Dish dish : dishIds) {
                    //遍历菜品，获得每个菜品的状态，判断菜品是否都处于起售状态
                    if(!Objects.equals(dish.getStatus(), StatusConstant.ENABLE)) {
                        //如果有任何一个菜品未起售，则抛出异常
                        throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                }
            }
        }

        //2.所有检查通过后，再更新套餐状态
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
