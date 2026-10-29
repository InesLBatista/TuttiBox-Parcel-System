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
class OperatorTerminalPageIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("página de supervisão do operador está disponível com as operações da UC6")
    void testOperatorPageIsAvailable() throws Exception {
        mockMvc.perform(get("/operator.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("Supervis")))
            .andExpect(content().string(containsString("operador")))
            .andExpect(content().string(containsString("id=\"operator-form\"")))
            .andExpect(content().string(containsString("/api/operator/parcels")))
            .andExpect(content().string(containsString("/api/operator/parcels/expired")))
            .andExpect(content().string(containsString("/compartments")))
            .andExpect(content().string(containsString("out-of-service")))
            .andExpect(content().string(containsString("in-service")));
    }

    @Test
    @DisplayName("página inicial liga à supervisão do operador")
    void testIndexLinksToOperatorPage() throws Exception {
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/operator.html\"")));
    }
}
