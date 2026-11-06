package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpringMvcTest {

    @RestController
    @RequestMapping("/orders")
    static class OrderController {

        @GetMapping("/{id}")
        public String get(@PathVariable("id") String id,
                          @RequestParam(value = "detail", defaultValue = "false") boolean detail) {
            return id + ":" + detail;
        }

        @GetMapping("/fail")
        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    @RestControllerAdvice
    static class GlobalAdvice {

        @ExceptionHandler(IllegalStateException.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        public String handle(IllegalStateException ex) {
            return "handled:" + ex.getMessage();
        }
    }

    @Test
    void resolvesPathAndQueryParameters() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new OrderController())
                .setControllerAdvice(new GlobalAdvice())
                .build();

        mvc.perform(get("/orders/order-1").param("detail", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string("order-1:true"));
    }

    @Test
    void handlesExceptionThroughControllerAdvice() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new OrderController())
                .setControllerAdvice(new GlobalAdvice())
                .build();

        mvc.perform(get("/orders/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("handled:boom"));
    }
}
