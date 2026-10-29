package tuttibox.hw1.usability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tuttibox.hw1.TestcontainersConfiguration;

/**
 * Testes de Usabilidade e Acessibilidade das Interfaces Web
 * Avalia os critérios de usabilidade pelas heurísticas:
 * - Visibilidade do estado do sistema e feedback acessível (aria-live, role="status")
 * - Correspondência com o mundo real e ergonomia de formulários (labels explícitos, placeholders, inputmode)
 * - Controlo do utilizador e recuperação de erros (ações de reset, limpeza, confirmação em passos)
 * - Consistência e normas (design coerente, folha de estilos unificada, navegação global partilhada)
 * - Prevenção de erros (sugestões em datalist, desativação de botões em voo, confirmação de fecho de porta)
 * - Reconhecimento em vez de memorização (tabelas com estados vazios explicativos, sumários de sucesso)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WebInterfaceUsabilityTest {
    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "página {0} cumpre os padrões globais de usabilidade e acessibilidade")
    @ValueSource(strings = {"/index.html", "/recipient.html", "/courier.html", "/tracking.html", "/operator.html"})
    @DisplayName("padrões globais: todas as páginas têm viewport responsivo, idioma pt, styles.css e navegação cruzada")
    void testGlobalUsabilityStandardsAcrossAllPages(String pageUrl) throws Exception {
        MvcResult result = mockMvc.perform(get(pageUrl))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        //acessibilidade técnica básica: doctype, lang e charset
        assertThat(html).containsIgnoringCase("<!DOCTYPE html>");
        assertThat(html).contains("<html lang=\"pt\">");
        assertThat(html).contains("<meta charset=\"UTF-8\">");

        //responsividade para touchscreens e mobile 
        assertThat(html).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");

        //consistência visual e temática unificada
        assertThat(html).contains("<link rel=\"stylesheet\" href=\"/styles.css\">");

        //estrutura semântica para HTML5
        assertThat(html).contains("<main>");
        assertThat(html).contains("<header>");
        assertThat(html).contains("<footer>");

        //navegação global e cruzada: o utilizador tem links de rodapé para regressar ou mudar de terminal
        assertThat(html).contains("<footer>");
        if (!pageUrl.equals("/index.html")) {
            assertThat(html).containsAnyOf("href=\"/\"", "href=\"/index.html\"");
        }
        if (!pageUrl.equals("/recipient.html")) {
            assertThat(html).contains("href=\"/recipient.html\"");
        }
        if (!pageUrl.equals("/courier.html")) {
            assertThat(html).contains("href=\"/courier.html\"");
        }
        if (!pageUrl.equals("/tracking.html")) {
            assertThat(html).contains("href=\"/tracking.html\"");
        }
        if (!pageUrl.equals("/operator.html")) {
            assertThat(html).contains("href=\"/operator.html\"");
        }
    }

    @Test
    @DisplayName("UC1 usabilidade (index.html): formulário de pesquisa com datalist de sugestões, labels acessíveis e tabela de compartimentos")
    void testIndexStationSearchUsability() throws Exception {
        MvcResult result = mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        //label explicitamente associado ao input (WCAG 2.1 - 1.3.1 Info and Relationships)
        assertThat(html).contains("<label for=\"address\">Morada</label>");
        assertThat(html).contains("id=\"address\"");
        assertThat(html).contains("name=\"address\"");

        //prevenção de erros e ergonomia: autocomplete e placeholder descritivo
        assertThat(html).contains("autocomplete=\"street-address\"");
        assertThat(html).contains("placeholder=\"Ex.: Anadia\"");

        //sugestões rápidas para reconhecimento em vez de memorização heurística
        assertThat(html).contains("<datalist id=\"address-examples\">");
        assertThat(html).contains("<option value=\"Aveiro\">");
        assertThat(html).contains("<option value=\"Universidade de Aveiro\">");

        //feedback acessível de estado ao vivo
        assertThat(html).contains("role=\"status\"");
        assertThat(html).contains("aria-live=\"polite\"");

        //tabela acessível para visualização de resultados com distinção de compartimentos por tamanho (S, M, L)
        assertThat(html).contains("<table");
        assertThat(html).contains("<th scope=\"col\">S</th>");
        assertThat(html).contains("<th scope=\"col\">M</th>");
        assertThat(html).contains("<th scope=\"col\">L</th>");
    }

    @Test
    @DisplayName("UC4 Usabilidade (recipient.html): terminal do destinatário com input de código touch-friendly, ações de reset e feedback de sucesso")
    void testRecipientTerminalUsability() throws Exception {
        MvcResult result = mockMvc.perform(get("/recipient.html"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        //identificação clara do contexto do terminal
        assertThat(html).contains("Terminal do destinatário");

        //ergonomia do código de recolha: 8 caracteres, teclado otimizado, autocomplete de SMS
        assertThat(html).contains("<label for=\"pickup-code\">Código de recolha</label>");
        assertThat(html).contains("id=\"pickup-code\"");
        assertThat(html).contains("maxlength=\"8\"");
        assertThat(html).contains("autocomplete=\"one-time-code\"");
        assertThat(html).contains("autocapitalize=\"characters\"");

        //ações de controlo do utilizador (controlo e liberdade do user)
        assertThat(html).contains("id=\"submit-button\"");
        assertThat(html).contains("id=\"reset-button\"");
        assertThat(html).contains("Limpar");

        //região acessível de feedback para leitores de ecrã e touchscreen
        assertThat(html).contains("id=\"status\"");
        assertThat(html).contains("role=\"status\"");
        assertThat(html).contains("aria-live=\"polite\"");

        //seção dedicada de confirmação/sucesso informando sobre a abertura física da porta
        assertThat(html).contains("id=\"success\"");
        assertThat(html).contains("id=\"success-summary\"");
        assertThat(html).contains("id=\"new-pickup-button\"");
    }

    @Test
    @DisplayName("UC3 usabilidade (courier.html): terminal do courier com fluxo em 2 passos, prevenção de fecho prematuro e recuperação")
    void testCourierTerminalUsability() throws Exception {
        MvcResult result = mockMvc.perform(get("/courier.html"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        //recolha de dados e abertura
        assertThat(html).contains("id=\"dropoff-form\"");
        assertThat(html).contains("id=\"tracking-code\"");
        assertThat(html).contains("id=\"station-id\"");
        assertThat(html).contains("id=\"compartment-id\"");
        assertThat(html).contains("id=\"courier-id\"");
        assertThat(html).contains("id=\"open-button\"");
        assertThat(html).contains("id=\"reset-button\"");

        //confirmação física do fecho da porta antes de finalizar o depósito
        assertThat(html).contains("id=\"confirmation\"");
        assertThat(html).contains("Passo 2: confirmar o fecho");
        assertThat(html).contains("id=\"confirm-button\"");

        //conclusão com possibilidade de novo depósito
        assertThat(html).contains("id=\"success\"");
        assertThat(html).contains("id=\"new-button\"");

        //prevenção de erros e instruções claras ao operador logístico
        assertThat(html).contains("role=\"status\"");
        assertThat(html).contains("aria-live=\"polite\"");
    }

    @Test
    @DisplayName("UC5 usabilidade (tracking.html): consulta acessível sem popups intrusivos, com visualização de estado e timeline de histórico")
    void testTrackingPageUsability() throws Exception {
        MvcResult result = mockMvc.perform(get("/tracking.html"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        //campo de tracking com label e autocapitalize
        assertThat(html).contains("<label for=\"tracking-code\">Código de tracking</label>");
        assertThat(html).contains("id=\"tracking-code\"");
        assertThat(html).contains("autocapitalize=\"characters\"");

        //ações de submissão e limpeza
        assertThat(html).contains("id=\"submit-button\"");
        assertThat(html).contains("id=\"reset-button\"");

        //feedback na própria página acessível sem alerts boqueantes
        assertThat(html).contains("id=\"status-message\"");
        assertThat(html).contains("role=\"status\"");
        assertThat(html).contains("aria-live=\"polite\"");

        //cartões organizados para Estado Atual e Histórico Cronológico
        assertThat(html).contains("id=\"status-card\"");
        assertThat(html).contains("id=\"history-card\"");
        assertThat(html).contains("id=\"history\"");
    }

    @Test
    @DisplayName("UC6 usabilidade (operator.html): supervisão com filtros completos por estado, tabelas organizadas e mensagens de estado vazio")
    void testOperatorSupervisionUsability() throws Exception {
        MvcResult result = mockMvc.perform(get("/operator.html"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        //filtros estruturados para o operador
        assertThat(html).contains("id=\"operator-form\"");
        assertThat(html).contains("<label for=\"station-id\">Código da estação</label>");
        assertThat(html).contains("<label for=\"actor\">Identificador do operador</label>");
        assertThat(html).contains("<label for=\"parcel-state\">Estado das encomendas</label>");

        //dropdown com todos os estados válidos da máquina de estados (CREATED, READY_FOR_PICKUP, CANCELLED, PICKED_UP, EXPIRED, RETURNED)
        assertThat(html).contains("<option value=\"CREATED\">CREATED</option>");
        assertThat(html).contains("<option value=\"READY_FOR_PICKUP\">READY_FOR_PICKUP</option>");
        assertThat(html).contains("<option value=\"CANCELLED\">CANCELLED</option>");
        assertThat(html).contains("<option value=\"PICKED_UP\">PICKED_UP</option>");
        assertThat(html).contains("<option value=\"EXPIRED\">EXPIRED</option>");
        assertThat(html).contains("<option value=\"RETURNED\">RETURNED</option>");

        //mensagens informativas para estados vazios de tabela
        assertThat(html).contains("class=\"empty\"");
        assertThat(html).contains("Indique uma estação e clique em atualizar.");
        assertThat(html).contains("Sem encomendas expiradas para mostrar.");

        //tabelas bem delimitadas para encomendas, expiradas e manutenção de compartimentos
        assertThat(html).contains("id=\"parcels-title\"");
        assertThat(html).contains("id=\"expired-title\"");
        assertThat(html).contains("id=\"compartments-title\"");
    }
}
