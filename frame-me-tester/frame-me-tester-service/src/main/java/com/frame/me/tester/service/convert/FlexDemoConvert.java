package com.frame.me.tester.service.convert;

import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.api.vo.FlexDemoVO;
import com.frame.me.tester.entity.FlexDemoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Flex 演示实体与 DTO/VO 之间的转换器.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FlexDemoConvert {

    FlexDemoVO toVo(FlexDemoEntity entity);

    List<FlexDemoVO> toVoList(List<FlexDemoEntity> entities);

    FlexDemoEntity toEntity(FlexDemoDTO dto);
}
