package tuttibox.hw1.geocoding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.port.GeocodingException;
import tuttibox.hw1.port.GeocodingPort;

@Component
@ConditionalOnProperty(
    prefix = "tuttibox.geocoding",
    name = "provider",
    havingValue = "nominatim")
public class NominatimGeocodingAdapter implements GeocodingPort {
    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingAdapter.class);
    private static final String PROVIDER = "nominatim";

    private final GeocodingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NominatimGeocodingAdapter(GeocodingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public Optional<Coordinates> getCoordinates(String address) {
        URI uri = buildUri(address);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(properties.getTimeout())
            .header("User-Agent", properties.getUserAgent())
            .header("Accept", "application/json")
            .GET()
            .build();

        log.info("geocoding chamada: fornecedor={}, endereço={}", PROVIDER, address);

        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            log.warn("geocoding resultado: fornecedor={}, endereço={}, falha=timeout", PROVIDER, address);
            throw new GeocodingException(
                GeocodingException.Reason.TIMEOUT,
                "geocoding service timed out",
                ex);
        } catch (IOException ex) {
            log.warn("geocoding resultado: fornecedor={}, endereço={}, falha=indisponível",
                PROVIDER, address);
            throw new GeocodingException(
                GeocodingException.Reason.UNAVAILABLE,
                "geocoding service is unavailable",
                ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("geocoding resultado: fornecedor={}, endereço={}, falha=interrompido",
                PROVIDER, address);
            throw new GeocodingException(
                GeocodingException.Reason.UNAVAILABLE,
                "geocoding service is unavailable",
                ex);
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        int status = response.statusCode();

        if (status == 429) {
            log.warn("geocoding resultado: fornecedor={}, endereço={}, status={}, falha=rate limit, duraçãoMs={}",
                PROVIDER, address, status, durationMs);
            throw new GeocodingException(
                GeocodingException.Reason.RATE_LIMITED,
                "geocoding service usage limit exceeded");
        }
        if (status >= 500) {
            log.warn("geocoding resultado: fornecedor={}, endereço={}, status={}, falha=erro do fornecedor, duraçãoMs={}",
                PROVIDER, address, status, durationMs);
            throw new GeocodingException(
                GeocodingException.Reason.PROVIDER_ERROR,
                "geocoding service returned an error");
        }
        if (status < 200 || status >= 300) {
            log.warn("geocoding resultado: fornecedor={}, endereço={}, status={}, falha=resposta inesperada, duraçãoMs={}",
                PROVIDER, address, status, durationMs);
            throw new GeocodingException(
                GeocodingException.Reason.PROVIDER_ERROR,
                "geocoding service returned an error");
        }

        Optional<Coordinates> coordinates = parseCoordinates(address, response.body());
        log.info("geocoding resultado: fornecedor={}, endereço={}, status={}, encontrado={}, duraçãoMs={}",
            PROVIDER, address, status, coordinates.isPresent(), durationMs);
        return coordinates;
    }

    private Optional<Coordinates> parseCoordinates(String address, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isArray()) {
                throw invalidResponse(address);
            }
            if (root.size() == 0) {
                return Optional.empty();
            }

            JsonNode first = root.get(0);
            String latitude = first.path("lat").asString();
            String longitude = first.path("lon").asString();
            if (!StringUtils.hasText(latitude) || !StringUtils.hasText(longitude)) {
                throw invalidResponse(address);
            }

            double lat = Double.parseDouble(latitude);
            double lon = Double.parseDouble(longitude);
            if (!Double.isFinite(lat) || !Double.isFinite(lon)
                    || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                throw invalidResponse(address);
            }
            return Optional.of(new Coordinates(lat, lon));
        } catch (GeocodingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("geocoding resultado: fornecedor={}, endereço={}, falha=resposta inválida",
                PROVIDER, address);
            throw new GeocodingException(
                GeocodingException.Reason.INVALID_RESPONSE,
                "geocoding service returned an invalid response",
                ex);
        }
    }

    private GeocodingException invalidResponse(String address) {
        log.warn("geocoding resultado: fornecedor={}, endereço={}, falha=resposta inválida",
            PROVIDER, address);
        return new GeocodingException(
            GeocodingException.Reason.INVALID_RESPONSE,
            "geocoding service returned an invalid response");
    }

    private URI buildUri(String address) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
            .path("/search")
            .queryParam("q", address)
            .queryParam("format", "jsonv2")
            .queryParam("limit", properties.getLimit());

        if (StringUtils.hasText(properties.getCountryCodes())) {
            builder.queryParam("countrycodes", properties.getCountryCodes());
        }
        if (StringUtils.hasText(properties.getLanguage())) {
            builder.queryParam("accept-language", properties.getLanguage());
        }

        return builder.build().encode().toUri();
    }
}
