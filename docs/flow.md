# Flow de demonstração da interface

Este percurso demonstra as cinco páginas web e o ciclo de vida de duas encomendas: uma percorre pesquisa, depósito, tracking e pickup; a segunda permite mostrar a lista e remoção de expiradas na supervisão. O [enunciado](guidelines.md) define o UC2 do parceiro através da REST API, sem exigir uma página web de registo, pelo que esse passo é preparado pela API.

## Preparação

1. A partir de `hw1/`, inicia a base de dados e a aplicação:

   ```bash
   docker compose up -d postgres
   TUTTIBOX_GEOCODING_PROVIDER=memory ./mvnw spring-boot:run
   ```

   O servidor inicia em <http://localhost:8080> e carrega automaticamente três estações de Aveiro, com compartimentos S, M e L.

2. Mantém o terminal da aplicação aberto: durante o depósito, a notificação falsa escreve o código de recolha nos logs. O provider `memory` permite pesquisar as moradas de demonstração sem depender de um serviço externo.

## Encomenda principal: pesquisa, depósito, tracking e recolha

1. Abre <http://localhost:8080/>. Na página **Encontrar estação TuttiBox**, escreve `Aveiro` em **Morada** e clica em **Procurar estações**.
2. Observa a tabela ordenada por distância, com código e disponibilidade de compartimentos S/M/L. Usa o link **Terminal do courier** para mudar de página.
3. De acordo com o canal definido para o UC2, o parceiro regista a primeira encomenda através da REST API, num terminal separado:

   ```bash
   cd hw1
   curl -sS -X POST http://localhost:8080/api/parcels \
     -H 'Content-Type: application/json' \
     -d '{"stationId":"ST-AVEIRO-CENTRO","compartmentSize":"M","recipientContact":"demo@example.com","partnerId":"PARCEIRO-DEMO"}'
   ```

   Guarda da resposta os valores `trackingCode`, `assignedStationCode` e `assignedCompartmentCode`; o código de tracking também é necessário nos passos seguintes. Um pedido M deve reservar um compartimento M livre na estação indicada.

4. Na página **Terminal do courier**, preenche **Tracking code** com o valor da resposta, **Estação** com `ST-AVEIRO-CENTRO`, **Compartimento** com `assignedCompartmentCode` e **Identificador do courier** com `COURIER-DEMO`; clica em **Abrir compartimento**.
5. Mostra o passo de confirmação, que simula colocar a encomenda e fechar a porta, e clica em **Confirmar fecho e concluir**; a página confirma o depósito.
6. No terminal da aplicação, encontra a linha `código de recolha enviado` e copia o código de oito caracteres gerado para esta encomenda.
7. Usa **Consultar tracking** no rodapé para abrir a página de tracking, escreve o `trackingCode` e clica em **Consultar**; confirma o estado `READY_FOR_PICKUP` e a transição `CREATED → READY_FOR_PICKUP`.
8. Usa o link **Terminal do destinatário**, escreve o código de recolha e clica em **Recolher encomenda**; confirma a mensagem de compartimento aberto e simula retirar a encomenda e fechar a porta.
9. Volta a **Consultar tracking**, pesquisa o mesmo código e confirma o estado `PICKED_UP` e o histórico atualizado. A recolha liberta o compartimento.

## Supervisão do operador e manutenção

1. Abre **Supervisão do operador** através do rodapé ou diretamente em <http://localhost:8080/operator.html>.
2. Preenche **Código da estação** com `ST-AVEIRO-CENTRO`, mantém ou escreve `OPERATOR-DEMO` em **Identificador do operador**, escolhe `PICKED_UP` em **Estado das encomendas** e clica em **Atualizar** para demonstrar o filtro.
3. Altera **Estado das encomendas** para **Todos os estados** e clica em **Atualizar**; percorre as tabelas **Encomendas**, **Encomendas expiradas** e **Compartimentos**, e observa que a encomenda principal está `PICKED_UP` e que o compartimento correspondente voltou a `FREE`.
4. Na tabela de compartimentos, escolhe um compartimento livre que não esteja a ser usado no fluxo (por exemplo, um `C-S...`) e clica em **Fora de serviço**; confirma o estado `OUT_OF_SERVICE`.
5. Clica em **Voltar ao serviço** no mesmo compartimento e confirma que regressa a `FREE`.

## Encomenda expirada e remoção

O sistema não oferece um botão web para forçar a expiração; normalmente o scheduler aplica a regra quando o prazo termina. Para demonstrar imediatamente a operação de remoção, prepara uma segunda encomenda e usa o endpoint de expiração, que simula essa situação.

1. Regista uma segunda encomenda M pela mesma API, usando outro destinatário, por exemplo `demo-expirada@example.com`, e guarda os valores `trackingCode` e `assignedCompartmentCode`:

   ```bash
   cd hw1
   curl -sS -X POST http://localhost:8080/api/parcels \
     -H 'Content-Type: application/json' \
     -d '{"stationId":"ST-AVEIRO-CENTRO","compartmentSize":"M","recipientContact":"demo-expirada@example.com","partnerId":"PARCEIRO-DEMO"}'
   ```

2. Repete na página **Terminal do courier** os passos de abertura e confirmação para a segunda encomenda, introduzindo o seu tracking e compartimento; a encomenda tem de chegar a `READY_FOR_PICKUP` antes de ser expirada.
3. Num terminal, substitui `<TRACKING_DA_SEGUNDA_ENCOMENDA>` pelo tracking guardado e simula o vencimento do prazo:

   ```bash
   curl -i -X POST 'http://localhost:8080/api/parcels/<TRACKING_DA_SEGUNDA_ENCOMENDA>/expire?actor=DEMO'
   ```

4. Na página do operador, volta a clicar em **Atualizar**. A segunda encomenda deverá aparecer em **Encomendas expiradas**; clica em **Remover** nessa linha.
5. Confirma a mensagem de sucesso e atualiza novamente: a encomenda passa a `RETURNED` e o seu compartimento fica livre.

## Páginas e ações demonstradas

- `/` — pesquisa de estações e disponibilidade por tamanho.
- `/courier.html` — depósito em dois passos e confirmação do fecho.
- `/tracking.html` — estado atual e histórico de transições.
- `/recipient.html` — recolha com código de utilização única.
- `/operator.html` — filtros de encomendas, listagem de expiradas, remoção e manutenção de compartimentos.

As páginas não implementam autenticação; os identificadores de parceiro, courier e operador são usados para registo das operações e do histórico.
