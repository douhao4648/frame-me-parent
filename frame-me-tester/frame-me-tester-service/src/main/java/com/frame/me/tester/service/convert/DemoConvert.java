//package com.frame.me.tester.service.convert;
//
//import com.frame.me.tester.api.dto.DemoDTO;
//import com.frame.me.tester.api.vo.DemoVO;
//import com.frame.me.tester.entity.DemoEntity;
//import org.mapstruct.Mapper;
//import org.mapstruct.ReportingPolicy;
//
//import java.util.List;
//
///**
// * Demo 对象转换器.
// */
//@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
//public interface DemoConvert {
//
//    /**
//     * 实体转 VO.
//     *
//     * @param entity 实体
//     * @return VO
//     */
//    DemoVO toVo(DemoEntity entity);
//
//    /**
//     * 实体列表转 VO 列表.
//     *
//     * @param entities 实体列表
//     * @return VO 列表
//     */
//    List<DemoVO> toVoList(List<DemoEntity> entities);
//
//    /**
//     * DTO 转实体.
//     *
//     * @param dto DTO
//     * @return 实体
//     */
//    DemoEntity toEntity(DemoDTO dto);
//}
