package tuttibox.hw1.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.port.GeocodingException;

class NominatimGeocodingAdapterTest {
    private HttpServer server;
    private GeocodingProperties properties;
    private NominatimGeocodingAdapter adapter;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        properties = new GeocodingProperties();
        properties.setProvider("nominatim");
        properties.setUserAgent("TuttiBox-Test/1.0");
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setLimit(1);
        properties.setCountryCodes("pt");
        properties.setLanguage("pt");

        logger = (Logger) LoggerFactory.getLogger(NominatimGeocodingAdapter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("devolve coordenadas e envia os parâmetros esperados ao fornecedor")
    void testSuccessfulGeocoding() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        startServer(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, 200, """
                [{"lat":"40.6405","lon":"-8.6538"}]
                """);
        });

        Optional<Coordinates> result = adapter().getCoordinates("Aveiro");

        assertThat(result).contains(new Coordinates(40.6405, -8.6538));
        assertThat(query.get())
            .contains("q=Aveiro")
            .contains("format=jsonv2")
            .contains("limit=1")
            .contains("countrycodes=pt")
            .contains("accept-language=pt");
        assertThat(userAgent.get()).isEqualTo("TuttiBox-Test/1.0");
        assertThat(messages())
            .anyMatch(message -> message.contains("geocoding chamada")
                && message.contains("Aveiro"))
            .anyMatch(message -> message.contains("encontrado=true"));
    }

    @Test
    @DisplayName("devolve vazio quando o fornecedor não encontra a morada")
    void testAddressNotFound() throws Exception {
        startServer(exchange -> respond(exchange, 200, "[]"));

        assertThat(adapter().getCoordinates("Morada desconhecida")).isEmpty();
    }

    @Test
    @DisplayName("mapeia HTTP 429 para RATE_LIMITED")
    void testRateLimited() throws Exception {
        startServer(exchange -> respond(exchange, 429, "{}"));

        assertReason("Aveiro", GeocodingException.Reason.RATE_LIMITED);
    }

    @Test
    @DisplayName("mapeia HTTP 5XX para PROVIDER_ERROR")
    void testProviderError() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));

        assertReason("Aveiro", GeocodingException.Reason.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("mapeia JSON inválido para INVALID_RESPONSE")
    void testInvalidJson() throws Exception {
        startServer(exchange -> respond(exchange, 200, "não é json"));

        assertReason("Aveiro", GeocodingException.Reason.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("mapeia resposta sem coordenadas para INVALID_RESPONSE")
    void testMissingCoordinates() throws Exception {
        startServer(exchange -> respond(exchange, 200, "[{\"display_name\":\"Aveiro\"}]"));

        assertReason("Aveiro", GeocodingException.Reason.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("mapeia timeout para TIMEOUT")
    void testTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "[]");
        });
        properties.setTimeout(Duration.ofMillis(100));

        assertReason("Aveiro", GeocodingException.Reason.TIMEOUT);
    }

    @Test
    @DisplayName("mapeia falha de ligação para UNAVAILABLE")
    void testConnectionFailure() throws Exception {
        HttpServer temporary = HttpServer.create(new InetSocketAddress(0), 0);
        int port = temporary.getAddress().getPort();
        temporary.start();
        temporary.stop(0);

        properties.setBaseUrl("http://localhost:" + port);
        adapter = new NominatimGeocodingAdapter(properties, new ObjectMapper());

        assertReason("Aveiro", GeocodingException.Reason.UNAVAILABLE);
    }

    private NominatimGeocodingAdapter adapter() {
        if (adapter == null) {
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            adapter = new NominatimGeocodingAdapter(properties, new ObjectMapper());
        }
        return adapter;
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", handler);
        server.start();
    }

    private void assertReason(String address, GeocodingException.Reason reason) {
        assertThatThrownBy(() -> adapter().getCoordinates(address))
            .isInstanceOf(GeocodingException.class)
            .satisfies(ex -> assertThat(((GeocodingException) ex).getReason()).isEqualTo(reason));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private java.util.List<String> messages() {
        return appender.list.stream()
            .map(event -> java.util.Objects.requireNonNull(event).getFormattedMessage())
            .toList();
    }
}
