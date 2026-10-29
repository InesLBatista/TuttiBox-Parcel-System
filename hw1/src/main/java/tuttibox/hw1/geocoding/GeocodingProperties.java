package tuttibox.hw1.geocoding;

import java.time.Duration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tuttibox.geocoding")
public class GeocodingProperties {
    private String provider = "nominatim";
    private String baseUrl = "https://nominatim.openstreetmap.org";
    private String userAgent = "TuttiBox-HW1/1.0 (TQS academic project)";
    private Duration timeout = Duration.ofSeconds(5);
    private int limit = 1;
    private String countryCodes = "pt";
    private String language = "pt";
}
