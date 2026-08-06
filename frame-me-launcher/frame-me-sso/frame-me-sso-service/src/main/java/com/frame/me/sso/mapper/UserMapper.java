package com.frame.me.sso.mapper;

import com.frame.me.sso.entity.UserEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSO 用户 Mapper.
 *
 * @author frame-me
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
