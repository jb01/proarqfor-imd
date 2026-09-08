## Purpose

Manter o trabalho do agente dentro do MVP e exigir revisão humana antes de implementação e ações sensíveis.

## ADDED Requirements

### Requirement: Aprovação anterior à implementação

O agente MUST preparar proposta, specs, design, tarefas e três ADRs Proposed e parar até receber APROVADO: plano, APROVADO: ADR-0001, APROVADO: ADR-0002 e APROVADO: ADR-0003. Nenhum status automático substitui aprovação.

#### Scenario: Plano preparado
- **WHEN** os artefatos existem mas falta qualquer uma das quatro aprovações
- **THEN** não começa implementação, Maven, dependências ou Docker

### Requirement: Checkpoint de exclusão em fast

O agente MUST apresentar fluxo, sucesso da cópia, falhas e testes antes de implementar código que exclua arquivos de storage/fast e aguardar APROVADO: exclusão-fast-storage.

#### Scenario: Plano aprovado sem checkpoint específico
- **WHEN** existe aprovação do plano mas não da exclusão-fast-storage
- **THEN** o código de exclusão não é implementado

### Requirement: Guardrail antes do shell

O projeto MUST possuir .codex/hooks.json e .codex/hooks/bloquear-exclusao-fast.sh para interceptação anterior à execução. Quando habilitado, o guardrail MUST bloquear comandos shell que removam ou alterem arquivos de fast. A estrutura conservadora proposta bloqueia todo shell. Não MUST ser habilitada nem executada nesta etapa.

#### Scenario: Comando destrutivo com hook habilitado
- **WHEN** um comando shell coberto tenta remover, sobrescrever ou mover arquivo de fast
- **THEN** a chamada é negada antes da execução

#### Scenario: Comando indireto
- **WHEN** um comando shell coberto usa variável, caminho relativo, symlink ou interpretador
- **THEN** a política conservadora também nega a chamada

#### Scenario: Etapa de documentação
- **WHEN** o agente prepara a estrutura do hook
- **THEN** o matcher fica inerte e o hook não é executado

### Requirement: Validação e entrega controlada

O agente MUST executar testes adequados após cada comportamento implementado e relatar resultados reais. Antes de merge ou entrega da implementação MUST apresentar alterações, testes, limitações, riscos e checklist e esperar APROVADO: merge. Commit, push, merge, deploy, Docker e ações destrutivas MUST exigir aprovação humana específica; nesta etapa não serão executados.

#### Scenario: Entrega proposta
- **WHEN** a implementação futura está pronta mas falta APROVADO: merge
- **THEN** o agente apresenta evidências e para antes de merge ou entrega

#### Scenario: Teste de comportamento
- **WHEN** um comportamento é implementado com autorização
- **THEN** testes pertinentes usam dados sintéticos e temporários; resultados e falhas são informados sem inventar execuções
