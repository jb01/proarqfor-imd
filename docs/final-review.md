# Revisão final — checkpoint 7.5

Status: o usuário respondeu `APROVADO: merge` nesta conversa após revisar o material apresentado. Checkpoint 7.5 concluído; checklist OpenSpec: 26/26 tarefas concluídas. O registro da aprovação não executou commit, push, merge ou deploy.

## Alterações apresentadas

O MVP implementa cadastro de um .dd por evidência, SHA-256 inicial, listagem/detalhes, remoção confirmada somente do registro, estados restritos, arquivamento síncrono com ZIP/AES-GCM e uma retomada, persistência SQLite e desarquivamento somente simulado. Mantém Java 21, Spring Boot 3.5.16, Maven e Thymeleaf.

A árvore de trabalho ainda contém mudanças não commitadas: resolução de paths relativos em EvidenceRegistrationService/StorageCopyService e um teste de cadastro correspondente; documentação, governança e planejamento reconciliados; Dockerfile, Compose, .dockerignore e evidências da demonstração. Essas alterações locais foram preservadas nesta revisão, sem atribuir sua autoria a este recorte. O hook de fast foi validado em fixture; a tarefa 2.5 de integração completa do hook Docker foi retirada do escopo por solicitação humana. Scripts e registros históricos permanecem.

## Testes e evidências realmente disponíveis

| Validação | Resultado e limite |
|---|---|
| JUnit/Surefire | 15 relatórios locais somam 248 testes, zero falhas, erros ou ignorados. São resultados preexistentes, não uma nova execução nesta revisão. O histórico também registra a execução integral de 247 testes anterior ao teste adicional de paths relativos. |
| Contrato do hook de fast | 16/16 casos aprovados; recusa real de true e tentativa de remover sentinela, preservada. |
| Hook Docker | 17 testes isolados aprovados e recusas reais registradas; liberação positiva no runtime não comprovada. Fora do escopo atual. |
| Build Docker da 7.2 | BUILD SUCCESS; usou -DskipTests e não executou JUnit. |
| Integração Docker da 7.2 | Cadastros correto/divergente, arquivamento e bloqueio de divergência; reinício real do mesmo container; todos os campos SQLite e bytes dos três artefatos preservados; detalhes HTTP acessíveis após reinício. Container parado ao final. |
| Revisão documental atual | Validação estrita OpenSpec e git diff --check executados; resultados registrados em tasks.md. |

Evidências: [demonstração Docker](docker-demonstration-review.md), [resultado estruturado](evidence/docker-task72/result.json), [build](evidence/docker-task72/build-summary.log), [integração dos hooks](hook-integration-2026-09-11.md) e [revisão do fluxo de arquivamento](archiving-orchestration-review.md).

A primeira inspeção da cópia de trabalho pela verificação Docker falhou por permissão no host; foi retomada por docker cp para snapshots sintéticos, sem mudar permissões nem repetir cadastro/arquivamento. A demonstração não foi uma sessão visual de navegador e utilizou paths absolutos internos ao container; não substitui testes específicos das alterações de paths relativos.

## Limitações e riscos para a decisão humana

- SQLite e filesystem não formam transação atômica; não existe recuperação automática após crash. O reinício testado foi normal.
- SHA-256 é calculado somente no cadastro. A confirmação de cópia por tamanho não detecta alterações concorrentes de mesmo tamanho.
- Desarquivamento move .zip.enc e retorna EM_ANALISE, sem restaurar .dd, descriptografar ou recalcular hashes; re-arquivamento do cifrado é bloqueado.
- Senha/chave no banco, senha visível e .dd retido em work limitam a confidencialidade. O MVP não possui login, auditoria ou cadeia de custódia.
- Remover cadastro preserva arquivos físicos, mas remove também os metadados criptográficos do registro.
- Hooks não são sandbox do sistema operacional. Os matchers do projeto principal permanecem inertes; a integração de fast foi demonstrada apenas em ambiente descartável.
- Dados da demonstração estão em /tmp: persistem no reinício do container, mas não têm garantia contra limpeza/reinício do host.
- A árvore contém arquivos não rastreados e alterações locais. A aprovação deste checkpoint não seleciona automaticamente arquivos para commit nem define branch/PR de destino. A regra /.demo-data/ presente no .gitignore difere do diretório ./demo-data configurado no Compose padrão; a demonstração utilizou o mount isolado em /tmp. Não foi alterada a política de compartilhamento de dados autorizada pelo usuário.

## Checklist de revisão

- [x] Alterações e estado da árvore apresentados.
- [x] Evidências de testes distinguidas de verificações não executadas.
- [x] Demonstração Docker real e resultado de persistência apresentados.
- [x] Falhas de verificação, limitações e riscos explicitados.
- [x] Escopo retirado da tarefa 2.5 preservado no histórico.
- [x] Receber mensagem humana explícita `APROVADO: merge` — recebida nesta conversa após a apresentação desta revisão.
- [ ] Obter autorização específica para commit/push/deploy, caso sejam solicitados.

A tarefa 7.5 foi encerrada com a mensagem humana explícita `APROVADO: merge`, recebida em resposta à revisão final. A aprovação está registrada em tasks.md. Operações Git não foram executadas; autorizações específicas de commit/push/deploy, quando necessárias, permanecem separadas.
