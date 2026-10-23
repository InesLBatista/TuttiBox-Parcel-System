package tuttibox.hw1.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tuttibox.hw1.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourierTerminalPageIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("terminal do courier é servido com o formulário de drop-off e confirmação")
    void testCourierTerminalPageIsAvailable() throws Exception {
        mockMvc.perform(get("/courier.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("Terminal do courier")))
            .andExpect(content().string(containsString("id=\"dropoff-form\"")))
            .andExpect(content().string(containsString("/dropoff/confirm")));
    }
}
