package com.frame.me.tester.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.api.result.PageResult;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.mybatis.util.PageUtils;
import com.frame.me.base.result.ResultCode;
import com.frame.me.tester.api.dto.DemoDTO;
import com.frame.me.tester.api.query.DemoComplexQuery;
import com.frame.me.tester.api.query.DemoQuery;
import com.frame.me.tester.api.vo.DemoComplexVO;
import com.frame.me.tester.api.vo.DemoVO;
import com.frame.me.tester.entity.DemoEntity;
import com.frame.me.tester.mapper.DemoMapper;
import com.frame.me.tester.service.IDemoService;
import com.frame.me.tester.service.convert.DemoConvert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Demo 业务服务实现.
 */
@Service
@RequiredArgsConstructor
public class DemoServiceImpl implements IDemoService {

    private final DemoMapper demoMapper;
    private final DemoConvert demoConvert;

    @Override
    public List<DemoVO> list() {
        List<DemoEntity> entities = demoMapper.selectList(null);
        return demoConvert.toVoList(entities);
    }

    @Override
    public PageResult<DemoVO> page(DemoQuery query) {
        Page<DemoEntity> page = PageUtils.toPage(query, "create_time:desc");

        QueryWrapper<DemoEntity> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getName()), "name", query.getName());
        wrapper.eq(query.getAge() != null, "age", query.getAge());

        return PageUtils.toResult(demoMapper.selectPage(page, wrapper), demoConvert::toVo);
    }

    @Override
    public List<DemoComplexVO> complexList(DemoComplexQuery query) {
        return demoMapper.selectComplexList(
                query.getMinAge(),
                query.getMaxAge(),
                query.getStartTime(),
                query.getEndTime()
        );
    }

    @Override
    public DemoVO getById(Long id) {
        DemoEntity entity = demoMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        return demoConvert.toVo(entity);
    }

    @Override
    public Long create(DemoDTO dto) {
        DemoEntity entity = demoConvert.toEntity(dto);
        demoMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(Long id, DemoDTO dto) {
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
        return rows > 0;
    }

    @Override
    public Boolean delete(Long id) {
        DemoEntity exist = demoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        int rows = demoMapper.deleteById(id);
        return rows > 0;
    }
}
