package com.frame.me.audit.service.convert;

import com.frame.me.audit.api.vo.LogVO;
import com.frame.me.audit.entity.LogEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 审计日志实体与 VO 之间的转换器.
 *
 * @author frame-me
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LogConvert {

    LogVO toVo(LogEntity entity);

    List<LogVO> toVoList(List<LogEntity> entities);
}
