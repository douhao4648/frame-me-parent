package com.frame.me.sso.service;

import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.mapper.UserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SSO 用户服务.
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 根据账号查询用户.
     */
    public UserEntity findByAccount(String account) {
        return userMapper.selectOneByQuery(QueryWrapper.create().eq("account", account));
    }

    /**
     * 根据用户 ID 查询用户.
     */
    public UserEntity findById(Long id) {
        return userMapper.selectOneById(id);
    }

    /**
     * 查询全部用户.
     */
    public List<UserEntity> list() {
        return userMapper.selectListByQuery(QueryWrapper.create());
    }

    /**
     * 保存用户.
     */
    public void save(UserEntity user) {
        userMapper.insert(user);
    }

    /**
     * 更新用户.
     */
    public void update(UserEntity user) {
        userMapper.update(user);
    }
}
