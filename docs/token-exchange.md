# Token exchange no AM

## Estado

Implementadas a política de troca e a seleção de claims por audiência no customizador JWT. O provider e o endpoint continuam a ser os do Spring Security. Foram adicionados testes para execução posterior; não foram executados testes nem compilação nesta entrega, por indicação do utilizador.

## Comportamento herdado

A inspeção do provider Spring Security 7.1.2 confirmou que ele autentica o cliente, exige o grant, resolve os tokens persistidos e verifica a sua validade. O actor é opcional, exceto quando `may_act` exige a sua apresentação. O contexto JWT contém o cliente solicitante e a autorização original. Os scopes são verificados contra o cliente; o AM acrescenta as restrições do subject e do destino. A autorização derivada é persistida sem refresh token.

Fonte: [OAuth2TokenExchangeAuthenticationProvider 7.1.2](https://github.com/spring-projects/spring-security/blob/7.1.2/oauth2/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2TokenExchangeAuthenticationProvider.java).

`SpringTokenExchangeProviderTest` caracteriza o provider real sem HTTP/base de dados. A certificação ponta a ponta do endpoint continua pendente de testes de integração.

## Configuração

O cliente A deve estar ativo e ter o grant `urn:ietf:params:oauth:grant-type:token-exchange`. Se também emitir um actor token, precisa de `client_credentials` e de uma service account ativa. Clientes federados podem ter ambos os grants no AM; o registo no IdP permanece `client_credentials`.

A audiência é exatamente o client ID de B, associado a uma aplicação ativa. Não são aceites aliases, múltiplas audiências ou o parâmetro `resource` nesta versão.

O limite da cadeia de actores pode ser configurado em `application.properties` ou configuração externa equivalente:

```properties
igrp.oauth2.token-exchange.max-actor-depth=5
```

Não é necessária uma lista de destinos permitidos por cliente. Um cliente autorizado para o grant pode solicitar uma troca para um destino conhecido e ativo, sujeita às validações dos tokens, scopes e direitos do utilizador nessa aplicação.

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

## Direitos e claims

- O utilizador deve estar ACTIVE ou TEMPORARY.
- A role ativa deve estar ACTIVE, ter uma atribuição não expirada e estar associada à aplicação B.
- A role e organização atuais devem corresponder aos claims do subject token; se mudaram, é necessário obter um novo subject token.
- Apenas a role ativa é utilizada. Não há união automática das restantes roles do utilizador.
- Permissões devem estar ACTIVE e pertencer à aplicação B, diretamente ou, para permissões legadas sem aplicação explícita, através de recursos ativos ligados a B.
- `permissions` contém `permission_key` quando existente; para permissões legadas, usa `name`.
- `resource_access` usa a chave B e contém apenas a role ativa autorizada.
- `is_super_admin` é false: a troca não transporta um bypass global.
- Scopes devem estar autorizados no subject, no cliente A e no cliente B. Scopes OAuth e permissões da aplicação são conceitos distintos; não há mapeamento implícito entre nomes de scopes e permission keys.
- Se `scope` não for enviado, o Spring tenta reutilizar os scopes originais; se não forem aceites no destino, enviar explicitamente o subconjunto pretendido.
- `sub` preserva o utilizador, `aud` identifica B e `act` identifica o actor atual, preservando os anteriores em objetos aninhados. O limite de profundidade inclui o actor atual.
- Não são copiados indiscriminadamente os claims originais, metadata ou atributos pessoais.

## Sessão e limites desta entrega

Para compatibilidade com o AM, exige-se uma sessão original ativa, reutiliza-se `sid`/`device_id` e não se chama o fluxo de criação de sessão interativa. A expiração é limitada pelos tokens apresentados, TTL do destino e prazos da sessão.

A auditoria existente recebe o evento TOKEN_ISSUED, utilizador, cliente e audiência. A coluna `agent`, a auditoria completa da cadeia e a avaliação integral da revogação de tokens derivados permanecem nas etapas 4–5 do plano. O starter não foi alterado.

## Erros

- `unauthorized_client`: cliente sem grant de troca.
- `invalid_target`: destino desconhecido, inativo ou sem aplicação ativa; formato de destino não suportado.
- `invalid_scope`: scopes fora dos limites do subject/cliente/destino.
- `invalid_grant`: token/actor/utilizador/sessão inválidos ou contexto de role alterado.
- `access_denied`: role ativa sem atribuição válida ou sem acesso à aplicação B.
- `invalid_request`: tipos de token não suportados ou cadeia demasiado profunda.

O Spring pode rejeitar o pedido antes do customizador, incluindo erros de autenticação, conversão de parâmetros e validação de `may_act`.
