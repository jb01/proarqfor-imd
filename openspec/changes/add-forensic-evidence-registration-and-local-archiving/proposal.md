## Why

O arqfor_v1.0 precisa demonstrar localmente o cadastro e o arquivamento de evidências digitais, com verificação inicial de integridade e movimentação real de arquivos. Esta mudança planeja o MVP acadêmico antes de qualquer implementação.

## What Changes

- Planejar listagem, detalhes, cadastro de um único `.dd` por evidência e remoção confirmada do registro.
- Verificar SHA-256 por streaming e tornar divergências visíveis e bloqueadas.
- Restringir estados e transições; arquivar sincronamente com uma repetição em caso de falha.
- Simular três storages locais, ZIP seguido de AES-GCM e desarquivamento que apenas move o arquivo cifrado.
- Persistir metadados no SQLite e documentar as limitações acadêmicas.
- Estabelecer ADRs Proposed, checkpoints humanos e estrutura de hook sem executá-lo.

## Capabilities

### New Capabilities

- `evidence-registration`: telas, cadastro, hashes, detalhes e remoção do registro.
- `evidence-lifecycle`: estados, transições e bloqueios.
- `local-archiving`: movimentação, ZIP, criptografia, repetição e desarquivamento simulado.
- `agent-governance`: escopo, checkpoints, guardrail e validação.

### Modified Capabilities

Nenhuma; não há specs principais existentes.

## Impact

Planejamento de uma única aplicação Java 21 LTS, Spring Boot 3.x, Maven, Thymeleaf, Spring Data JPA, SQLite e JUnit 5. Git/GitHub, Codex/OpenSpec e Docker Compose apenas para futura demonstração local. Nenhum código da aplicação, projeto Maven, dependência ou configuração Docker é criado nesta etapa. Driver SQLite e integração de dialeto JPA deverão ser propostos nominalmente e aprovados antes de configuração.

## Non-goals

Sem login, autenticação, autorização, perfis, API REST, microserviços, nuvem, limitação de throughput, filas, jobs assíncronos, MCP, integração externa, cadeia de custódia, auditoria, restauração real ou hashes após cadastro. Sem commit, push, merge ou deploy nesta etapa.

## Review gate

Checkpoint inicial concluído em 2026-09-08: o usuário enviou `APROVADO: plano`, `APROVADO: ADR-0001`, `APROVADO: ADR-0002` e `APROVADO: ADR-0003`. O plano apresentado, incluindo as interpretações em design.md, está aprovado; evidências em tasks.md. Posteriormente, o usuário enviou `APROVADO: exclusão-fast-storage` após revisão do fluxo e testes propostos. Cadastro, ZIP/cifra e orquestração foram implementados por recortes, com testes sintéticos temporários. Merge e demais checkpoints pendentes seguem registrados em tasks.md.
