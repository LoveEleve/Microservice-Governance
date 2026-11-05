package io.microsphere.labs.mybatis.mapper;

import io.microsphere.labs.mybatis.domain.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    User selectById(@Param("id") Long id);

    List<User> search(@Param("name") String name, @Param("age") Integer age);

    User selectWithOrders(@Param("id") Long id);

    int insert(User user);

    int updateName(@Param("id") Long id, @Param("name") String name);

    int deleteById(@Param("id") Long id);
}
