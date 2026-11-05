package io.microsphere.labs.mybatis.web;

import io.microsphere.labs.mybatis.domain.User;
import io.microsphere.labs.mybatis.service.UserService;
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
@RequestMapping("/lab/mybatis/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable("id") Long id) {
        return userService.getById(id);
    }

    @GetMapping
    public List<User> search(@RequestParam(value = "name", required = false) String name,
                             @RequestParam(value = "age", required = false) Integer age) {
        return userService.search(name, age);
    }

    @GetMapping("/{id}/orders")
    public User getWithOrders(@PathVariable("id") Long id) {
        return userService.getWithOrders(id);
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
