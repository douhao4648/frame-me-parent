package com.frame.me.tester.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageResult;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.mybatis.util.PageUtils;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.tester.api.IDemoApi;
import com.frame.me.tester.api.dto.DemoDTO;
import com.frame.me.tester.api.query.DemoQuery;
import com.frame.me.tester.api.vo.DemoVO;
import com.frame.me.tester.convert.DemoConvert;
import com.frame.me.tester.entity.DemoEntity;
import com.frame.me.tester.mapper.DemoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 演示 Controller，提供完整的增删改查及分页查询接口.
 */
@RestController
@RequiredArgsConstructor
public class DemoController implements IDemoApi {

    private final DemoMapper demoMapper;
    private final DemoConvert demoConvert;

    @Override
    public IResult<List<DemoVO>> list() {
        List<DemoEntity> entities = demoMapper.selectList(null);
        return Result.success(demoConvert.toVoList(entities));
    }

    @Override
    public IResult<PageResult<DemoVO>> page(DemoQuery query) {
        Page<DemoEntity> page = PageUtils.toPage(query, "create_time:desc");

        QueryWrapper<DemoEntity> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getName()), "name", query.getName());
        wrapper.eq(query.getAge() != null, "age", query.getAge());

        return Result.success(PageUtils.toResult(demoMapper.selectPage(page, wrapper), demoConvert::toVo));
    }

    @Override
    public IResult<DemoVO> getById(@PathVariable Long id) {
        DemoEntity entity = demoMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        return Result.success(demoConvert.toVo(entity));
    }

    @Override
    public IResult<Long> create(@RequestBody DemoDTO dto) {
        DemoEntity entity = demoConvert.toEntity(dto);
        demoMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @Override
    public IResult<Boolean> update(@PathVariable Long id, @RequestBody DemoDTO dto) {
        DemoEntity exist = demoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        if (dto.getVersion() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "更新时 version 不能为空");
        }
        DemoEntity entity = demoConvert.toEntity(dto);
        entity.setId(id);
        int rows = demoMapper.updateById(entity);
        return Result.success(rows > 0);
    }

    @Override
    public IResult<Boolean> delete(@PathVariable Long id) {
        DemoEntity exist = demoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        int rows = demoMapper.deleteById(id);
        return Result.success(rows > 0);
    }
}
