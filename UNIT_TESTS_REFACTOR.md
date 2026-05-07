# Unit Tests para feature/config-user-by-id Refactor

## Resumo
Testes unitários abrangentes criados para cobrir o refactor que substitui `externalId` (String) por `id` interno (Integer) para identificação de usuários.

---

## Testes Criados

### 1. **RespondUserInvitationCommandHandlerTest** (Atualizado)
**Localização:** `src/test/java/cv/igrp/platform/access_management/users/application/commands/`

**Objetivo:** Testar o handler que processa respostas de convites com o novo scheme de Integer ID.

**Novos Testes Adicionados:**

| Teste | Descrição |
|-------|-----------|
| `handle_validIntegerUserIdFromProfile_success()` | Valida conversão de String para Integer do `profile.id()` |
| `handle_invalidIntegerUserIdFromProfile_throwsException()` | Testa erro quando `profile.id()` não é um Integer válido |
| `handle_userFoundByIntegerId_updatesExistingUser()` | Testa lookup de usuário existente usando Integer ID |

**Casos Cobertos:**
- ✅ Aceitar convite e criar novo usuário com Integer ID
- ✅ Aceitar convite e atualizar usuário existente usando lookup por Integer
- ✅ Erro ao converter subject inválido para Integer
- ✅ Rejeitar convite sem criar usuário
- ✅ Validação de método de autenticação
- ✅ Verificação de identifier (email/phone)
- ✅ Salvamento de user identifiers

---

### 2. **ScopeAspectTest** (Novo)
**Localização:** `src/test/java/cv/igrp/platform/access_management/shared/security/`

**Objetivo:** Testar o aspect que injeta o userId (agora Integer) no contexto de scope.

**Testes:**

| Teste | Descrição |
|-------|-----------|
| `testInjectUserIdAsInteger()` | Valida injeção de Integer userId do ActorPrincipal |
| `testInjectDifferentUserId()` | Testa múltiplos Integer userIds diferentes |
| `testInjectSuperAdminFlag()` | Valida propagação da flag de superadmin |
| `testApplyWithMultipleArgs()` | Testa aspect com múltiplos argumentos |
| `testApplyWithEmptySets()` | Testa com departamentos/aplicações vazios |
| `testProceededWithModifiedArgs()` | Valida que args modificadas são passadas ao proceed() |

**Casos Cobertos:**
- ✅ Injeção de Integer userId no ScopeContext
- ✅ Configuração correta de department/application/role IDs
- ✅ Propagação da flag superAdmin
- ✅ Execução do JoinPoint.proceed com args modificadas

---

### 3. **ScopeServiceTest** (Novo)
**Localização:** `src/test/java/cv/igrp/platform/access_management/shared/infrastructure/service/`

**Objetivo:** Testar o serviço que extrai ActorPrincipal com Integer ID do contexto de segurança.

**Testes:**

| Teste | Descrição |
|-------|-----------|
| `testGetActorReturnIntegerId()` | Testa parsing de String subject para Integer ID |
| `testGetActorNoAuthenticationThrows()` | Valida erro sem autenticação |
| `testGetActorWithDifferentIntegerIds()` | Testa múltiplos Integer IDs |
| `testIsSuperAdminUsesIntegerId()` | Valida query JDBC com Integer parameter |
| `testIsNotSuperAdmin()` | Testa usuário não-superadmin |
| `testGetVisibleDepartmentIdsForSuperAdmin()` | Valida escopo de departamentos para superadmin |
| `testGetVisibleDepartmentIdsUsesCache()` | Testa cache de departamentos visíveis |
| `testGetVisibleRoleIdsForSuperAdmin()` | Valida escopo de roles para superadmin |
| `testGetVisibleApplicationIds()` | Testa aplicações visíveis para departamentos |
| `testActorPrincipalStructure()` | Valida estrutura do record ActorPrincipal |
| `testGetActorParseFailure()` | Testa falha na conversão Integer.parseInt() |

**Casos Cobertos:**
- ✅ Conversão de String JWT subject para Integer ID
- ✅ Queries JDBC usando Integer ID parameter
- ✅ Lógica de visibilidade para superadmin vs usuários regulares
- ✅ Caching de departamentos/aplicações/roles
- ✅ Tratamento de erro para subject inválido

---

### 4. **UserIdRefactorIntegrationTest** (Novo)
**Localização:** `src/test/java/cv/igrp/platform/access_management/shared/security/`

**Objetivo:** Testar a conversão end-to-end de String para Integer ID através do pipeline.

**Testes:**

| Teste | Descrição |
|-------|-----------|
| `testJwtSubjectToIntegerConversion()` | Valida conversão básica String → Integer |
| `testValidNumericSubjects()` | Testa strings numéricas válidas |
| `testInvalidNonNumericSubjects()` | Valida rejeição de strings não-numéricas |
| `testNegativeSubject()` | Testa números negativos |
| `testIntegerBounds()` | Testa Integer.MIN_VALUE e MAX_VALUE |
| `testOverflowSubject()` | Valida rejeição de overflow |
| `testProfileIdExtractionWorkflow()` | Simula pipeline completo do handler |
| `testProfileInvalidIdConversion()` | Testa profile com ID inválido |
| `testScopeContextIntegerId()` | Valida storage no ScopeContext |
| `testActorPrincipalIdConversion()` | Valida conversão em ActorPrincipal |
| `testRepositoryIntegerIdParameter()` | Documenta mudança de API de repository |
| `testScopeContextUserIdPropagation()` | Testa propagação ao longo do request |
| `testUserProfileExternalIdBackwardCompatibility()` | Valida compatibilidade backward |

**Casos Cobertos:**
- ✅ Conversão de String para Integer em todos os pontos
- ✅ Validação de bounds e overflow
- ✅ Tratamento de erros de conversão
- ✅ Propagação de Integer ID através do pipeline
- ✅ Compatibilidade backward com getExternalId()

---

## Executar os Testes

### Todos os testes do refactor:
```bash
mvn test -Dtest=RespondUserInvitationCommandHandlerTest,ScopeAspectTest,ScopeServiceTest,UserIdRefactorIntegrationTest
```

### Testes específicos por classe:
```bash
mvn test -Dtest=RespondUserInvitationCommandHandlerTest
mvn test -Dtest=ScopeAspectTest
mvn test -Dtest=ScopeServiceTest
mvn test -Dtest=UserIdRefactorIntegrationTest
```

### Testes específicos por método:
```bash
mvn test -Dtest=ScopeServiceTest#testGetActorReturnIntegerId
```

### Com relatório de cobertura:
```bash
mvn clean test -Dtest=RespondUserInvitationCommandHandlerTest,ScopeAspectTest,ScopeServiceTest,UserIdRefactorIntegrationTest jacoco:report
```

---

## Tecnologias Utilizadas

- **Spring Test** - Framework de testes Spring Boot
- **JUnit 5** - Framework de testes
- **Mockito** - Mocking de dependências
- **@ExtendWith(MockitoExtension.class)** - Integração Mockito com JUnit 5
- **@InjectMocks** - Injeção automática de mocks
- **@Mock** - Criação de mocks

---

## Cobertura de Componentes

### Componentes do Refactor Testados:
1. ✅ **RespondUserInvitationCommandHandler**
   - Conversão de `profile.id()` (String) para Integer
   - Lookup de usuário por Integer ID
   - Tratamento de erros de conversão

2. ✅ **ScopeAspect**
   - Injeção de Integer userId via `scopeService.getActor().id()`
   - Propagação para ScopeContext

3. ✅ **ScopeService**
   - Parsing de JWT subject (String) para Integer ID
   - Queries JDBC com Integer parameter
   - ActorPrincipal com Integer id

4. ✅ **ScopeContext**
   - Storage e retrieval de Integer userId

5. ✅ **ActorPrincipal**
   - Record com Integer id

6. ✅ **String → Integer Conversion Pipeline**
   - JWT subject parsing
   - Validação de bounds
   - Tratamento de erros

---

## Casos de Teste Críticos

| Caso | Impacto | Status |
|------|---------|--------|
| Conversão inválida de subject | Alto | ✅ Testado |
| Overflow de Integer | Alto | ✅ Testado |
| Lookup de usuário por Integer | Alto | ✅ Testado |
| Injeção de scope com Integer | Alto | ✅ Testado |
| Query JDBC com Integer parameter | Alto | ✅ Testado |
| Backward compatibility | Médio | ✅ Testado |
| Cache de scope | Médio | ✅ Testado |
| Múltiplos Integer IDs | Médio | ✅ Testado |

---

## Próximos Passos

1. ✅ Executar testes: `mvn test -Dtest=RespondUserInvitationCommandHandlerTest,ScopeAspectTest,ScopeServiceTest,UserIdRefactorIntegrationTest`
2. ✅ Validar cobertura de código (target: >80%)
3. ✅ Integrar com CI/CD pipeline
4. ⏳ Executar testes de integração e-2e

---

## Notas

- Todos os testes usam **Mockito** para isolar dependências
- Testes seguem padrão **Arrange-Act-Assert**
- DisplayNames descritivos para melhor legibilidade
- Cobertura inclui happy paths, edge cases e error handling
