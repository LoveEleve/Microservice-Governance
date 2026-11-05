package io.microsphere.labs.mybatis.service;

import io.microsphere.labs.mybatis.domain.User;
import io.microsphere.labs.mybatis.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Transactional(readOnly = true)
    public List<User> search(String name, Integer age) {
        return userMapper.search(name, age);
    }

    @Transactional(readOnly = true)
    public User getWithOrders(Long id) {
        return userMapper.selectWithOrders(id);
    }

    @Transactional
    public User create(User user) {
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public int rename(Long id, String name) {
        return userMapper.updateName(id, name);
    }

    @Transactional
    public int delete(Long id) {
        return userMapper.deleteById(id);
    }
}
