package tuttibox.hw1.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.cucumber.spring.ScenarioScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ScenarioScope
public class ApiSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScenarioState state;

    public void get(String path) throws Exception {
        get(path, Map.of());
    }

    public void get(String path, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get(path);
        params.forEach(request::param);
        perform(request);
    }

    public void post(String path) throws Exception {
        post(path, Map.of());
    }

    public void post(String path, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path);
        params.forEach(request::param);
        perform(request);
    }

    public void postJson(String path, Object body) throws Exception {
        perform(MockMvcRequestBuilders.post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    public void perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString();
        JsonNode json = null;
        if (content != null && !content.isBlank()) {
            try {
                json = objectMapper.readTree(content);
            } catch (Exception ignored) {
                json = null;
            }
        }
        state.setResult(result, json);
    }

    public int status() {
        return state.getResult().getResponse().getStatus();
    }

    public String body() {
        try {
            return state.getResult().getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to read response body", e);
        }
    }

    public String contentType() {
        return state.getResult().getResponse().getContentType();
    }

    public JsonNode json() {
        return state.getJson();
    }

    public String text(String field) {
        assertThat(json()).as("response body must be a JSON object").isNotNull();
        JsonNode node = json().get(field);
        return node == null || node.isNull() ? null : node.asString();
    }

    public int arraySize() {
        assertThat(json()).as("response body must be a JSON array").isNotNull();
        assertThat(json().isArray()).as("response body must be a JSON array").isTrue();
        return json().size();
    }
}
