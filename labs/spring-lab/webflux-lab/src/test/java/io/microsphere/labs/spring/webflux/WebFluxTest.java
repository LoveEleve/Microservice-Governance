package io.microsphere.labs.spring.webflux;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

class WebFluxTest {

    @Test
    void returnsMonoAndFlux() {
        HelloController controller = new HelloController();

        StepVerifier.create(controller.hello())
                .expectNext("hello")
                .verifyComplete();

        StepVerifier.create(controller.numbers())
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    void handlesWebFluxRequestWithoutServer() {
        WebTestClient client = WebTestClient
                .bindToController(new HelloController())
                .build();

        client.get().uri("/hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("hello");
    }
}
