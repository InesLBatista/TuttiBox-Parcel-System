package tuttibox.hw1.geocoding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GeocodingProperties.class)
public class GeocodingConfiguration {
}
