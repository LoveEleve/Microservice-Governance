package io.microsphere.labs.shardingsphere.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createOrder(long userId, BigDecimal amount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into t_order (user_id, amount, status) values (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setBigDecimal(2, amount);
            statement.setString(3, "NEW");
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated order_id returned by ShardingSphere");
        }
        return key.longValue();
    }

    public void createItem(long orderId, long userId, String productName, int quantity) {
        jdbcTemplate.update(
                "insert into t_order_item (order_id, user_id, product_name, quantity) values (?, ?, ?, ?)",
                orderId, userId, productName, quantity);
    }

    public List<Map<String, Object>> findOrdersByUserId(long userId) {
        return jdbcTemplate.queryForList(
                "select order_id, user_id, amount, status from t_order where user_id = ? order by order_id",
                userId);
    }

    public List<Map<String, Object>> findOrderByOrderId(long orderId) {
        return jdbcTemplate.queryForList(
                "select order_id, user_id, amount, status from t_order where order_id = ?",
                orderId);
    }

    public List<Map<String, Object>> findItemsByUserId(long userId) {
        return jdbcTemplate.queryForList(
                "select item_id, order_id, user_id, product_name, quantity from t_order_item where user_id = ? order by item_id",
                userId);
    }

    public void upsertDict(String dictType, String dictValue) {
        jdbcTemplate.update(
                "insert into t_dict (dict_type, dict_value) values (?, ?) on duplicate key update dict_value = values(dict_value)",
                dictType, dictValue);
    }

    public List<Map<String, Object>> findDict(String dictType) {
        return jdbcTemplate.queryForList(
                "select dict_id, dict_type, dict_value from t_dict where dict_type = ?",
                dictType);
    }
}
