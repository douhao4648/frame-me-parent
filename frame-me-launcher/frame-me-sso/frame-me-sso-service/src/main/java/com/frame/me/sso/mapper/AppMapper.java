package com.frame.me.sso.mapper;

import com.frame.me.sso.entity.AppEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSO 应用注册表 Mapper.
 *
 * @author frame-me
 */
@Mapper
public interface AppMapper extends BaseMapper<AppEntity> {
}
