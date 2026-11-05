package io.microsphere.labs.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.microsphere.labs.mybatisplus.domain.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    @Select("select id, name, age, deleted, created_at, updated_at from users where id = #{id}")
    User selectRawById(@Param("id") Long id);
}
