package tuttibox.hw1.usability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.fake.InMemoryNotificationFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PlaywrightEndToEndTest {
    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private InMemoryGeocodingFake geocodingFake;
    @Autowired
    private InMemoryHardwareFake hardwareFake;
    @Autowired
    private InMemoryNotificationFake notificationFake;

    private BrowserContext browserContext;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void prepareScenario() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
        geocodingFake.clearFailure();
        hardwareFake.reset();
        notificationFake.clear();
        browserContext = browser.newContext();
        page = browserContext.newPage();
    }

    @AfterEach
    void closePage() {
        if (browserContext != null) {
            browserContext.close();
        }
    }

    @Test
    @DisplayName("todas as páginas principais abrem num browser real e carregam o estilo partilhado")
    void allMainPagesRenderInChromium() {
        for (String path : List.of(
                "/", "/recipient.html", "/courier.html", "/tracking.html", "/operator.html")) {
            page.navigate(baseUrl() + path);

            assertThat(page.locator("main").isVisible()).as("main em %s", path).isTrue();
            assertThat(page.locator("h1").isVisible()).as("título em %s", path).isTrue();
            assertThat(page.locator("footer").isVisible()).as("rodapé em %s", path).isTrue();
            assertThat(page.locator("link[rel='stylesheet'][href='/styles.css']").count())
                .as("folha de estilos em %s", path).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("pesquisa de estações apresenta resultados após interação no browser")
    void stationSearchRendersLiveResults() {
        station("ST-PLAY-SEARCH", "Estação Playwright", 40.6405, -8.6538);
        compartment("C-PLAY-S", "ST-PLAY-SEARCH", CompartmentSize.S, CompartmentStatus.FREE);
        geocodingFake.addAddress("Morada Playwright", new Coordinates(40.6405, -8.6538));

        page.navigate(baseUrl() + "/");
        page.locator("#address").fill("Morada Playwright");
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Procurar estações")).click();

        page.getByText("Estação Playwright").waitFor();
        assertThat(page.locator("#results").isVisible()).isTrue();
        assertThat(page.locator("#results-body tr").count()).isEqualTo(1);
        assertThat(page.locator("#results-body").textContent()).contains("ST-PLAY-SEARCH", "1");
    }

    @Test
    @DisplayName("courier, tracking e destinatário completam o ciclo de uma encomenda no browser")
    void parcelLifecycleWorksAcrossBrowserTerminals() {
        Station station = station("ST-PLAY-LIFE", "Estação E2E", 40.64, -8.64);
        Compartment compartment = compartment(
            "C-PLAY-M", station.getCode(), CompartmentSize.M, CompartmentStatus.RESERVED);
        String trackingCode = "TRK-PLAY0001";
        LocalDateTime registeredAt = LocalDateTime.now();
        Parcel parcel = new Parcel(trackingCode, "destinatario@example.com", "M");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.setPartnerId("PARTNER_E2E");
        parcel.setRegisteredAt(registeredAt);
        parcel.setDropOffDeadline(registeredAt.plusDays(2));
        parcelRepository.save(parcel);

        page.navigate(baseUrl() + "/courier.html");
        page.getByLabel("Tracking code").fill(trackingCode);
        page.getByLabel("Estação").fill(station.getCode());
        page.getByLabel("Compartimento").fill(compartment.getCode());
        page.getByLabel("Identificador do courier").fill("COURIER_E2E");
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Abrir compartimento")).click();
        page.locator("#confirmation").waitFor();
        assertThat(page.locator("#confirmation").textContent()).contains("Passo 2: confirmar o fecho");

        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Confirmar fecho e concluir")).click();
        page.getByRole(AriaRole.HEADING,
            new Page.GetByRoleOptions().setName("Depósito concluído")).waitFor();
        assertThat(parcelRepository.findByTrackingCode(trackingCode).orElseThrow().getCurrentState())
            .isEqualTo(ParcelState.READY_FOR_PICKUP);

        String pickupCode = notificationFake.findLastByTrackingCode(trackingCode)
            .orElseThrow().pickupCode();

        page.navigate(baseUrl() + "/tracking.html?code=" + trackingCode);
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Consultar")).click();
        page.locator("#history li").first().waitFor();
        assertThat(page.locator("#state").textContent()).isEqualTo("READY_FOR_PICKUP");
        assertThat(page.locator("#history li").count()).isGreaterThanOrEqualTo(2);

        page.navigate(baseUrl() + "/recipient.html");
        page.getByLabel("Código de recolha").fill(pickupCode);
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Recolher encomenda")).click();
        page.getByRole(AriaRole.HEADING,
            new Page.GetByRoleOptions().setName("Compartimento aberto com sucesso")).waitFor();

        assertThat(parcelRepository.findByTrackingCode(trackingCode).orElseThrow().getCurrentState())
            .isEqualTo(ParcelState.PICKED_UP);
        assertThat(compartmentRepository.findByStationCodeAndCode(station.getCode(), compartment.getCode())
            .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("operador altera o estado de um compartimento pela interface")
    void operatorCanTakeCompartmentOutOfServiceAndRestoreIt() {
        Station station = station("ST-PLAY-OPS", "Estação Operador", 40.64, -8.64);
        compartment("C-PLAY-FREE", station.getCode(), CompartmentSize.S, CompartmentStatus.FREE);

        page.navigate(baseUrl() + "/operator.html?stationId=" + station.getCode());
        Locator row = page.locator("#compartments-body tr")
            .filter(new Locator.FilterOptions().setHasText("C-PLAY-FREE"));
        row.getByRole(AriaRole.BUTTON,
            new Locator.GetByRoleOptions().setName("Fora de serviço")).click();
        page.getByText("compartimento C-PLAY-FREE colocado fora de serviço.").waitFor();
        assertThat(row.textContent()).contains("OUT_OF_SERVICE");

        row.getByRole(AriaRole.BUTTON,
            new Locator.GetByRoleOptions().setName("Voltar ao serviço")).click();
        page.getByText("compartimento C-PLAY-FREE de volta ao serviço.").waitFor();
        assertThat(row.textContent()).contains("FREE");
        assertThat(compartmentRepository.findByStationCodeAndCode(station.getCode(), "C-PLAY-FREE")
            .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private Station station(String code, String name, double latitude, double longitude) {
        return stationRepository.save(new Station(code, name, latitude, longitude));
    }

    private Compartment compartment(
            String code, String stationCode, CompartmentSize size, CompartmentStatus status) {
        Station station = stationRepository.findByCode(stationCode).orElseThrow();
        Compartment compartment = new Compartment(code, station, size);
        if (status == CompartmentStatus.RESERVED) {
            compartment.reserve();
        } else if (status == CompartmentStatus.OCCUPIED) {
            compartment.reserve();
            compartment.occupy();
        } else if (status == CompartmentStatus.OUT_OF_SERVICE) {
            compartment.putOutOfService();
        }
        return compartmentRepository.save(compartment);
    }
}
