# Auditoria e métrica por agente

> **Estado (2026-11-19):** implementado e testado nas duas partes. O AM guarda `agent` e `act` na auditoria de segurança e na auditoria central (igrp-platform-access-management!509). O starter grava-os no `audit_event` e na outbox, e conta as chamadas em `igrp.agent.calls` ([igrp-framework-auth-backend-monorepo!25](https://git.nosi.cv/igrp-3_0/igrp-auth/backend/igrp-framework-auth-backend-monorepo/-/merge_requests/25)).

## Dados registados

O starter guarda `agent` (actor atual) e `act` (cadeia com `sub`, `iss` e actores anteriores) em `audit_event` e no evento da outbox. O AM recebe ambos em `t_business_audit_event`. A API de consulta central expõe `act` como texto JSON, tal como `payload`.

A auditoria de segurança do AM também tem colunas `agent` e `act`, preenchidas pelo contexto autenticado ou pelo evento de emissão do token exchange. A cadeia não é duplicada no campo limitado `context_data`.

Para delegação, `agent` é `act.sub`. Para service accounts autónomas, sem `act`, é o `client_id`; no starter existe fallback para `sub`. Não se inventa uma cadeia de delegação para chamadas autónomas. Contas de serviço são identificadas como SERVICE_ACCOUNT, não inferidas a partir do nome do utilizador.

## Migrações e compatibilidade

- AM: `V31_1__audit_actor_chain.sql`.
- Starter: `V3__actor_chain.sql` (`db/igrp-audit`), no histórico Flyway próprio `igrp_audit_schema_history`.
- Eventos antigos, sem `act`, continuam aceites.
- Os hashes antigos são preservados. Novos hashes incluem a extensão quando os campos de actor estão presentes; modificar ou remover a cadeia invalida o hash.
- Atualizar o AM antes dos serviços produtores. Um AM antigo não sabe persistir o novo campo recebido.
- A alteração exige publicar/instalar a versão atualizada do starter e atualizar/reiniciar os consumidores. Editar o repositório não atualiza serviços já instalados.

## Métrica

O starter regista o contador Micrometer `igrp.agent.calls`, com tags `agent` e `service`. O filtro corre após autenticação e antes da autorização HTTP, contando pedidos autenticados que chegam a esse ponto, incluindo recusas de autorização posteriores. Não depende de `audit.record`, não conta pedidos anónimos e não conta redispatches async/error. Falhas durante a autenticação não têm actor autenticado e não são contadas.

O starter inclui Actuator. Usando a configuração normal de exposição e segurança de Actuator do serviço, a métrica fica disponível em:

```text
GET /actuator/metrics/igrp.agent.calls?tag=agent:agent-a
```

Para consulta HTTP, adicionar `metrics` à lista existente de `management.endpoints.web.exposure.include` e permitir o acesso apenas à monitorização na configuração de segurança do serviço. O starter não torna endpoints de gestão públicos. Serviços com security chain própria devem continuar a aplicar `RouteSecurity`.

Com `micrometer-registry-prometheus` no serviço e o endpoint Prometheus configurado, calcular a taxa de chamadas por segundo:

```promql
sum by (service, agent) (rate(igrp_agent_calls_total[5m]))
```

O contador aparece após a primeira chamada desse agente. Trata-se de um contador de chamadas, não de alertas de anomalia.

## Validação

No AM: `ActorAuditTest`, `SecurityAuditChainServiceTest` (preservação da cadeia, detecção de adulteração e compatibilidade dos eventos antigos) e `TokenExchangeIT` (coluna `agent`/`act` preenchida na emissão de um token trocado). No starter: `AgentIdentityTest` e `AgentCallMetricsFilterTest`.
