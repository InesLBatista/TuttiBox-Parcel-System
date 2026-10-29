package tuttibox.hw1.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI tuttiboxOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("TuttiBox API")
                .version("1.0.0")
                .description("REST API for station search, parcel lifecycle and locker operations. "
                    + "This academic/demo application does not implement authentication."));
    }
}
