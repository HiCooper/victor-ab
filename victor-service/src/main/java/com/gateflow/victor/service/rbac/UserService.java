package com.gateflow.victor.service.rbac;

import com.gateflow.victor.domain.entity.User;
import com.gateflow.victor.infra.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 根据用户名查询用户
     */
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }
}
