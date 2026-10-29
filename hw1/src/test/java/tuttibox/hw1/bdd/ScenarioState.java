package tuttibox.hw1.bdd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;

import io.cucumber.spring.ScenarioScope;
import tools.jackson.databind.JsonNode;

@Component
@ScenarioScope
public class ScenarioState {
    private MvcResult result;
    private JsonNode json;
    private String lastTrackingCode;
    private String lastPickupCode;
    private final List<String> trackingCodes = new ArrayList<>();
    private final Map<String, String> rememberedTrackingCodes = new LinkedHashMap<>();
    private List<ConcurrentRegistration> concurrentRegistrations = List.of();

    public MvcResult getResult() {
        return result;
    }

    public void setResult(MvcResult result, JsonNode json) {
        this.result = result;
        this.json = json;
        if (json != null && json.hasNonNull("trackingCode")) {
            setLastTrackingCode(json.get("trackingCode").asString());
        }
    }

    public JsonNode getJson() {
        return json;
    }

    public String getLastTrackingCode() {
        return lastTrackingCode;
    }

    public void setLastTrackingCode(String lastTrackingCode) {
        this.lastTrackingCode = lastTrackingCode;
        if (lastTrackingCode != null && !trackingCodes.contains(lastTrackingCode)) {
            trackingCodes.add(lastTrackingCode);
        }
    }

    public String getLastPickupCode() {
        return lastPickupCode;
    }

    public void setLastPickupCode(String lastPickupCode) {
        this.lastPickupCode = lastPickupCode;
    }

    public List<String> getTrackingCodes() {
        return List.copyOf(trackingCodes);
    }

    public void rememberTrackingCode(String name, String trackingCode) {
        rememberedTrackingCodes.put(name, trackingCode);
    }

    public String rememberedTrackingCode(String name) {
        return rememberedTrackingCodes.get(name);
    }

    public List<ConcurrentRegistration> getConcurrentRegistrations() {
        return concurrentRegistrations;
    }

    public void setConcurrentRegistrations(List<ConcurrentRegistration> concurrentRegistrations) {
        this.concurrentRegistrations = List.copyOf(concurrentRegistrations);
    }

    public void reset() {
        result = null;
        json = null;
        lastTrackingCode = null;
        lastPickupCode = null;
        trackingCodes.clear();
        rememberedTrackingCodes.clear();
        concurrentRegistrations = List.of();
    }

    public record ConcurrentRegistration(String partnerId, String trackingCode, Throwable failure) {
        public boolean succeeded() {
            return failure == null;
        }
    }
}
