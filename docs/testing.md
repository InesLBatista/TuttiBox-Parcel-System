# Testes

O projeto tem testes unitários, de integração, concorrência, contrato, BDD, usabilidade e E2E; executar a suite completa a partir de `hw1/` com `./mvnw clean verify` requer Java 21 e Docker ativo para o PostgreSQL do Testcontainers, e os testes Playwright requerem Chromium instalado.

## Testes unitários

Validam regras e componentes isolados, para detetar regressões sem depender de uma aplicação ou base de dados completas.

| Classe | O que verifica e porquê |
| --- | --- |
| `model/CompartmentSizeTest` | Verifica compatibilidade e interpretação dos tamanhos S/M/L para impedir atribuições de cacifos demasiado pequenos. |
| `model/CompartmentTest` | Verifica estados e transições de compartimentos para proteger as regras de reserva, ocupação, libertação e manutenção. |
| `model/ParcelTest` | Verifica estados, histórico e prazos de encomendas para impedir transições inválidas do ciclo de vida. |
| `service/ParcelServiceTest` | Verifica drop-off, pickup e falhas de hardware com dependências simuladas para garantir que erros não alteram indevidamente o estado. |
| `service/PickupCodeTest` | Verifica geração, normalização, formato e hash dos códigos para garantir validação consistente sem guardar o código em claro. |
| `service/StationSearchServiceTest` | Verifica ordenação por distância e disponibilidade por tamanho para apresentar estações úteis ao destinatário. |
| `fake/InMemoryGeocodingFakeTest` | Verifica respostas e falhas configuráveis do geocoding falso para tornar os testes e demonstrações reproduzíveis. |
| `fake/InMemoryHardwareFakeTest` | Verifica abertura, fecho e falhas simuladas do hardware para cobrir comportamentos dos cacifos sem equipamento real. |
| `geocoding/NominatimGeocodingAdapterTest` | Verifica pedidos ao Nominatim e tradução de erros, timeouts e respostas inválidas para proteger a integração externa. |
| `port/LoggingHardwarePortTest` | Verifica que chamadas e erros do hardware são registados e propagados para facilitar diagnóstico operacional. |
| `config/ParcelLifecycleSchedulerTest` | Verifica que o scheduler delega a aplicação de prazos no serviço para manter uma única regra de expiração. |
| `Hw1ApplicationTests` | Verifica que o contexto Spring da aplicação inicia para detetar falhas básicas de configuração. |

## Testes de integração e aceitação

Exercitam várias camadas em conjunto, incluindo controladores HTTP, serviços, persistência PostgreSQL, seed e hardware simulado.

| Classe | O que verifica e porquê |
| --- | --- |
| `config/SeedDataIntegrationTest` | Verifica que o seed cria três estações e 18 compartimentos sem duplicar dados em execuções repetidas. |
| `controller/StationSearchControllerIntegrationTest` | Verifica pesquisa de estações, disponibilidade e erros do geocoding pela API para validar a integração entre HTTP e serviço. |
| `controller/RegisterParcelIntegrationTest` | Verifica validação do pedido e reserva do compartimento compatível mais pequeno para garantir registos corretos. |
| `controller/ParcelControllerIntegrationTest` | Verifica o registo de encomendas pela API e a resposta com tracking para validar o contrato funcional do parceiro. |
| `controller/CourierDropOffIntegrationTest` | Verifica as etapas de abertura e confirmação do drop-off pela API, incluindo falhas de hardware e pedidos inválidos. |
| `controller/RecipientPickUpIntegrationTest` | Verifica pickup por código, uso único e recuperação de erros para evitar entregas ou libertações indevidas. |
| `controller/ParcelSizeValidationIntegrationTest` | Verifica que o tamanho efetivo do compartimento é compatível com a encomenda antes do drop-off. |
| `controller/DuplicateParcelRegistrationIntegrationTest` | Verifica que pedidos repetidos criam encomendas independentes e não reutilizam reservas de forma incorreta. |
| `controller/GlobalExceptionHandlerIntegrationTest` | Verifica que erros de domínio, ausência de recursos e falhas de hardware recebem códigos HTTP adequados. |
| `controller/OperatorSupervisionIntegrationTest` | Verifica filtros, remoção de encomendas expiradas e gestão do estado dos compartimentos pela API do operador. |
| `controller/ParcelHistoryIntegrationTest` | Verifica que o histórico de transições é consultável e contém as mudanças da encomenda. |
| `controller/ParcelLifecycleEdgeCasesIntegrationTest` | Verifica cancelamento, expiração e devolução para proteger transições menos comuns do ciclo de vida. |
| `controller/ParcelLifecycleAcceptanceTest` | Verifica cenários de aceitação do ciclo de vida através da API e da persistência. |
| `controller/CourierTerminalPageIntegrationTest` | Verifica que a página do courier é servida com os elementos do fluxo de depósito. |
| `controller/RecipientTerminalPageIntegrationTest` | Verifica que a página de pickup do destinatário está disponível. |
| `controller/TrackingPageIntegrationTest` | Verifica que a página de consulta de tracking está disponível. |
| `controller/OperatorTerminalPageIntegrationTest` | Verifica a página de supervisão e a ligação a partir da página inicial. |
| `service/ParcelServiceIntegrationTest` | Verifica o serviço com repositórios e hardware falso para confirmar que só o fecho validado conclui o drop-off. |
| `service/ParcelDeadlineIntegrationTest` | Verifica cancelamento e expiração quando os prazos vencem e a preservação de encomendas ainda dentro do prazo. |

## Testes de concorrência

| Classe | O que verifica e porquê |
| --- | --- |
| `concurrency/ApiConcurrencyIntegrationTest` | Envia operações concorrentes pela API para detetar condições de corrida nas transições e reservas. |
| `concurrency/ParcelLifecycleConcurrencyIntegrationTest` | Executa operações concorrentes no ciclo de vida para garantir que a encomenda não sofre transições incompatíveis. |
| `service/ParcelAllocationConcurrencyIntegrationTest` | Tenta reservar simultaneamente o último compartimento e verifica que nunca é atribuído a duas encomendas. |

## Testes de contrato

| Classe | O que verifica e porquê |
| --- | --- |
| `contract/ApiContractTest` | Compara respostas JSON da API com os schemas em `src/test/resources/contracts/` para detetar alterações incompatíveis para os clientes. |

## Testes BDD com Cucumber

Os cenários em `hw1/src/test/resources/features/` descrevem comportamentos observáveis e ligam cada regra a uma necessidade do sistema; a suite é executada por `bdd/CucumberTest`.

| Feature | O que cobre e porquê |
| --- | --- |
| `uc1_station_search.feature` | Pesquisa estações por morada, disponibilidade e erros de geocoding para validar a descoberta de cacifos. |
| `uc2_parcel_registration.feature` | Registo, seleção do menor compartimento compatível, validação e concorrência para proteger a alocação. |
| `uc3_courier_dropoff.feature` | Abertura, confirmação de fecho, prazos e falhas do courier para garantir um depósito seguro. |
| `uc4_recipient_pickup.feature` | Pickup com código válido, inválido, expirado ou repetido para garantir que só o destinatário autorizado liberta o cacifo. |
| `uc5_tracking_and_lifecycle.feature` | Estado, histórico, cancelamento, expiração e devolução para tornar o percurso da encomenda verificável. |
| `uc6_operator_supervision.feature` | Filtros, remoção de expiradas e manutenção de compartimentos para validar as operações do operador. |
| `uc7_deadlines.feature` | Aplicação manual e agendada dos prazos para evitar encomendas a bloquear cacifos indefinidamente. |
| `uc8_web_terminals_and_seed.feature` | Disponibilidade das páginas e idempotência dos dados de demonstração para preparar a utilização do sistema. |

## Usabilidade e testes E2E

| Classe | O que verifica e porquê |
| --- | --- |
| `usability/WebInterfaceUsabilityTest` | Verifica navegação, rótulos acessíveis, layout responsivo e conteúdo dos cinco terminais para reduzir erros de utilização. |
| `usability/PlaywrightEndToEndTest` | Usa Chromium real para percorrer páginas, pesquisar estações, completar depósito e pickup e gerir compartimentos pela interface. |
