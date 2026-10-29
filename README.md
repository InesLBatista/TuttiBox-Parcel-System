# Hw1-Tqs - Inês Batista, 124877
### Requisitos
- Java 21
- Docker e Docker Compose para os testes que usam Testcontainers e para os serviços locais
- Chromium do Playwright para os testes E2E (comando abaixo explicado)

Todos os comandos Maven são executados dentro de hw1/ com o Maven Wrapper incluído no projeto.

## Executar a aplicação
Arranque a base de dados PostgreSQL definida em `hw1/compose.yaml`. Por omissão, o container publica a porta `5433` no computador para evitar conflitos com uma instalação local de PostgreSQL que já use `5432`; a aplicação está configurada para essa porta:
```bash
cd hw1
docker compose up -d postgres
./mvnw spring-boot:run
```

A aplicação fica disponível em <http://localhost:8080>. Para parar a base de dados: docker compose stop postgres

Se necessário, usar outra porta no host, define POSTGRES_HOST_PORT ao iniciar o Compose e ajusta SPRING_DATASOURCE_URL para essa mesma porta ao iniciar a aplicação. Por exemplo, para usar a porta 5432: POSTGRES_HOST_PORT=5432 docker compose up -d postgres e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tuttibox ./mvnw spring-boot:run

## Testes
Os testes que iniciam PostgreSQL com Testcontainers precisam de Docker ativo. Para executar a suite completa, incluindo Cucumber, contratos, integrações, Playwright e a verificação mínima de cobertura:
```bash
cd hw1
./mvnw clean verify
```

Comandos para executar cada grupo isoladamente:
```bash
#testes unitários (exclui classes de integração, Cucumber, contrato e browser)
./mvnw -Dtest='**/*Test.java,!**/*IntegrationTest.java,!**/CucumberTest.java,!**/ApiContractTest.java,!**/PlaywrightEndToEndTest.java,!**/WebInterfaceUsabilityTest.java' test

#testes de integração e concorrência
./mvnw '-Dtest=**/*IntegrationTest.java,**/*AcceptanceTest.java' test

#testes de contrato da API
./mvnw -Dtest=ApiContractTest test

#testes BDD com Cucumber
./mvnw -Dtest=CucumberTest test

#testes E2E no browser (instalar Chromium uma vez por máquina)
./mvnw -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args='install --with-deps chromium' \
  -Dexec.classpathScope=test exec:java
./mvnw -Dtest=PlaywrightEndToEndTest test

#testes da interface sem browser
./mvnw -Dtest=WebInterfaceUsabilityTest test
```

Os nomes e diretórios das suites estão em hw1/src/test/java/tuttibox/hw1/. Os cenários Cucumber estão em hw1/src/test/resources/features/.

## Relatórios de testes e cobertura

Gera os relatórios a partir de `hw1/`:

```bash
cd hw1
./mvnw clean verify
```

Para abrir o relatório HTML completo do JaCoCo no browser em Linux:

```bash
xdg-open target/site/jacoco/index.html
```

O XML usado pelo SonarQube fica em hw1/target/site/jacoco/jacoco.xml

Os relatórios Surefire por classe ficam em hw1/target/surefire-reports/ a saída detalhada de Cucumber aparece no terminal e o resumo em hw1/target/surefire-reports/TEST-tuttibox.hw1.bdd.CucumberTest.xml 

Na CI, os artefactos surefire-reports e jacoco-coverage ficam disponíveis na página da execução do workflow

## SonarQube local
O projeto inclui o scanner Maven e uma configuração Compose opcional para SonarQube Community Build. Abre um terminal na pasta hw1/ e inicia o servidor:
```bash
cd hw1
docker compose --profile sonar up -d sonarqube
```

Quando o container estiver iniciado, abre <http://localhost:9000> no browser. Na primeira utilização, entra com admin/admin e altera a palavra-passe quando solicitado. Na interface, cria um token em My Account → Security → Generate Tokens

No terminal da pasta `hw1/`, gera a cobertura e executa a análise SonarQube com esse token:

```bash
./mvnw clean verify
./mvnw sonar:sonar -Dsonar.token=O_TOKEN_AQUI
```

Depois de a análise terminar com sucesso, abre o dashboard do projeto no browser: <http://localhost:9000/dashboard?id=tuttibox-hw1>. A página apresenta bugs, vulnerabilidades, code smells, métricas de qualidade e cobertura importada do JaCoCo.

Para não escrever o token no histórico da shell, define SONAR_TOKEN no ambiente e executa ./mvnw sonar:sonar

Para parar o servidor: docker compose --profile sonar stop sonarqube

Os dados persistem no volume sonarqube-data para arrancar novamente, é necessário repetir o comando up -d. O arranque inicial pode demorar alguns minutos. Esta configuração é para análise local e desenvolvimento.

## Playwright
Os testes E2E usam Playwright Java com Chromium e Testcontainers com PostgreSQL. A instalação do browser é feita na máquina local pelo comando da secção de testes; na CI, o workflow instala Chromium automaticamente antes de executar clean verify.