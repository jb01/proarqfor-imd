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

### Requirement: Docker condicionado à autorização humana explícita

O projeto MUST possuir um hook PreToolUse que bloqueie `docker run` por padrão antes da execução. O agente MUST apresentar a ação concreta e obter a autorização humana `APROVADO: Use o docker.` antes de executar Docker. A autorização MUST ser vinculada ao comando, cwd, imagem, argumentos e mounts revisados; não constitui liberação permanente. O mecanismo de verificação pelo hook MUST ser definido e revisado antes da implementação; na ausência de evidência confiável, a execução MUST permanecer bloqueada. A regra MUST preservar os demais hooks e permissões.

#### Scenario: Execução sem autorização
- **WHEN** uma chamada coberta tenta executar docker run sem aprovação humana verificável para a ação
- **THEN** o hook bloqueia antes da execução e informa o checkpoint necessário

#### Scenario: Aprovação forjada no comando
- **WHEN** a frase de aprovação aparece somente no payload, variável, arquivo do projeto ou texto gerado pelo agente
- **THEN** essa presença não é aceita como autorização e a execução permanece bloqueada

#### Scenario: Ação revisada e aprovada
- **WHEN** a autorização humana foi verificada para a ação apresentada e todos os demais controles permitem a execução
- **THEN** somente essa ação pode prosseguir, sem liberar comandos Docker diferentes automaticamente

#### Scenario: Comando alterado após aprovação
- **WHEN** a ação pretendida muda materialmente em relação ao comando aprovado
- **THEN** a aprovação anterior não libera a nova ação e uma nova autorização é necessária

#### Scenario: Chamada indireta ou ambígua
- **WHEN** o Docker é invocado por caminho absoluto, alias equivalente container run ou shell indireto, ou não é possível determinar com segurança o efeito do comando
- **THEN** a chamada não recebe liberação automática sem autorização verificável e a ambiguidade mantém o bloqueio

#### Scenario: Outro guardrail mantém a recusa
- **WHEN** há aprovação de Docker mas o guardrail de fast ou outra permissão bloqueia a chamada
- **THEN** a ação continua bloqueada e o novo hook não desabilita nem contorna esse controle
