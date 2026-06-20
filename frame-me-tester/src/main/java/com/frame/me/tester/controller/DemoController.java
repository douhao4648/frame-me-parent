package com.frame.me.tester.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.api.result.IResult;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.tester.entity.DemoEntity;
import com.frame.me.tester.mapper.DemoMapper;
import com.frame.me.tester.query.DemoQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 演示 Controller，提供完整的增删改查及分页查询接口.
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoMapper demoMapper;

    /**
     * 查询演示数据列表.
     *
     * @return 演示数据列表
     */
    @GetMapping("/list")
    public IResult<List<DemoEntity>> list() {
        return Result.success(demoMapper.selectList(null));
    }

    /**
     * 分页查询演示数据.
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping("/page")
    public IResult<Page<DemoEntity>> page(DemoQuery query) {
        Page<DemoEntity> page = query.toPage();
        QueryWrapper<DemoEntity> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getName()), "name", query.getName());
        wrapper.eq(query.getAge() != null, "age", query.getAge());
        return Result.success(demoMapper.selectPage(page, wrapper));
    }

    /**
     * 根据 ID 查询演示数据.
     *
     * @param id 数据 ID
     * @return 演示数据
     */
    @GetMapping("/{id}")
    public IResult<DemoEntity> getById(@PathVariable Long id) {
        DemoEntity entity = demoMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        return Result.success(entity);
    }

    /**
     * 插入演示数据.
     *
     * @param demoEntity 演示实体
     * @return 新增数据 ID
     */
    @PostMapping
    public IResult<Long> create(@RequestBody DemoEntity demoEntity) {
        demoMapper.insert(demoEntity);
        return Result.success(demoEntity.getId());
    }

    /**
     * 根据 ID 更新演示数据.
     *
     * @param id         数据 ID
     * @param demoEntity 演示实体，需包含 version 用于乐观锁控制
     * @return 是否更新成功
     */
    @PutMapping("/{id}")
    public IResult<Boolean> update(@PathVariable Long id, @RequestBody DemoEntity demoEntity) {
        DemoEntity exist = demoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        if (demoEntity.getVersion() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "更新时 version 不能为空");
        }
        demoEntity.setId(id);
        int rows = demoMapper.updateById(demoEntity);
        return Result.success(rows > 0);
    }

    /**
     * 根据 ID 删除演示数据（逻辑删除）.
     *
     * @param id 数据 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    public IResult<Boolean> delete(@PathVariable Long id) {
        DemoEntity exist = demoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        int rows = demoMapper.deleteById(id);
        return Result.success(rows > 0);
    }
}
