package tuttibox.hw1.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingHardwarePortTest {
    private HardwarePort delegate;
    private LoggingHardwarePort hardwarePort;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        delegate = mock(HardwarePort.class);
        hardwarePort = new LoggingHardwarePort(delegate);
        logger = (Logger) LoggerFactory.getLogger(LoggingHardwarePort.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("abertura com sucesso registra a chamada e o resultado")
    void testOpenCompartmentIsLogged() {
        hardwarePort.openCompartment("ST-01", "C-1");

        verify(delegate).openCompartment("ST-01", "C-1");
        assertThat(messages())
            .anyMatch(message -> message.contains("openCompartment")
                && message.contains("ST-01")
                && message.contains("C-1")
                && message.contains("sucesso=true"));
    }

    @Test
    @DisplayName("falha na abertura é registrada e propagada")
    void testOpenCompartmentFailureIsLogged() {
        doThrow(new HardwareException("falha ao abrir"))
            .when(delegate).openCompartment("ST-01", "C-1");

        assertThatThrownBy(() -> hardwarePort.openCompartment("ST-01", "C-1"))
            .isInstanceOf(HardwareException.class)
            .hasMessageContaining("falha ao abrir");

        assertThat(messages())
            .anyMatch(message -> message.contains("openCompartment")
                && message.contains("sucesso=false")
                && message.contains("falha ao abrir"));
    }

    @Test
    @DisplayName("confirmação de fecho com sucesso registra a chamada e o resultado")
    void testConfirmDoorClosedIsLogged() {
        hardwarePort.confirmDoorClosed("ST-01", "C-1");

        verify(delegate).confirmDoorClosed("ST-01", "C-1");
        assertThat(messages())
            .anyMatch(message -> message.contains("confirmDoorClosed")
                && message.contains("sucesso=true"));
    }

    @Test
    @DisplayName("falha na confirmação de fecho é registrada e propagada")
    void testConfirmDoorClosedFailureIsLogged() {
        doThrow(new HardwareException("falha ao fechar"))
            .when(delegate).confirmDoorClosed("ST-01", "C-1");

        assertThatThrownBy(() -> hardwarePort.confirmDoorClosed("ST-01", "C-1"))
            .isInstanceOf(HardwareException.class)
            .hasMessageContaining("falha ao fechar");

        assertThat(messages())
            .anyMatch(message -> message.contains("confirmDoorClosed")
                && message.contains("sucesso=false")
                && message.contains("falha ao fechar"));
    }

    @Test
    @DisplayName("consulta do estado da porta registra a chamada e o resultado")
    void testIsDoorClosedIsLogged() {
        when(delegate.isDoorClosed("ST-01", "C-1")).thenReturn(false);

        assertThat(hardwarePort.isDoorClosed("ST-01", "C-1")).isFalse();

        verify(delegate).isDoorClosed("ST-01", "C-1");
        assertThat(messages())
            .anyMatch(message -> message.contains("isDoorClosed")
                && message.contains("fechada=false"));
    }

    @Test
    @DisplayName("falha na consulta do estado da porta é registrada e propagada")
    void testIsDoorClosedFailureIsLogged() {
        when(delegate.isDoorClosed("ST-01", "C-1"))
            .thenThrow(new HardwareException("falha no sensor"));

        assertThatThrownBy(() -> hardwarePort.isDoorClosed("ST-01", "C-1"))
            .isInstanceOf(HardwareException.class)
            .hasMessageContaining("falha no sensor");

        assertThat(messages())
            .anyMatch(message -> message.contains("isDoorClosed")
                && message.contains("sucesso=false")
                && message.contains("falha no sensor"));
    }

    private List<String> messages() {
        return appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}
