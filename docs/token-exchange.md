# Token exchange no AM

## Estado

Implementado no AM (v3-20, #28): a política de troca no `TokenExchangeClaimsService`, aplicada pelo `IgrpTokenCustomizer`. O provider e o endpoint continuam a ser os do Spring Security. O token trocado é fino como todos os outros (#33, spec "Tokens"). A parte do starter fica por fazer: troca automática ao chamar outro serviço, cache por utilizador e destino, e métrica por agente.

## Comportamento herdado

A inspeção do provider Spring Security 7.1.2 confirmou que ele autentica o cliente, exige o grant, resolve os tokens persistidos e verifica a sua validade. O actor é opcional, exceto quando `may_act` exige a sua apresentação. O contexto JWT contém o cliente solicitante e a autorização original. Os scopes são verificados contra o cliente; o AM acrescenta as restrições do subject e do destino. A autorização derivada é persistida sem refresh token.

Fonte: [OAuth2TokenExchangeAuthenticationProvider 7.1.2](https://github.com/spring-projects/spring-security/blob/7.1.2/oauth2/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2TokenExchangeAuthenticationProvider.java).

`SpringTokenExchangeProviderTest` caracteriza o provider real sem HTTP/base de dados. `TokenExchangeIT` testa o endpoint de ponta a ponta, com Postgres e a auditoria.

## Configuração

O cliente A deve estar ativo e ter o grant `urn:ietf:params:oauth:grant-type:token-exchange`. Se também emitir um actor token, precisa de `client_credentials` e de uma service account ativa. Clientes federados podem ter ambos os grants no AM; o registo no IdP permanece `client_credentials`.

A audiência é exatamente o client ID de B, associado a uma aplicação ativa. Não são aceites aliases, múltiplas audiências ou o parâmetro `resource` nesta versão.

O limite da cadeia de actores pode ser configurado em `application.properties` ou configuração externa equivalente:

```properties
igrp.oauth2.token-exchange.max-actor-depth=5
```

Não é necessária uma lista de destinos permitidos por cliente. Um cliente autorizado para o grant pode solicitar uma troca para um destino conhecido e ativo, sujeita às validações dos tokens e scopes. O que o utilizador pode fazer em B decide-o B, pelo conjunto de permissões do utilizador na aplicação de B.

## Pedido sem actor token

```bash
curl -X POST 'https://am.exemplo.cv/oauth2/token' \
  -u 'servico-a:SEGREDO' \
  --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
  --data-urlencode 'subject_token=TOKEN_DO_UTILIZADOR' \
  --data-urlencode 'subject_token_type=urn:ietf:params:oauth:token-type:access_token' \
  --data-urlencode 'audience=servico-b' \
  --data-urlencode 'scope=faturas.read'
```

O AM deriva o actor do client ID autenticado. Se existir `actor_token`, também é obrigatório enviar `actor_token_type=urn:ietf:params:oauth:token-type:access_token`. Nesse caso, o actor deve ser um token `client_credentials` emitido pelo AM para a service account do mesmo cliente solicitante, e `act.sub` será o ID dessa conta.

Somente access tokens são aceites como entrada e saída. Não há suporte a tokens externos ou ID tokens nesta versão.

## Claims do token trocado

- O utilizador deve estar ACTIVE ou TEMPORARY. Depois de um logout ou kill, o token do utilizador deixa de estar no armazém de autorizações e o Spring recusa a troca com `invalid_grant`.
- `sub` é o utilizador, `aud` é B (só B) e `azp` é o cliente A.
- `act` identifica o actor atual e preserva os anteriores em objetos aninhados. O limite de profundidade inclui o actor atual. `act` não conta para a autorização: fica na auditoria.
- Os claims de identidade são os de qualquer token de utilizador (`is_super_admin` e, conforme os scopes, `name`, `preferred_username`, `email`), e o `device_id` do token original é mantido.
- **Não há** `permissions`, `resource_access`, `selectedRole`, `org` nem `sid`. B lê as permissões do utilizador para a sua aplicação através do starter (Redis, e `GET /api/users/me/permissions` numa falha).
- Scopes devem estar autorizados no subject, no cliente A e no cliente B. Scopes OAuth e permissões da aplicação são conceitos distintos.
- A expiração é a mais curta entre a do token do utilizador, a do actor token e o TTL de acesso do cliente B.
- Não há refresh token.

A auditoria regista TOKEN_ISSUED com o utilizador, o cliente, a audiência, `agent` e a cadeia `act`. A persistência está descrita em [agent-audit.md](agent-audit.md).

## Erros

- `unauthorized_client`: cliente sem grant de troca.
- `invalid_target`: destino desconhecido, inativo ou sem aplicação ativa; formato de destino não suportado.
- `invalid_scope`: scopes fora dos limites do subject/cliente/destino.
- `invalid_grant`: token do utilizador ou actor inválido, revogado ou expirado; utilizador indisponível; issuer diferente.
- `invalid_request`: tipos de token não suportados ou cadeia demasiado profunda.

O Spring pode rejeitar o pedido antes do customizador, incluindo erros de autenticação, conversão de parâmetros e validação de `may_act`.
