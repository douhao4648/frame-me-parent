package com.frame.me.tester.service.impl;

import cn.hutool.core.util.StrUtil;
import com.frame.me.api.result.PageData;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.mybatis.flex.util.PageUtils;
import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.api.query.FlexDemoQuery;
import com.frame.me.tester.api.vo.FlexDemoVO;
import com.frame.me.tester.entity.FlexDemoEntity;
import com.frame.me.tester.mapper.FlexDemoMapper;
import com.frame.me.tester.service.IFlexDemoService;
import com.frame.me.tester.service.convert.FlexDemoConvert;
import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Flex 演示业务实现.
 *
 * <p>{@link #createInSecond} 与 {@link #countInSecond} 通过 MyBatis-Flex 原生
 * {@code @UseDataSource("second")} 切换到 second 数据源，其余方法走默认 master 数据源。</p>
 */
@Service
@RequiredArgsConstructor
public class FlexDemoServiceImpl implements IFlexDemoService {

    private final FlexDemoMapper flexDemoMapper;
    private final FlexDemoConvert flexDemoConvert;

    @Override
    public List<FlexDemoVO> list() {
        return flexDemoConvert.toVoList(flexDemoMapper.selectAll());
    }

    @Override
    public PageData<FlexDemoVO> page(FlexDemoQuery query) {
        Page<FlexDemoEntity> page = flexDemoMapper.paginate(PageUtils.toPage(query), buildWrapper(query));
        return PageUtils.toPageData(page, flexDemoConvert::toVo);
    }

    @Override
    public FlexDemoVO getById(Long id) {
        FlexDemoEntity entity = flexDemoMapper.selectOneById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        return flexDemoConvert.toVo(entity);
    }

    @Override
    public Long create(FlexDemoDTO dto) {
        FlexDemoEntity entity = flexDemoConvert.toEntity(dto);
        flexDemoMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @UseDataSource("second")
    public Long createInSecond(FlexDemoDTO dto) {
        FlexDemoEntity entity = flexDemoConvert.toEntity(dto);
        flexDemoMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(Long id, FlexDemoDTO dto) {
        FlexDemoEntity exist = flexDemoMapper.selectOneById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        FlexDemoEntity entity = flexDemoConvert.toEntity(dto);
        entity.setId(id);
        entity.setVersion(dto.getVersion());
        return flexDemoMapper.update(entity) > 0;
    }

    @Override
    public Boolean delete(Long id) {
        FlexDemoEntity exist = flexDemoMapper.selectOneById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "数据 {} 不存在", id);
        }
        return flexDemoMapper.deleteById(id) > 0;
    }

    @Override
    public long count() {
        return flexDemoMapper.selectCountByQuery(QueryWrapper.create());
    }

    @Override
    @UseDataSource("second")
    public long countInSecond() {
        return flexDemoMapper.selectCountByQuery(QueryWrapper.create());
    }

    @Override
    public List<FlexDemoVO> listByXml(FlexDemoQuery query) {
        return flexDemoMapper.listByXml(query);
    }

    private QueryWrapper buildWrapper(FlexDemoQuery query) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.and(new QueryColumn("name").like(query.getName()));
        }
        if (query.getAge() != null) {
            wrapper.and(new QueryColumn("age").eq(query.getAge()));
        }
        wrapper.orderBy(PageUtils.toOrderBy(query, "create_time"));
        return wrapper;
    }
}
