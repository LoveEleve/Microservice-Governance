package io.microsphere.labs.seata.at.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final JdbcTemplate jdbcTemplate;

    public AccountService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deduct(String userId, int amount) {
        int updated = jdbcTemplate.update(
                "UPDATE lab_account SET balance = balance - ? WHERE user_id = ? AND balance >= ?",
                amount, userId, amount);

        if (updated != 1) {
            throw new IllegalStateException("account balance is not enough: " + userId);
        }
    }
}
