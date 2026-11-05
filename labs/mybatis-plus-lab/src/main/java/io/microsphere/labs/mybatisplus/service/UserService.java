package io.microsphere.labs.mybatisplus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.microsphere.labs.mybatisplus.domain.User;
import io.microsphere.labs.mybatisplus.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    public User getRawById(Long id) {
        return userMapper.selectRawById(id);
    }

    @Transactional(readOnly = true)
    public List<User> search(String name, Integer age) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(name), User::getName, name)
                .eq(age != null, User::getAge, age)
                .orderByAsc(User::getId);
        return userMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public IPage<User> page(long current, long size, String name, Integer age) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(name), User::getName, name)
                .eq(age != null, User::getAge, age)
                .orderByAsc(User::getId);
        return userMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Transactional
    public User create(User user) {
        userMapper.insert(user);
        return userMapper.selectById(user.getId());
    }

    @Transactional
    public int rename(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return userMapper.updateById(user);
    }

    @Transactional
    public int delete(Long id) {
        return userMapper.deleteById(id);
    }
}
