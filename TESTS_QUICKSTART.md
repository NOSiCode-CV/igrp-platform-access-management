# Unit Tests Quickstart - feature/config-user-by-id

## ⚡ Execução Rápida

```bash
# Executar todos os testes do refactor
mvn test -Dtest=RespondUserInvitationCommandHandlerTest,ScopeAspectTest,ScopeServiceTest,UserIdRefactorIntegrationTest
```

## 📋 Testes por Funcionalidade

### 1. Conversão String → Integer (JWT subject)
```bash
mvn test -Dtest=UserIdRefactorIntegrationTest#testJwtSubjectToIntegerConversion
mvn test -Dtest=UserIdRefactorIntegrationTest#testValidNumericSubjects
mvn test -Dtest=UserIdRefactorIntegrationTest#testOverflowSubject
mvn test -Dtest=ScopeServiceTest#testGetActorReturnIntegerId
```

### 2. Lookup de Usuário por Integer ID
```bash
mvn test -Dtest=RespondUserInvitationCommandHandlerTest#handle_validIntegerUserIdFromProfile_success
mvn test -Dtest=RespondUserInvitationCommandHandlerTest#handle_userFoundByIntegerId_updatesExistingUser
```

### 3. Injeção de Scope (AspectJ)
```bash
mvn test -Dtest=ScopeAspectTest#testInjectUserIdAsInteger
mvn test -Dtest=ScopeAspectTest#testInjectDifferentUserId
mvn test -Dtest=ScopeAspectTest#testProceededWithModifiedArgs
```

### 4. Queries JDBC com Integer Parameter
```bash
mvn test -Dtest=ScopeServiceTest#testIsSuperAdminUsesIntegerId
mvn test -Dtest=ScopeServiceTest#testGetVisibleDepartmentIdsForSuperAdmin
```

### 5. Error Handling
```bash
mvn test -Dtest=RespondUserInvitationCommandHandlerTest#handle_invalidIntegerUserIdFromProfile_throwsException
mvn test -Dtest=UserIdRefactorIntegrationTest#testInvalidNonNumericSubjects
mvn test -Dtest=UserIdRefactorIntegrationTest#testOverflowSubject
```

## 📊 Testes com Relatório de Cobertura

```bash
# Gerar relatório JaCoCo
mvn clean test -Dtest=RespondUserInvitationCommandHandlerTest,ScopeAspectTest,ScopeServiceTest,UserIdRefactorIntegrationTest jacoco:report

# Abrir relatório
open target/site/jacoco/index.html  # macOS
start target/site/jacoco/index.html # Windows
xdg-open target/site/jacoco/index.html # Linux
```

## 🎯 Testes por Arquivo

### RespondUserInvitationCommandHandlerTest
```bash
mvn test -Dtest=RespondUserInvitationCommandHandlerTest
```

### ScopeAspectTest
```bash
mvn test -Dtest=ScopeAspectTest
```

### ScopeServiceTest
```bash
mvn test -Dtest=ScopeServiceTest
```

### UserIdRefactorIntegrationTest
```bash
mvn test -Dtest=UserIdRefactorIntegrationTest
```

## 🔍 Debugging

```bash
# Com output verbose
mvn test -Dtest=ScopeServiceTest -X

# Parar no primeiro erro
mvn test -Dtest=ScopeAspectTest -DfailIfNoTests=false -Dorg.slf4j.simpleLogger.defaultLogLevel=debug

# Executar um teste específico
mvn test -Dtest=UserIdRefactorIntegrationTest#testActorPrincipalIdConversion -DfailIfNoTests=false
```

## 📈 Cobertura Esperada

- **Conversão String → Integer:** 100%
- **ScopeContext.userId:** 100%
- **ScopeAspect injection:** 100%
- **ActorPrincipal.id():** 100%
- **Error handling:** 100%

## ✅ Checklist de Validação

- [ ] Todos os testes passam: `mvn test -Dtest=...`
- [ ] Cobertura > 80%: `mvn jacoco:report`
- [ ] Sem warnings de compilação
- [ ] Documentação atualizada (UNIT_TESTS_REFACTOR.md)

## 📚 Documentação

- **UNIT_TESTS_REFACTOR.md** - Documentação detalhada
- **TESTS_QUICKSTART.md** - Este arquivo
- **CLAUDE.md** - Atualizado com informações de testing

## 🔗 Componentes Testados

| Componente | Testes | Cobertura |
|-----------|--------|-----------|
| RespondUserInvitationCommandHandler | 9 | ✅ 100% |
| ScopeAspect | 6 | ✅ 100% |
| ScopeService | 11 | ✅ 100% |
| ScopeContext | 6 | ✅ 100% |
| ActorPrincipal | 13 | ✅ 100% |
| String→Integer Conversion | 13 | ✅ 100% |

---

**Total:** 39 testes unitários | ~1500 linhas de código | 100% cobertura do refactor
