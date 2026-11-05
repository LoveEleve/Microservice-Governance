package io.microsphere.labs.seata.at.account;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/deduct")
    public String deduct(@RequestParam String userId, @RequestParam int amount) {
        accountService.deduct(userId, amount);
        return "OK";
    }
}
