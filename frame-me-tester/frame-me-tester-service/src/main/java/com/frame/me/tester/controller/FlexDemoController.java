package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageData;
import com.frame.me.base.result.Result;
import com.frame.me.tester.api.IFlexDemoApi;
import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.api.query.FlexDemoQuery;
import com.frame.me.tester.api.vo.FlexDemoVO;
import com.frame.me.tester.service.IFlexDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MyBatis-Flex 演示 Controller，实现 {@link IFlexDemoApi}.
 */
@RestController
@Validated
@RequiredArgsConstructor
public class FlexDemoController implements IFlexDemoApi {

    private static final String DS_SECOND = "second";

    private final IFlexDemoService flexDemoService;

    @Override
    public IResult<List<FlexDemoVO>> list() {
        return Result.success(flexDemoService.list());
    }

    @Override
    public IResult<PageData<FlexDemoVO>> page(FlexDemoQuery query) {
        return Result.success(flexDemoService.page(query));
    }

    @Override
    public IResult<FlexDemoVO> getById(Long id) {
        return Result.success(flexDemoService.getById(id));
    }

    @Override
    public IResult<Long> create(FlexDemoDTO dto) {
        return Result.success(flexDemoService.create(dto));
    }

    @Override
    public IResult<Long> createInSecond(FlexDemoDTO dto) {
        return Result.success(flexDemoService.createInSecond(dto));
    }

    @Override
    public IResult<Boolean> update(Long id, FlexDemoDTO dto) {
        return Result.success(flexDemoService.update(id, dto));
    }

    @Override
    public IResult<Boolean> delete(Long id) {
        return Result.success(flexDemoService.delete(id));
    }

    @Override
    public IResult<Long> count(String ds) {
        long count = DS_SECOND.equalsIgnoreCase(ds) ? flexDemoService.countInSecond() : flexDemoService.count();
        return Result.success(count);
    }

    @Override
    public IResult<List<FlexDemoVO>> listByXml(FlexDemoQuery query) {
        return Result.success(flexDemoService.listByXml(query));
    }
}
