package io.microsphere.labs.mybatisplus.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.microsphere.labs.mybatisplus.domain.User;
import io.microsphere.labs.mybatisplus.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/lab/mybatis-plus/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable("id") Long id) {
        return userService.getById(id);
    }

    @GetMapping("/{id}/raw")
    public User getRawById(@PathVariable("id") Long id) {
        return userService.getRawById(id);
    }

    @GetMapping
    public List<User> search(@RequestParam(value = "name", required = false) String name,
                             @RequestParam(value = "age", required = false) Integer age) {
        return userService.search(name, age);
    }

    @GetMapping("/page")
    public IPage<User> page(@RequestParam(value = "current", defaultValue = "1") long current,
                            @RequestParam(value = "size", defaultValue = "10") long size,
                            @RequestParam(value = "name", required = false) String name,
                            @RequestParam(value = "age", required = false) Integer age) {
        return userService.page(current, size, name, age);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}/name")
    public int rename(@PathVariable("id") Long id, @RequestParam("name") String name) {
        return userService.rename(id, name);
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable("id") Long id) {
        return userService.delete(id);
    }
}
