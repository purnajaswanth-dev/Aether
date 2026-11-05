package com.context_platform.aether;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class WebMvcMappingAmbiguityTest extends BaseTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Verify Spring MVC handler mappings initialize without any ambiguous route collisions")
    void testNoAmbiguousHandlerMappings() {
        assertThat(handlerMapping).isNotNull();
        assertThat(handlerMapping.getHandlerMethods()).isNotEmpty();
    }
}
