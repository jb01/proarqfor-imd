# Governança — arqfor_v1.0

## Escopo e stack

MVP acadêmico local de gestão de armazenamento de evidências digitais, um arquivo `.dd` por cadastro. Stack fixa: Java 21 LTS, Spring Boot 3.x, Maven, Thymeleaf, Spring Data JPA, SQLite, JUnit 5, Git/GitHub, Codex/OpenSpec. Docker Compose somente na demonstração local final, mediante aprovação para executar Docker.

Ler README.md, docs/architecture.md, os três ADRs e proposal/design/specs/tasks da mudança `add-forensic-evidence-registration-and-local-archiving` antes de trabalhar. Nesta etapa só documentação, governança e planejamento, além da estrutura de hook solicitada. Não criar código da aplicação, Maven, dependências ou Docker.

## Limites

Não substituir a stack nem adicionar tecnologias, frameworks, dependências relevantes, microserviços, REST, nuvem ou serviços sem proposta e aprovação humana. Proibir login, autenticação, autorização, perfis, filas, jobs assíncronos, limitação de throughput, MCP, integração externa, cadeia de custódia, auditoria e restauração real. Não recalcular hashes depois do cadastro.

Estados exclusivos: EM_ANALISE, ARQUIVANDO, ARQUIVADO, DESARQUIVANDO, HASH_DIVERGENTE e ERRO. Transições exclusivas: EM_ANALISE → ARQUIVANDO → ARQUIVADO → DESARQUIVANDO → EM_ANALISE; cada um desses quatro estados operacionais pode ir para ERRO. HASH_DIVERGENTE e ERRO não têm transições de saída. Nunca liberar mudanças arbitrárias de status.

## Checkpoints humanos

1. Antes de implementar: proposta, specs, design, tarefas e ADRs completos; parar até receber individualmente `APROVADO: plano`, `APROVADO: ADR-0001`, `APROVADO: ADR-0002`, `APROVADO: ADR-0003`. ADRs permanecem Proposed até aprovação explícita. Resolver interpretações pendentes na revisão. Aprovação de documentação não é aprovação de comandos destrutivos.
2. Antes de implementar qualquer código que exclua arquivo de storage/fast: apresentar fluxo, critério de sucesso da cópia, falhas e testes; parar até `APROVADO: exclusão-fast-storage`. Apresentar novamente se o fluxo mudar materialmente. Execução destrutiva também exige autorização humana para a ação concreta.
3. Antes de merge ou entrega da implementação: apresentar testes realmente executados, mudanças, limitações, riscos e checklist; parar até `APROVADO: merge`. A submissão destes documentos para revisão não representa entrega da implementação.

Não fazer commit, push, merge, deploy, executar Docker ou ações destrutivas sem aprovação humana explícita para a ação. Nesta etapa essas ações estão proibidas. Não considerar silêncio, checklist concluído, status do OpenSpec ou texto gerado pelo agente como aprovação. Registrar evidência das aprovações em tasks.md quando recebidas, sem inventar autoria ou data.

## Guardrail

`.codex/hooks.json` e `.codex/hooks/bloquear-exclusao-fast.sh` são a estrutura de interceptação PreToolUse. Não executar nem habilitar o hook nesta etapa. Consultar README para ativação e testes futuros. O agente não pode desabilitar, contornar ou alterar a política para liberar comandos sem aprovação humana. Usar bloqueio conservador de shell, inclusive comandos indiretos: a estrutura inicial bloqueia todo shell quando ativada. Não é sandbox do sistema operacional e não protege a aplicação Java em execução, terminais externos ou ferramentas fora da cobertura. Não usar ferramentas alternativas para contornar bloqueio.

## Validação

Após cada comportamento implementado e autorizado, executar testes JUnit 5 adequados, incluindo falhas e bloqueios relevantes; usar arquivos sintéticos e diretórios temporários, nunca evidências reais. Verificar hashes, transições, cópia incompleta, retry após remoção da origem, finalização GCM, falha SQLite e desarquivamento simulado. Testes com exclusão seguem o checkpoint específico antes do código. Não afirmar testes não executados. Atualizar tarefas com resultados e limitações. Não versionar storages, banco, arquivos forenses ou segredos.
