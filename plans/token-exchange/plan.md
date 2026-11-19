# Plano de execução — Token Exchange no AM

Estado: etapas 2–3 implementadas; etapa 1 analisada por inspeção do provider Spring, com teste de caracterização executado. `clean compile test` passou no AM em 2026-11-19: 1162 testes, zero falhas, erros ou testes ignorados. Validação HTTP/base de dados via Failsafe pendente. Ver [contrato e configuração](../../docs/token-exchange.md).

## Objetivo e âmbito

Completar e validar o token exchange RFC 8693 no endpoint existente `POST /oauth2/token`, calcular os direitos do utilizador para a audiência de destino e registar a delegação na auditoria.

A regra central é: o token emitido representa o utilizador perante o serviço B, com as permissões que lhe são aplicáveis em B. O actor não acrescenta permissões ao utilizador.

Este plano abrange apenas o AM. Ficam fora do âmbito:

- Alterações no starter.
- Interceptores HTTP e cache de tokens nos serviços consumidores.
- Métricas de chamadas por agente no starter.
- Alertas de anomalia.

## Situação identificada

- O discovery anuncia o grant de token exchange, disponibilizado pela infraestrutura Spring utilizada pelo AM.
- Ainda não foi validada uma troca completa com `subject_token`, `actor_token` e audiência de destino.
- `JwtTokenConfig` passa o client ID do solicitante para o enriquecimento dos claims.
- `ClaimsEnrichmentService` calcula as permissões do utilizador a partir da role ativa, sem filtro pela audiência.
- `resource_access` agrupa as roles pela chave do cliente solicitante, sem selecionar as roles da aplicação de destino.
- A auditoria de segurança do AM não tem uma coluna própria `agent`.
- O fluxo de `client_credentials` com service accounts já existe e deve ser preservado.

Esta avaliação resulta da inspeção do código e dos testes existentes, não de uma chamada real de token exchange.

## 1. Validar o comportamento herdado do Spring

- [ ] Criar um teste de integração com utilizador, cliente A, service account de A e aplicação/cliente B.
- [ ] Autorizar A a utilizar `urn:ietf:params:oauth:grant-type:token-exchange`.
- [ ] Executar a troca no contexto de teste, apresentando `subject_token`, `actor_token` e `audience`.
- [ ] Verificar como o provider resolve `sub`, `aud`, `act`, scopes e autorização persistida.
- [ ] Verificar a compatibilidade com `JwtTokenConfig` e `SessionIssuanceService`.
- [ ] Identificar os pontos de extensão necessários, reutilizando o provider existente sempre que possível.

**Resultado esperado:** determinar o suporte efetivo do framework, sem criar um segundo endpoint nem duplicar o provider sem necessidade.

## 2. Definir e implementar a política de troca

Contrato inicial proposto:

| Elemento | Regra |
| --- | --- |
| Cliente solicitante | Autenticado, ativo e autorizado para o grant. |
| `subject_token` | Access token emitido pelo AM, válido e associado a um utilizador ativo. |
| `actor_token` | Opcional. Se enviado, access token válido da service account associada ao cliente solicitante, com `actor_token_type` obrigatório. Sem ele, o AM usa o cliente autenticado como actor. `may_act`, quando presente, continua sujeito à validação do Spring. |
| `audience` | Um único destino conhecido e ativo. |
| Destinos | Sem lista de destinos por cliente; validar o destino e os direitos do utilizador nessa aplicação. |
| Scopes | Limitados pela autorização original e pela política aplicável ao destino, sem ampliação implícita. |
| Cadeia de actores | Preservada e estendida com o actor atual, com limite de profundidade. |

- [ ] Implementar o mapeamento `audience → cliente/recurso → aplicação`.
- [ ] Validar os tokens, o utilizador, o actor e a correspondência entre actor e cliente autenticado.
- [ ] Validar o destino e os scopes solicitados.
- [ ] Preservar a cadeia de actores e aplicar o limite de profundidade.
- [ ] Devolver erros OAuth coerentes para tokens inválidos, destinos desconhecidos e trocas não autorizadas.
- [ ] Evitar a exposição de tokens ou detalhes sensíveis nos erros.

**Resultado esperado:** possuir um token de utilizador e um token de serviço não permite, por si só, delegar acesso a qualquer aplicação.

## 3. Calcular os claims para a aplicação de destino

Separar o enriquecimento de token exchange do comportamento dos grants existentes. Introduzir um contexto explícito contendo:

```text
utilizador
cliente solicitante
actor
aplicação/audiência de destino
scopes autorizados
sessão de origem
```

- [ ] Resolver as atribuições de roles válidas do utilizador na aplicação B.
- [ ] Aplicar o contexto de organização e a política de role ativa utilizada pelo AM.
- [ ] Excluir roles e permissões inativas, expiradas ou alheias ao destino.
- [ ] Calcular as permissões efetivas de B.
- [ ] Preencher `resource_access` com a chave correspondente a B.
- [ ] Preservar `sub` como o utilizador e emitir `aud` para B.
- [ ] Tratar explicitamente `selectedRole`, `org` e `is_super_admin`, evitando transportar privilégios globais automaticamente.
- [ ] Garantir que os direitos do actor não ampliam os direitos do utilizador.

**Decisão implementada:** respeitar a role ativa, com atribuição válida e associação à aplicação B; não reunir as restantes roles. Exigir que role e organização continuem iguais às do subject token. `is_super_admin` é false no token derivado.

### Entrega das etapas 1–3

- Provider Spring reutilizado e inspecionado; teste de caracterização executado com sucesso.
- Audiência resolvida pelo client ID do destino, sem allowlist por cliente.
- Actor opcional, validação da sua service account quando apresentado e limite da cadeia.
- Scopes limitados pelo subject, cliente solicitante e destino.
- Claims por aplicação com role ativa e permissões ativas do destino.
- Clientes federados admitem o grant adicional no AM.
- Compatibilidade mínima de sessão: reutilizar sid, validar a sessão original e limitar expiração, sem criar um novo login.
- Testes de política escritos e documentação adicionada. Os checkboxes de validação abaixo só devem ser concluídos após execução e verificação.

**Resultado esperado:** o token destinado a B contém os direitos efetivos do utilizador em B, sem claims associados incorretamente a A. Não deve atribuir todas as permissões existentes na aplicação B.

## 4. Integrar expiração, sessão e revogação

- [ ] Limitar a validade do token trocado pela validade dos tokens apresentados e pela política do destino.
- [ ] Definir o vínculo à sessão original do utilizador.
- [ ] Evitar criar uma nova sessão interativa em cada troca.
- [ ] Rejeitar trocas quando a sessão original estiver revogada ou expirada.
- [ ] Verificar introspection, logout e revogação para tokens derivados.
- [ ] Não emitir refresh tokens para token exchange nesta primeira versão.
- [ ] Preservar login, refresh e `client_credentials`.

**Resultado esperado:** a troca não prolonga indevidamente a sessão nem permite contornar a revogação no AM.

A invalidação imediata em serviços que validam JWT apenas localmente continua a depender do mecanismo de validação desses serviços. Este plano não altera os consumidores.

## 5. Completar a auditoria do AM

- [ ] Adicionar uma coluna separada `agent` para a identidade do actor atual.
- [ ] Adicionar um campo estruturado para a cadeia de actores.
- [ ] Registar utilizador, cliente solicitante, audiência, scopes, resultado e motivo de rejeição.
- [ ] Registar eventos de troca bem-sucedida e rejeitada.
- [ ] Atualizar DTOs, consultas e exportações afetados.
- [ ] Adaptar a serialização e a validação da cadeia de integridade, preservando a verificabilidade dos registos históricos.
- [ ] Garantir que os tokens apresentados ou emitidos nunca são persistidos na auditoria.

**Resultado esperado:** distinguir quem é o utilizador, quem agiu por ele e qual foi o destino.

## 6. Validar e documentar o contrato

Cobertura de testes:

- [ ] A → B com `sub`, `aud`, `act` e permissões corretos.
- [ ] Exclusão de roles e permissões alheias a B, incluindo direitos exclusivos de A e C.
- [ ] Utilizador sem acesso a B.
- [ ] Actor incompatível com o cliente autenticado.
- [ ] Cliente sem grant ou utilizador sem acesso ao destino.
- [ ] Tokens expirados, revogados ou de emissor não aceite.
- [ ] Roles e atribuições inválidas.
- [ ] Tentativa de ampliar scopes.
- [ ] Trocas sucessivas e limite da cadeia de actores.
- [ ] Sessão revogada, introspection e auditoria.
- [ ] Regressão de login, refresh e service accounts.

Documentação:

- [ ] Registo dos clientes e associação da audiência à aplicação de destino.
- [ ] Pedido `curl` completo e resposta esperada.
- [ ] Claims emitidos e respetiva semântica.
- [ ] Erros possíveis e limites de sessão/revogação.

## Entregas propostas

1. **Contrato e política:** caracterização do provider e validação dos clientes e destinos.
2. **Claims por audiência:** resolução de B e cálculo dos direitos efetivos do utilizador.
3. **Sessão e auditoria:** expiração, revogação, actor e cadeia de integridade.
4. **Validação e documentação:** testes de integração e contrato de utilização.

## Critério de conclusão

Um teste de integração deve demonstrar que A consegue obter um token para B em nome do utilizador, com apenas os direitos aplicáveis a B, actor auditável e política de sessão/expiração respeitada.

Os fluxos existentes devem continuar a passar nos testes de regressão. Nenhuma alteração ao starter faz parte desta entrega.
