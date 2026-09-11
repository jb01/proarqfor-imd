## Situação atual — checklist reconciliado com a implementação

26 das 26 tarefas numeradas estão concluídas, incluindo o checkpoint humano de aprovação de merge. Essa contagem não representa percentual de código pronto. A integração web de Desarquivar está concluída, conforme registro deste recorte abaixo. As notas distinguem o que já existe do que falta, sem criar novas aprovações nem alterar os critérios originais.

Concluído tecnicamente: fundação Java 21/Spring Boot 3.5.16/Maven; entidade/repositório SQLite e metadados criptográficos; cadastro SHA-256; listagem/formulário/detalhes com senha persistida; matriz de estados na entidade e fluxos nos serviços; ZIP; AES-GCM; cópia segura; arquivamento síncrono com uma retomada global e ERRO, integrado à ação Arquivar na interface; desarquivamento simulado integrado à interface, com movimento do cifrado, transições, bloqueios e ERRO. Aprovações do plano, dos três ADRs e da exclusão-fast-storage estão registradas.

Remoção confirmada somente do registro e preparação do Docker Compose concluídas. Demonstração Docker com persistência após reinício concluída (7.2). Aprovação humana de merge recebida; checkpoint 7.5 concluído. O relatório revisável de 7.4 já está apresentado.

Última execução comprovada: 247 testes, 0 falhas, 0 erros, 0 ignorados, conforme relatórios Surefire e registro da task 3.4 abaixo. Nenhuma nova dependência ou commit. Os relatos posteriores neste arquivo são históricos: expressões como “pendente” ou “não implementado” neles descrevem a ocasião do registro, não substituem este checklist atual.

## 1. Revisão humana antes de qualquer implementação

- [x] 1.1 Revisar os artefatos e confirmar as interpretações de exclusão do registro, status por ações, path em fast, derivação AES, cópia .dd retida em work e retorno .zip.enc; verificar decisão humana registrada e coerência entre specs/design/ADRs.
- [x] 1.2 Obter `APROVADO: plano`; verificar mensagem humana explícita antes de avançar.
- [x] 1.3 Obter `APROVADO: ADR-0001`; verificar mensagem humana e só então atualizar status do ADR.
- [x] 1.4 Obter `APROVADO: ADR-0002`; verificar mensagem humana e só então atualizar status do ADR.
- [x] 1.5 Obter `APROVADO: ADR-0003`; verificar mensagem humana, incluindo derivação proposta, e só então atualizar status do ADR.

## 2. Preparação futura autorizada

- [x] 2.1 Propor versões Spring Boot 3.x e dependências mínimas, inclusive driver SQLite e dialeto JPA, sem trocar a stack; verificar aprovação humana nominal antes de configuração.
  - Aprovação nominal recebida: o usuário respondeu `Aprovada.` nesta conversa à apresentação de org.xerial:sqlite-jdbc:3.49.1.0 e org.hibernate.orm:hibernate-community-dialects:6.6.53.Final, gerenciadas por Spring Boot 3.5.16/Java 21. Evidências técnicas e decisão em [docs/sqlite-dependencies-review.md](../../../docs/sqlite-dependencies-review.md). A resposta regulariza o registro nominal agora; não constitui aprovação anterior à configuração já existente. Nenhum código/dependência alterado ou novo teste executado neste recorte documental. Não autoriza commit, push, merge, Docker ou operações sobre evidências.
- [x] 2.2 Após 1 e 2.1, criar projeto Java 21/Maven com stack aprovada; verificar compilação e teste básico JUnit 5, sem Docker.
  - Fechamento formal solicitado pelo usuário após aprovação nominal de 2.1. Fundação existente Java 21/Maven/Spring Boot 3.5.16 conferida em pom.xml; compilação e execução JUnit 5 comprovadas pela última suíte registrada (228 testes, sem falhas, erros ou ignorados), incluindo WebApplicationTests e HomeControllerTest. Aprovações da seção 1 e de 2.1 registradas. Reconciliação documental da implementação já existente, sem atribuir aprovação retroativa à configuração anterior. Nenhum código, dependência ou dado alterado; não houve nova compilação ou execução de testes neste fechamento. Não foram executados Docker, commit, push ou merge. Demais tarefas permanecem pendentes; encerrado somente este recorte.
- [x] 2.3 Implementar persistência SQLite e separação de diretórios de dados; verificar unicidade, campos opcionais criptográficos e releitura após reinício com banco temporário.
  - Concluído: banco padrão em data/arqfor.db, raiz configurável comum e criação do diretório pai antes do pool JDBC. Reinício comprovado fechando contexto Spring/JPA/pool e abrindo novo contexto sobre banco temporário; registros, unicidade, campos opcionais e metadados criptográficos preservados. Não houve migração do banco antigo nem demonstração Docker.
- [x] 2.4 Revisar e, com autorização específica, habilitar/testar hook em ambiente descartável conforme README; verificar bloqueio antes de execução e registrar limitações de cobertura.
  - Concluído em 2026-09-11 após solicitação explícita de concluir 2.4/2.5: sessões nativas Codex recusaram `true` e `rm -- storage/fast/sentinela.dd` com `Command blocked by PreToolUse hook`. A sentinela sintética permaneceu intacta; a fonte da cópia foi listada como `trusted`, matcher `^Bash$`. Evidências de chamadas e resultados nativos em [docs/evidence/hooks-2026-09-11](../../../docs/evidence/hooks-2026-09-11); detalhes em [docs/hook-validation.md](../../../docs/hook-validation.md). O projeto principal permanece inerte; nenhuma política foi relaxada.

## 3. Cadastro, telas e estados

- [x] 3.1 Implementar cadastro de .dd, validação de identificador/path/hash e SHA-256 por streaming; verificar JUnit 5 para hash igual/divergente, caixa hexadecimal, duplicidade, symlink/escape e leitura falha.
- [x] 3.2 Implementar estados e transições permitidas no serviço; verificar todos os pares permitidos e proibidos, incluindo bloqueio total de HASH_DIVERGENTE/ERRO.
  - Matriz completa testada na entidade; cadastro, arquivamento e desarquivamento implementados nos serviços, com bloqueios para todos os estados inelegíveis e falhas operacionais. Não há seletor livre de status.
- [x] 3.3 Implementar listagem, Adicionar, cadastro/Cancelar/Cadastrar e detalhes com todos os campos; verificar navegação MVC e erros de formulário, usando apenas ferramentas já aprovadas.
  - Navegação, cadastro, listagem e detalhes completos, incluindo senha persistida com escape HTML e indicação de ausência. Arquivar conectado ao ArchivingService por POST, com mensagens e bloqueios testados. Desarquivar conectado ao serviço de 6.1/6.2, com indicação de retorno cifrado, bloqueios e testes MVC; não há edição arbitrária de status.
- [x] 3.4 Implementar remoção somente de registro com confirmação e cancelamento; verificar registro removido, arquivo preservado e bloqueio durante operação em curso.
  - Concluído: confirmação com identificador escapado, Cancelar sem mutação, POST com confirmação explícita e exclusão condicional no SQLite. Preserva arquivos; permite EM_ANALISE, ARQUIVADO, HASH_DIVERGENTE e ERRO; bloqueia ARQUIVANDO/DESARQUIVANDO inclusive após confirmação desatualizada. 17 novos testes integrados passaram.

## 4. Checkpoint obrigatório antes de código de exclusão em fast

- [x] 4.1 Apresentar fluxo de cópia, fechamento, comparação de tamanho, persistência e exclusão, com matriz de falhas e testes em temporários; verificar material revisável e ausência de hash posterior.
  - Material submetido em docs/archiving-orchestration-review.md: regras, critério de exclusão, retomada, falhas, testes propostos e limites. Submissão não é aprovação do checkpoint 4.2.
- [x] 4.2 Parar e obter `APROVADO: exclusão-fast-storage`; verificar mensagem explícita antes de implementar qualquer exclusão em fast. Autorização de execução destrutiva permanece separada.
  - O usuário enviou nesta conversa `APROVADO: exclusão-fast-storage`, após a apresentação de docs/archiving-orchestration-review.md e da solicitação de autorização dos testes sintéticos temporários. Implementação e testes limitados ao fluxo revisado; nenhuma autorização para operar sobre dados reais, fazer commit ou merge.

## 5. Arquivamento síncrono após checkpoint

- [x] 5.1 Implementar cópia fast → work e exclusão condicionada ao sucesso aprovado; verificar cópia incompleta, fechamento falho, tamanho divergente, colisão e falha ao excluir, preservando a última cópia utilizável.
- [x] 5.2 Implementar ZIP e cifra conforme ADR-0003 aprovado; verificar senha de 10 alfanuméricos, chave válida, parâmetros persistidos, IV novo e preservação do ZIP na falha de finalização.
  - ZIP e CryptoService concluídos nos recortes solicitados; validação da cifra registrada abaixo em 2026-09-09.
- [x] 5.3 Implementar publicação .zip.enc, persistência de metadados e exclusão do ZIP aberto após sucesso; verificar arquivo final, path, senha/IV e ARQUIVADO, sem excluir .dd de trabalho automaticamente.
  - Integração e conclusão de ARQUIVADO implementadas e testadas na camada de serviço; nenhuma ação web adicionada.
- [x] 5.4 Implementar uma repetição por etapa segura e ERRO na segunda falha; verificar sucesso na segunda tentativa, limite de duas tentativas, retomada após origem removida e falha de SQLite/limpeza após cifra publicada.

## 6. Desarquivamento simulado

- [x] 6.1 Implementar movimento de .zip.enc para fast com transições e atualização de path; verificar conteúdo e extensão preservados, sem descriptografia, descompactação ou recálculo de hash.
  - UnarchivingService implementado e testado com SQLite e arquivos temporários, incluindo confirmação de DESARQUIVANDO por conexão independente antes do movimento. O serviço preserva artefato cifrado e metadados. A ação web foi integrada em recorte posterior, com bloqueios no GET de detalhes e no POST de execução.
- [x] 6.2 Implementar erro do movimento e rejeição de re-arquivamento do .zip.enc conforme decisão revisada; verificar ERRO na falha, preservação de metadados e orientação de novo cadastro .dd.
  - Falha sem retry, ERRO e metadados preservados testados, incluindo rollback e falha SQLite após movimento. Ciclo real de arquivamento seguido do retorno simulado testado na camada de serviço; ArchivingService recusa o cifrado devolvido e orienta novo cadastro sem mudar EM_ANALISE.

## 7. Demonstração e revisão final futuras

- [x] 7.1 Preparar Docker Compose somente para demonstração final local, após implementação; verificar configuração revisada com dados e storages persistentes fora do container, sem executar Docker automaticamente.
  - Criados [Dockerfile](../../../Dockerfile), [docker-compose.yml](../../../docker-compose.yml) e [.dockerignore](../../../.dockerignore). O build usa Maven com Java 21 e o serviço executa o jar Spring Boot em `8080`. O bind mount `./demo-data:/var/lib/arqfor` mantém fora do container o SQLite e os três storages por meio de `FORENSTORAGE_DATA_ROOT=/var/lib/arqfor`; `demo-data` foi adicionado ao `.gitignore`. A configuração foi revisada estaticamente; Docker não foi executado, nenhum diretório de demonstração foi criado e nenhuma imagem foi baixada.
- [x] 7.2 Obter autorização específica `APROVADO: Use o docker.` vinculada aos comandos da demonstração, além da autorização de operações destrutivas com dados sintéticos; verificar o checkpoint e respeitar os guardrails existentes antes de executar e demonstrar persistência após reinicialização.
  - Concluída em 2026-09-11 após `APROVADO: Use o docker.` e `Autorizo as exclusões sintéticas descritas`. Build e cadastro HTTP executados; arquivamento da amostra válida e bloqueio da divergente comprovados. Mesmo container reiniciado, todos os campos SQLite e bytes dos três artefatos preservados, detalhes HTTP confirmados após reinício. Container parado ao final, dados mantidos. [Relatório e evidências](../../../docs/docker-demonstration-review.md). A primeira inspeção do work teve restrição de permissão no host; retomada por cópia de leitura via Docker sem alterar permissões ou repetir operações. Não houve novo JUnit; build usou -DskipTests. Não avançado para 7.5.
- [x] 7.3 Executar validação integrada autorizada: cadastro correto/divergente, bloqueios, arquivamento, duas falhas e retorno simulado; verificar resultados reais registrados, arquivos e metadados esperados.
  - Validação final executada com JUnit 5, SQLite e arquivos sintéticos em diretórios temporários. A execução focalizada dos fluxos finais passou com 242 testes; a suíte completa passou com 247 testes, ambos com 0 falhas, 0 erros e 0 ignorados. A cobertura consolidada inclui cadastro correto e divergente, estados bloqueados, cópia fast/work, ZIP, AES-GCM, falhas de fechamento/tamanho/SQLite, limite global de duas tentativas, ERRO, publicação e metadados, retorno simulado do .zip.enc, colisões, concorrência e integração MVC. Nenhum storage, banco ou evidência real foi usado; não houve execução Docker ou sessão manual de navegador.
- [x] 7.4 Apresentar alterações, testes executados, limitações, riscos e checklist; verificar relatório humano revisável sem alegar testes não executados.
  - Consolidação registrada neste checklist e nos relatórios [docs/archiving-orchestration-review.md](../../../docs/archiving-orchestration-review.md), [docs/hook-validation.md](../../../docs/hook-validation.md) e [docs/docker-hook-validation.md](../../../docs/docker-hook-validation.md). Limitações mantidas: integração de fast comprovada em 2.4; hook Docker retirado do escopo atual por solicitação humana, com limitações e evidências históricas preservadas; Docker Compose executado em demonstração isolada com persistência comprovada em 7.2; não há atomicidade SQLite/filesystem nem recuperação pós-crash; a suíte automatizada não substitui demonstração manual no navegador. Esta revisão não representa `APROVADO: merge`.
- [x] 7.5 Parar e obter `APROVADO: merge` antes de merge ou entrega da implementação; verificar mensagem explícita e autorização específica para qualquer commit/push/deploy necessário.
  - Revisão final preparada por solicitação do usuário: [alterações, evidências de testes, limitações, riscos e checklist](../../../docs/final-review.md). Conferidos 15 relatórios Surefire preexistentes (248 testes, zero falhas/erros/ignorados) e evidências da demonstração 7.2; nenhuma nova execução JUnit nesta revisão. Validação estrita OpenSpec e git diff --check passaram. O usuário respondeu nesta conversa `APROVADO: merge`, após a apresentação da revisão final. Aprovação explícita registrada e checkpoint concluído. Nenhum commit, push, merge ou deploy foi executado neste registro; commit/push/deploy continuam sujeitos à autorização específica, e não há branch/PR de destino definido neste checkpoint.

## Retirada da tarefa 2.5 do escopo atual

O usuário solicitou: “Remova a tarefa 2.5, não precisamos dela nesse momento.” A tarefa foi removida do checklist e da dependência de 7.2, sem ser marcada como concluída. Proposta, design e spec de governança foram alinhados. Permanecem 24/26 tarefas concluídas e somente 7.2/7.5 pendentes. Os relatos abaixo preservam a situação nas respectivas ocasiões. Scripts, configurações dos hooks e evidências de testes não foram removidos ou desativados. A autorização humana para executar Docker e os demais checkpoints continuam obrigatórios.

## Integração real de hooks — retomada de 2026-09-11

O usuário solicitou concluir 2.4 e 2.5, mostrar evidências de bloqueio e parar. Concluída 2.4 com recusa real de `true` e tentativa de remoção da sentinela sintética. Na 2.5, comprovadas recusas reais, mas encontrada incompatibilidade de contrato que impede validar a aprovação legítima; mantida aberta. [Relatório completo](../../../docs/hook-integration-2026-09-11.md). As chamadas e respostas são extraídas dos transcripts nativos; os scripts originais permanecem intactos. A confiança de teste foi registrada pela API nativa, sem bypass. Não houve nova aprovação de Docker real, merge ou revisão da política de liberação. Total atual: 24/27, pendentes 2.5, 7.2 e 7.5.

## Revisão documental das tarefas — 2026-09-11

Conferidos checklist, artefatos OpenSpec, ADRs, arquivos de implementação/testes, configuração Docker e relatórios locais existentes. Mantidas as 23 marcações concluídas de 27: nenhuma das quatro tarefas abertas possui evidência de conclusão integral. A existência da configuração Docker comprova a preparação de 7.1, não a demonstração de 7.2; testes isolados dos hooks não comprovam a interceptação exigida por 2.4/2.5. Não foi encontrada aprovação registrada que encerre 7.5.

Os 15 relatórios Surefire disponíveis somam 248 testes, sem falhas, erros ou ignorados; são resultados preexistentes e não comprovam uma nova execução integral nesta revisão. O registro histórico de 247 testes foi preservado. Nenhum teste JUnit, hook ou Docker foi executado nesta revisão documental. Corrigidas somente notas de situação atual sobre 2.4 e 7.1/7.4, preservando aprovações e registros históricos.

## Registro de aprovações

## Implementação do hook Docker — task 2.5 — validação parcial

O usuário solicitou `Execute a tarefa 2.5`. Implementados .codex/hooks/bloquear-docker.sh, .codex/hooks/bloquear-docker.py e entrada independente no hooks.json; matcher permanece inerte até a validação de ativação. O script original de fast e sua regra não foram alterados. Revisão técnica do mecanismo registrada no design antes do código: evento humano do transcript nativo fora do projeto, identidade de sessão/turno, revisão estruturada e correspondência exata de command/cwd/shell/login. Biblioteca padrão Python 3 já disponível, sem instalação ou dependência Java nova.

Gramática deliberadamente restrita: Docker direto/literal exige a frase `APROVADO: Use o docker.` como mensagem humana seguinte à revisão; shell indireto/ambíguo é bloqueado. Somente pwd/true/false literais com Bash sem login são isentos. O grant não é arquivo ou flag; vale para a mesma ação no turno da resposta, sem consumo único. Não supera outras recusas. Formato de transcript limitado ao legacy observado da IDE 0.153.4; não é uma API estável nem isolamento contra processos externos.

Executados `python3 -B scripts/test-docker-hook.py` (17 testes passaram), `bash scripts/test-hook-contract.sh` (16/16 casos passaram) e `bash -n .codex/hooks/bloquear-docker.sh`. Na primeira execução, dois testes positivos recusaram fixtures graváveis pelo grupo devido à umask; corrigidas somente permissões das fixtures para 0700/0600 e reexecutados com sucesso, sem relaxar o verificador. Cobertura: aprovação válida e forjada, alteração da ação, sessão/turno/origem/versão, comandos diretos/indiretos, transcript inválido/symlink/permissões, entrada inválida, falha do verificador e coexistência com recusa de fast. Executável substituto apenas grava argumentos temporários; nenhum Docker real, daemon, download, dado real ou JUnit executado.

A tarefa NÃO foi marcada concluída: faltam confiança/ativação e interceptação real pelo runtime compatível. A recusa anterior da revisão automática sobre configuração pessoal permanece; não foi tentada novamente ou contornada. Além disso, diretórios do transcript atual são 0775 e arquivo 0664; a política exige retirar escrita de grupo nesses paths antes de aceitá-los. Lista exata e ajuste mínimo proposto estão em docs/docker-hook-validation.md; nenhuma permissão nativa foi alterada. Apenas contratos passaram, não integração Codex. Total permanece 20/27. Sem commit, push, merge, Docker ou tarefa posterior.

## Solicitação de novo hook para Docker — task 2.5

O usuário solicitou criar uma nova tarefa para um hook que bloqueie docker run e exija permissão explícita para executar Docker, indicando a frase `APROVADO: Use o docker.`. A frase foi registrada como checkpoint solicitado. Não foi apresentado nesta rodada nenhum comando Docker concreto para execução; não foi tratada como liberação permanente nem como autorização para alterar a configuração pessoal de confiança do Codex que bloqueou 2.4.

Criada tarefa 2.5 pendente e alinhados proposta, design e spec agent-governance. Somente planejamento: nenhum hook novo implementado/ativado, Docker executado, teste de runtime realizado ou commit criado. Total atual: 20/27 tarefas concluídas. A task 2.4 e seu bloqueio continuam independentes; os totais nos relatos históricos abaixo descrevem suas respectivas ocasiões.

## Hook em ambiente descartável — task 2.4 — revisão parcial

Autorização recebida: o usuário solicitou “Hook Revisar, habilitar e testar em ambiente descartável, mediante autorização específica” e “Pare depois dela”. A autorização foi aplicada à revisão, cópia mínima temporária e testes sintéticos; não às outras tarefas ou commit.

Codex CLI 0.149.1 conferido; hooks stable true. Preservada a política conservadora: todo shell recebe recusa, sem parser nem exceções. Preparada /tmp/arqfor-hook-validation/workspace com cópia do script, hooks.json apontando para essa cópia com matcher ^Bash$, repositório Git vazio e sentinela sintética; nenhum dado real ou credencial copiado, nenhum commit. A fonte do projeto de trabalho permanece inerte.

Executados os testes de contrato e adicionada reprodução em scripts/test-hook-contract.sh: bash -n aprovado; 16/16 payloads retornaram código 2, stdout vazio e motivo de bloqueio no stderr, com sentinela preservada. A execução reproduzível gerou resultados em /tmp/arqfor-hook-contract.0mwq4p. Payloads são somente texto, incluindo rm/mv/sobrescrita, path absoluto, cwd, variável, symlink, interpretador, shell indireto, pipeline e entradas vazia/inválida/null. Esses testes não comprovam interceptação pelo runtime. Nenhum código Java alterado; JUnit não reexecutado.

A inicialização restrita do app-server falhou por filesystem/sandbox. A consulta hooks/list em execução autorizada iniciou sem turno de modelo e devolveu lista vazia: falta confiança no projeto descartável. O próximo passo preparado era config/value/write para confiar somente nessa pasta em ~/.codex/config.toml. A revisão automática rejeitou a ação por alterar persistentemente configuração pessoal fora do ambiente descartável e exceder a autorização. A escrita foi impedida; não foi contornada a recusa nem usada opção de bypass. Solicitar autorização específica para confiança dessa pasta e da definição exata do hook antes de retomar a integração. Detalhes em docs/hook-validation.md.

A task 2.4 permanece aberta e o total continua 20/26. Faltam carregamento confiável, bloqueio comprovado de comando inofensivo e, depois, testes de mutação da sentinela via ferramentas cobertas. Sem operações sobre dados reais, Docker, commit, push ou merge; nenhuma tarefa posterior iniciada.

## Remoção confirmada somente do registro — task 3.4 — 2026-09-10

Revisão do recorte aprovada: após o relato de conclusão da task 3.4 e dos 247 testes aprovados, o usuário respondeu nesta conversa: `Aprovado`. A aprovação refere-se a este recorte; permanece a instrução de parar e deixar o commit para o usuário. Não equivale a `APROVADO: merge` nem autoriza executar as tarefas restantes. Registro documental, sem nova execução de testes.

Autorização deste recorte: o usuário solicitou implementar confirmação com identificador, cancelamento, exclusão somente do cadastro, preservação dos arquivos e bloqueio durante operações; determinou parar após a conclusão e não fazer commit. Essa solicitação não aprova merge ou ações sobre dados reais.

Implementado EvidenceRemovalService, DELETE condicional no repositório e fluxo MVC GET/POST /evidences/{id}/remove. A página exibe o identificador com escape HTML, explica a remoção dos metadados (inclusive senha e parâmetros criptográficos), oferece Cancelar por navegação sem mutação e exige confirmação explícita no POST. O id e o identificador precisam corresponder ao mesmo registro. A exclusão permite apenas EM_ANALISE, ARQUIVADO, HASH_DIVERGENTE e ERRO; o predicado é avaliado no próprio DELETE, competindo com os claims existentes de arquivamento/desarquivamento. Não há acesso ao filesystem no serviço de remoção, cascata de entidades, alteração de status ou novo hash. Remoção bem-sucedida redireciona à lista; falhas de SQLite recebem mensagem sanitizada; id inexistente retorna 404.

Validação executada com JUnit 5, SQLite e arquivos sintéticos temporários: 17 novos testes integrados MVC/serviço/repositório cobrem os seis estados, confirmação GET sem exclusão, cancelamento, remoção da listagem, preservação dos bytes em fast/work/archive, confirmação ausente/falsa, identificador incorreto ou de outro registro, parâmetros extras, escape HTML, remoção repetida/id ausente e rollback por falha SQLite. Os dois ordenamentos da disputa com claims de arquivamento e desarquivamento foram verificados deterministicamente: claim anterior bloqueia remoção (inclusive chamada direta ao serviço); remoção anterior faz o claim retornar zero. Não foi feito teste de carga com threads nem sessão manual no navegador.

Comando executado:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

Resultado: BUILD SUCCESS, 247 testes, 0 falhas, 0 erros e 0 ignorados. A remoção apaga também os metadados criptográficos do cadastro; os arquivos retidos não recuperam esses metadados automaticamente. Esse efeito está explícito na confirmação. Sem recuperação/restauração ou limpeza de arquivos adicionada.

Task 3.4 concluída; total 20/26. Nenhuma dependência, serviço de movimentação/cifra, estado, hook, regra Git ou dado real alterado. Sem Docker, commit, push ou merge. As demais tarefas e a aprovação final de merge permanecem pendentes; encerrado somente este recorte para revisão.

## Banco e persistência após reinício — task 2.3

Implementado somente o recorte solicitado: application.properties usa jdbc:sqlite:${forenstorage.data-root}/data/arqfor.db e storages sob a mesma raiz configurável (padrão .; usar caminho absoluto para execução de outros diretórios). DataSourceConfiguration cria o diretório pai antes do pool JDBC, preserva propriedades Hikari e overrides, inclusive SQLite em memória. URLs SQLite URI file: são delegadas ao driver sem criação automática do pai. Falha ao criar diretório impede inicialização sem substituir arquivo existente. Não há migração, exclusão ou alteração do antigo forenstorage.db; o novo caminho usa banco próprio.

Testes adequados executados: aplicação completa sem servidor HTTP inicia em raiz temporária, cadastra evidências sintéticas, arquiva pelo fluxo existente, fecha contexto/EntityManagerFactory/pool e inicia novo contexto. Releitura verifica estado, paths, data, hashes persistidos, chave/IV/senha/salt/iterações/versão, bytes cifrados e registro ainda sem cifra. Duplicidade continua bloqueada pelo índice SQLite. Segundo teste verifica falha de inicialização quando data é um arquivo e comprova sua preservação. Nenhum hash recalculado após cadastro. Testes usam somente temporários; serviços de arquivo/cifra não foram alterados.

Primeira execução: 230 testes, uma falha na expectativa da classe da exceção de unicidade. O dialeto encapsula a violação como JpaSystemException; ajustado teste para conferir DataAccessException com causa SQLITE_CONSTRAINT_UNIQUE e quantidade de registros preservada. Reexecução completa:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

BUILD SUCCESS: 230 testes, 0 falhas, 0 erros, 0 ignorados, incluindo os dois novos testes. Reinício de contexto na mesma JVM, não de processo/container; demonstração Docker continua pendente. Persistência entre inicializações normais não garante recuperação de operação interrompida nem atomicidade entre arquivos/SQLite. Limitação acadêmica de chave/IV/senha no banco preservada.

Checklist: configuração alinhada, reinício comprovado, testes executados e documentação atualizada; task 2.3 concluída, 19/26 no total. Proibidas e não realizadas alterações fora do escopo: serviços de negócio, estados, hashes, restauração, interface, dependências, hook, regras Git, migração de dados reais e Docker. Sem commit, push ou merge; aprovação de merge permanece pendente. Encerrado este recorte para revisão.


## Integração web de Desarquivar — recorte concluído

Regras verificadas e implementadas: botão Desarquivar substitui Alterar status; habilitação somente em ARQUIVADO com currentPath igual ao archivedPath e extensão .zip.enc. POST /evidences/{id}/unarchive revalida esses critérios, recusa status manual e passa somente o id ao UnarchivingService existente. Não aceita paths/hashes/senha do formulário para alteração. Serviço chamado uma vez, sem retry adicional; mantém responsabilidade por transições, validação física e concorrência. GET não inicia operação. Redirecionamento aos detalhes apresenta estado/path relidos e mensagem de sucesso ou erro; sucesso informa retorno cifrado sem restauração. Retorno EM_ANALISE mantém ambos os botões bloqueados e orienta novo cadastro .dd. Banco indisponível não é apresentado como sucesso; erro de consulta retorna 503, id ausente retorna 404.

Testes adequados criados e executados: 16 casos MVC adicionais (formulário, GET sem efeitos, cinco estados bloqueados, três inconsistências de artefato, status manual/id ausente, parâmetros extras, redirecionamento, quatro tipos de falha e falha de consulta) e dois testes integrados com SQLite/serviços reais em temporários: cadastro/arquivamento/retorno pela interface, bytes cifrados preservados e repetição bloqueada; colisão de destino produz ERRO visível e controles desabilitados. Senha continua visível/escapada e chave ausente do HTML. Na primeira execução, um teste antigo contou artefatos de outras evidências temporárias; corrigido para inspecionar somente work/<id> da própria evidência, preservando as verificações de quantidade e conteúdo.

Comando executado novamente após correção:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

Resultado: BUILD SUCCESS, 228 testes, 0 falhas, 0 erros, 0 ignorados; 48 testes de EvidenceController e 3 integrados MVC. Validação com renderização Thymeleaf via MockMvc, sem sessão manual no navegador ou operação sobre dados reais. Fluxo síncrono sem progresso em tempo real; limitações de SQLite/filesystem, chave/IV no banco e ausência de restauração permanecem.

Checklist: integração concluída, regras e bloqueios verificados, testes executados, README/arquitetura/design/checklist atualizados. Permanecem 16/26 tarefas numeradas completas: fechada a pendência web associada a 3.3/6.1/6.2, sem antecipar demonstração ou revisão final. Proibidas e não realizadas alterações fora do escopo: serviços de arquivo/cifra, estados, hashes, restauração, remoção de registros, dependências, configuração de dados, hook e Git. Sem commit, push, merge, Docker ou deploy. Recorte submetido para revisão; APROVADO: merge permanece pendente.

## Desarquivamento somente simulado — serviço e transições

Implementado somente o recorte solicitado: UnarchivingService.unarchive(Long) e claimUnarchiving no repositório. Regras verificadas: entrada apenas em ARQUIVADO com path arquivado correspondente; confirmação de DESARQUIVANDO antes do movimento; mover somente .zip.enc de archive/<id> para fast/<id>, sem sobrescrita; confirmar currentPath e EM_ANALISE após sucesso; preservar bytes, extensão, data, hashes e todos os metadados criptográficos. archivedPath permanece histórico. Falhas operacionais vão para ERRO sem retry; tentativa concorrente recusada não altera o estado da operação proprietária. O bloqueio existente recusa re-arquivamento e orienta novo cadastro .dd.

Testes adequados criados/executados: sucesso com leitura SQLite por conexão independente antes do movimento; conteúdo/extensão/metadados preservados; cinco estados inelegíveis; origem ausente; path externo, outra evidência, extensão inválida, diretório e symlinks; colisão e parcial preservados; erro sanitizado sem retry; rollback no commit inicial/final; trigger SQLite bloqueando conclusão e também ERRO; concorrência e leitura obsoleta na atualização condicional; transação externa/id inválido; raízes sobrepostas e path arquivado inconsistente. Teste integrado de serviço usa arquivamento real, retorno cifrado e recusa de re-arquivamento, preservando .dd em work. Nenhuma descriptografia ou hash posterior ao cadastro.

Na primeira execução focalizada, 7 de 24 casos falharam apenas na comparação da precisão de createdAt (nanossegundos em memória versus milissegundos no SQLite). A preparação passou a reler o registro persistido antes da operação, verificando preservação da data armazenada. Após essa correção e mais três cenários, 27 testes passaram. Suíte completa: BUILD SUCCESS, 210 testes, 0 falhas, 0 erros, 0 ignorados. Comandos executados:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dtest=UnarchivingServiceTest test
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
OPENSPEC_TELEMETRY=0 openspec validate add-forensic-evidence-registration-and-local-archiving --strict
```

OpenSpec válido. Limitações: não há atomicidade banco/filesystem nem recuperação pós-crash; movimento pode deixar parcial e não há limpeza automática. Após movimento concluído e falha do commit final, ERRO grava o novo path quando possível. Se SQLite impedir também ERRO, a resposta informa que a persistência não foi confirmada, podendo permanecer DESARQUIVANDO com path antigo; cifrado preservado em fast exige avaliação manual. Validações não isolam alterações concorrentes de processos externos. Chave/IV/senha no SQLite continuam sendo limitação acadêmica; nenhum segredo registrado em código/log e nenhuma evidência real utilizada.

Checklist deste recorte: regras conferidas; serviço e testes implementados; suíte executada; documentação atualizada; 3.2/6.1/6.2 concluídos, total 16/26. Integração web de Desarquivar permanece pendente. Proibidas e não realizadas alterações fora do escopo: interface, restauração/descriptografia/descompactação, cálculo de hashes, serviços de arquivamento/cifra, novos estados, dependências, configurações de dados, hook, regras Git e remoção de registros. Sem commit, push, merge, Docker ou deploy. Recorte encerrado para revisão; APROVADO: merge continua pendente.

## Integração de Arquivar à interface e exibição da senha

Por solicitação explícita do usuário, implementado somente este recorte e seus testes. EvidenceController recebe ArchivingService e oferece POST /evidences/{id}/archive. O formulário dos detalhes envia apenas a ação do id; parâmetros de path/senha não alteram o registro e status manual é recusado. A elegibilidade visual verifica EM_ANALISE, extensão .dd e ausência de archivedPath; POST inelegível é bloqueado também no controller. O serviço permanece responsável por revalidar estado/arquivo/raiz, concorrência, retry e transições, sem alteração de seu código.

Após uma chamada ao serviço, redireciona aos detalhes com sucesso ou erro e nova consulta do registro. Não há retry adicional no controller. Falhas operacionais são apresentadas sem afirmar sucesso; falha SQLite é sanitizada e falha de consulta dos detalhes retorna HTTP 503 sem estado não confirmado. Evidência inexistente retorna HTTP 404. GET /archive não executa operação e retorna HTTP 405. O endpoint de edição manual de status continua inexistente.

A senha persistida usa th:text, com escape HTML; null/vazio/espaços mostram “Ainda não gerada.”. Nenhuma chave AES é renderizada. A exposição didática da senha sem login continua sendo a limitação aprovada do ADR-0003.

Testes adequados exigidos e executados: formulário POST habilitado, GET sem efeitos, sucesso com redirecionamento/leitura atualizada, cinco estados bloqueados, POST forjado, .zip.enc e archivedPath existentes, status manual, id inexistente, parâmetros extras ignorados, falhas operacionais/concorrência/SQLite, senha ausente/presente/escapada e chave ausente do HTML. Teste integrado usa cadastro via MVC, SQLite e serviços reais, um .dd sintético em @TempDir, arquivamento e leitura de senha/path/status pela interface; confirma origem removida, .dd de work preservado, cifrado presente e repetição do POST bloqueada. Não usa evidências ou banco reais do projeto.

Comandos executados:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dtest=EvidenceControllerTest test
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

Resultados: 32 testes MVC passaram na execução focalizada; suíte completa com 183 testes, 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS. São 20 casos adicionais em relação à suíte anterior de 163 (19 no controller e 1 integrado). Validação por MockMvc com renderização Thymeleaf; não foi executada sessão manual de navegador. A requisição segue síncrona, sem progresso em tempo real.

Checklist: regras conferidas; integração e senha implementadas; testes executados; README/arquitetura/design/checklist atualizados; item 3.3 concluído. Proibidas e não realizadas alterações fora do escopo: serviços de arquivo/criptografia, dependências, banco/configuração, novos estados, remoção de registros, desarquivamento/restauração, hook, regras Git ou login. Nenhum commit, push, merge, Docker ou deploy. Recorte encerrado para revisão; 13/26 tarefas concluídas, aprovação de merge ainda pendente.

## Revisão prévia da orquestração — registro histórico antes da aprovação

O usuário solicitou orquestrar o arquivamento completo com retry único e ERRO, listar regras de negócio, exigir testes adequados, proibir alterações fora do escopo e atualizar documentação, sem commit. Preparado docs/archiving-orchestration-review.md e adicionado link no README. O documento cobre cópia/fechamentos/tamanho/commit antes de exclusão, uma retomada global da etapa segura e adequação do CryptoService para persistência/limpeza após publicação sem recifrar. Contém matriz de testes JUnit 5, proposta de execução somente em @TempDir/SQLite temporário e proibições explícitas de alterações alheias ao recorte.

Item 4.1 concluído pela submissão do material; 4.2, 5.1, 5.3 e 5.4 permanecem pendentes. Não foi recebida a mensagem `APROVADO: exclusão-fast-storage`; aprovações anteriores não foram reinterpretadas. Nenhum código Java ou teste foi alterado/executado nesta revisão. Aguardada aprovação específica do fluxo e autorização da execução dos testes sintéticos descritos. Nenhum commit realizado.

Após a revisão do ZipService, o usuário respondeu nesta conversa: `Aprovado.`. Essa mensagem aprova somente o recorte apresentado; não equivale a `APROVADO: merge` nem aprova exclusão em fast.

Após a apresentação do CryptoService e dos resultados de validação, o usuário respondeu nesta conversa: `aprovado`. Registrada a aprovação da revisão desse recorte. A mensagem não equivale a `APROVADO: merge` nem autoriza exclusão em fast, commit, push ou implementação das próximas tarefas.

## Orquestração completa após aprovação de exclusão-fast-storage

Na sequência da revisão documentada em docs/archiving-orchestration-review.md, o usuário enviou literalmente `APROVADO: exclusão-fast-storage`. A aprovação foi recebida antes de qualquer código desta etapa que excluísse a origem em fast. A execução foi limitada aos testes com arquivos sintéticos e SQLite temporário apresentados para aprovação; nenhum arquivo real do projeto foi excluído e nenhum commit foi feito.

Implementados ArchivingService.archive(Long) e StorageCopyService. A orquestração exige EM_ANALISE e .dd elegível em fast; confirma ARQUIVANDO antes da cópia, confirma o path de work antes de excluir fast e conclui ARQUIVADO somente após cifra/publicação/metadados/exclusão do ZIP. Há bloqueio por id na instância e uma atualização condicional no repositório para disputar o início sem duplicá-lo. Não há transação externa abrangendo as operações de arquivo.

A cópia exige EOF, contagem de bytes, fechamento dos dois streams e tamanho confirmado. A exclusão revalida origem/destino por tamanho, data de modificação, identidade de arquivo e paths sem symlink. Tentativas usam diretórios e nomes próprios; colisões não são sobrescritas. ZIP parcial é preservado e a segunda tentativa usa outro nome, sem recópia do .dd já confirmado. Nenhum hash foi recalculado.

CryptoService conserva etapas de cifra fechada/publicação/persistência/limpeza e seus parâmetros apenas no contexto da chamada síncrona. Metadados ou limpeza podem ser repetidos sem recifrar o artefato publicado. O contrato público isolado permanece compatível com os testes anteriores. Uma nova cifra após falha de finalização usa IV novo e outro temporário. Arrays de chave/senha são limpos ao encerrar o contexto; não há novos logs de segredos nem arquivos de chave no Git.

O orçamento é global: primeira tentativa e uma retomada. Segunda falha, ainda que em outra etapa, grava ERRO e mensagem sem causas que possam expor segredos. Falha do banco ao gravar ERRO retorna IOException explícita e não declara estado confirmado. A gravação terminal de ERRO não é uma terceira tentativa de arquivamento.

Testes realmente executados com o agente Mockito já disponível, sem mudança de dependências:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dtest=CryptoServiceTest,ZipServiceTest test
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dtest=ArchivingServiceTest,StorageCopyServiceTest test
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

Resultados: respectivamente 26, 49 e 163 testes, todos com BUILD SUCCESS, 0 falhas, 0 erros e 0 ignorados. São 37 testes da orquestração e 12 da cópia. Cobertura inclui ordem com leitura SQLite por conexão física antes das exclusões; primeira e segunda falhas em dez etapas; falhas em etapas diferentes; rollback de commit de início/path/finalização; falha SQLite real por trigger e retomada dos mesmos bytes/IV; falha SQLite ao registrar ERRO; ZIP parcial com fast já removido; IV novo; estados bloqueados; paths inválidos/symlinks; cópia incompleta/fechamentos/tamanho/colisão; origem ou cópia alterada antes da remoção; chamadas concorrentes; ausência de segredos nos logs da operação bem-sucedida. Arquivos e bancos exclusivamente temporários e sintéticos.

Limitações mantidas: sem atomicidade SQLite/filesystem, proteção integral contra processos externos ou recuperação pós-crash. Parciais e .dd de trabalho ficam preservados; mesmo um arquivamento que conclui na segunda tentativa pode deixar parcial da primeira para avaliação humana. Se a persistência de metadados falhar definitivamente, o ZIP é retido e o cifrado não tem garantia de metadados recuperáveis. Chave/senha junto do SQLite continuam uma limitação acadêmica. Não foram implementados endpoints/telas, desarquivamento, restauração/descriptografia, jobs, novas transições ou dependências.

Checklist: 4.2 aprovado; 5.1/5.3/5.4 concluídos; testes executados; regras e restrições documentadas; README, arquitetura e planejamento atualizados. Progresso: 12/26 tarefas concluídas. Recorte encerrado para revisão sem commit, push, merge, Docker ou deploy. Checkpoint de merge permanece pendente; esta apresentação não representa merge ou entrega do MVP completo.

## CryptoService — 2026-09-09

Por solicitação explícita do usuário, implementado somente o recorte de cifra AES/GCM/NoPadding, publicação em archive, persistência SQLite e exclusão do ZIP após sucesso. A solicitação autoriza implementar e testar essa exclusão em work com dados sintéticos; não autoriza exclusão em fast ou operações sobre evidências reais. ADRs mantidos Accepted e stack/dependências preservadas.

CryptoService.encrypt(Long, Path) exige ARQUIVANDO e o ZIP irmão da cópia .dd apontada pelo registro em work. Gera senha de 10 alfanuméricos com SecureRandom, salt de 16 bytes, chave AES de 256 bits por PBKDF2WithHmacSHA256/600000 iterações, IV novo de 12 bytes e tag GCM de 128 bits. Cifra por streaming em temporário próprio, finaliza tag e fecha antes de publicar sem sobrescrita em archive/<id-interno>/<nome>.dd.zip.enc. Registra chave/IV/salt em Base64, senha, iterações, versão 1 e paths no SQLite. A transação retorna após commit antes de excluir o ZIP. A chamada rejeita transação externa ativa; não altera estados nem remove .dd. O contrato e o formato foram documentados no README.

Validação inicial `./mvnw -Dtest=CryptoServiceTest test`: falhou antes dos testes por indisponibilidade de autoattach do Mockito/Byte Buddy. Reexecução com agente Mockito já presente: 17 testes passaram. Após adicionar os casos de IV novo após falha e falha de commit posterior ao flush, executada a suíte completa:

```bash
./mvnw '-DargLine=-javaagent:/home/josemberg/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

Resultado: BUILD SUCCESS, 114 testes, 0 falhas, 0 erros, 0 ignorados; 19 do CryptoService. Cobertura: bytes cifrados e tag final comparados a criptografia independente, tamanho chave/IV/salt, senha e derivação, releitura SQLite por conexão física antes da exclusão, parâmetros e paths persistidos, hashes preservados, IV novo inclusive após falha, temporários separados, escrita/fechamento/finalização falhos, falha SQLite real por trigger, commit simulado com rollback após flush, exclusão falha com metadados mantidos, colisão sem sobrescrita, ZIP inexistente, ZIP alheio/escape/symlinks, bloqueio dos outros cinco estados e de transação externa. Verificada ausência de chave/senha no log capturado da operação bem-sucedida; nenhum valor de chave foi incluído em código, documentação ou Git.

Limitações: banco e filesystem não são atômicos; parcial pode permanecer, e falha de persistência após publicação deixa cifrado sem garantia de metadados confirmados, preservando o ZIP. Após falha de limpeza, artefato e metadados permanecem e nova chamada recusa recifrar. Retomada coordenada da etapa segura e limite de tentativas continuam pendentes no item 5.4. Não há isolamento contra substituição concorrente de paths ou recuperação automática pós-crash. Chave/senha no SQLite e .dd aberto em work são limitações acadêmicas, explicitadas no README; logs Hibernate que poderiam revelar parâmetros/conteúdo de entidade ficam OFF. As regras Git existentes não foram alteradas. Não foram usados dados reais, nem implementados restauração/descriptografia, exclusão em fast, transições novas ou fluxo completo.

Checklist para revisão: CryptoService e campos persistentes implementados; testes adequados executados; limitações e formato documentados; item 5.2 concluído (7/26 tarefas), item 5.3 parcialmente implementado, demais checkpoints preservados. Encerrado o recorte solicitado sem commit, push, merge, Docker ou deploy; este relatório é submissão para revisão, não entrega/merge do MVP.

## ZipService isolado — 2026-09-09

Implementado somente ZipService.compress(Path), conforme solicitação do usuário para compactar o .dd em storage/cold/work e parar sem criptografar ou excluir ZIP. Raiz configurável por forenstorage.storage.work (padrão storage/cold/work). Recebe a cópia .dd já existente em work, inclusive em subdiretório próprio da evidência, e cria arquivo irmão <nome>.dd.zip com uma única entrada contendo o nome original. Usa streaming da biblioteca padrão Java e retorna o path somente após fechamento bem-sucedido, preservando o .dd. CREATE_NEW recusa sobrescrita de destino existente. Rejeita caminhos externos, symlinks na origem, extensão incorreta e arquivos não regulares/ilegíveis. Não adiciona dependências nem integra ações de arquivamento, banco ou estados.

Validação executada: `./mvnw -Dtest=ZipServiceTest test` — BUILD SUCCESS; 7 testes, 0 falhas, 0 erros, 0 ignorados. Verificados ZIP legível com diretório central, entrada única/nome/tamanho e bytes binários exatos em 100000 bytes, arquivo vazio, origem inexistente sem ZIP residual, preservação da origem, colisão sem sobrescrita, escape de work, extensão/diretório inválidos e symlinks de origem/destino. Somente dados sintéticos em diretórios temporários. Suíte completa não reexecutada nesta etapa.

Limitações: falha durante escrita/fechamento pode deixar ZIP parcial, que é preservado e não representa sucesso; retry e acompanhamento de etapa permanecem no item 5.4. Validações de path não garantem proteção contra substituição concorrente por processos externos. Não houve criptografia, exclusão de ZIP/.dd, recálculo de hash, movimentação de fast, alteração de hook, Docker, commit, push ou merge. Checklist deste recorte: serviço implementado, testes solicitados executados e resultados registrados; item amplo 5.2 e checkpoints posteriores continuam pendentes. Registro submetido para revisão, sem representar entrega/merge do MVP.

## Evidências das aprovações iniciais

Em 2026-09-07, o usuário autorizou registrar as interpretações consultadas como propostas para revisão. Em 2026-09-08, o usuário aprovou explicitamente o plano e os três ADRs nesta conversa:

```text
APROVADO: plano
APROVADO: ADR-0001
APROVADO: ADR-0002
APROVADO: ADR-0003
```

As tarefas 1.1–1.5 estão concluídas pela aprovação do plano apresentado e dos ADRs, agora Accepted. As tarefas 2.1–7.5 permanecem pendentes. Na ocasião dessas aprovações, nenhuma implementação ou teste da aplicação havia sido realizado. Aprovação de dependências específicas, exclusão-fast-storage, execução do hook/Docker, ações destrutivas e merge não foi concedida por essas mensagens.

## Correção pontual da fundação web — 2026-09-08

Por solicitação explícita do usuário, foi implementada somente a página inicial: HomeController com GET / retornando a view index e template com título ForenStorage, subtítulo e mensagem solicitados. HomeControllerTest verifica HTTP 200 e a view index usando MockMvc standalone.

Validação: `./mvnw test`, executado em `src/web`, terminou com BUILD SUCCESS: 2 testes, 0 falhas, 0 erros e 0 ignorados, incluindo o teste de contexto existente. O teste de controller verifica mapeamento e nome da view; não renderiza o template Thymeleaf.

A fundação existente usa Spring Boot 4.0.8, divergindo de 3.x no plano; pom.xml e dependências não foram alterados nesta correção. As tarefas amplas 2.2 e 3.3 permanecem pendentes, pois esta página não implementa cadastro ou listagem. Nenhuma funcionalidade futura, commit, push ou merge foi realizada nesta correção.

## Alinhamento da fundação ao Spring Boot 3.x — 2026-09-08

Por solicitação explícita do usuário, pom.xml foi ajustado de Spring Boot 4.0.8 para 3.5.16, mantendo Java 21. Os starters webmvc e os três starters de teste específicos do Boot 4 foram substituídos por spring-boot-starter-web e spring-boot-starter-test, compatíveis com a linha 3 e JUnit 5. Demais dependências existentes continuam gerenciadas pelo parent. A divergência de versão mencionada no registro anterior está resolvida.

HomeControllerTest agora usa @WebMvcTest e verifica HTTP 200, view index e os três textos do HTML renderizado por Thymeleaf. `./mvnw test` em src/web: BUILD SUCCESS, 2 testes, 0 falhas, 0 erros, 0 ignorados; o teste de contexto existente também passou. Nenhuma funcionalidade futura foi implementada e nenhum commit, push ou merge foi realizado.

## Modelo de evidência e persistência — escopo solicitado como “tarefa 4”

Implementados Evidence, EvidenceStatus e EvidenceRepository, com os campos id, evidenceIdentifier, currentPath, informedHash, calculatedHash, status, archivedPath, encryptionIv, errorMessage e createdAt. Estados persistidos como texto; criação com EM_ANALISE ou HASH_DIVERGENTE; demais alterações passam por transitionTo, sem setter público de status. A entidade recebe os hashes e o estado inicial do chamador: cálculo e comparação de hash não foram implementados nesta etapa.

Mantida a configuração SQLite existente em forenstorage.db. schema.sql cria o índice único do identificador após inicialização JPA; a atualização automática de constraints únicas pelo Hibernate foi desativada porque SQLite não suporta o ALTER TABLE emitido. O teste de duplicidade verifica a causa SQLITE_CONSTRAINT_UNIQUE, encapsulada pelo dialeto em uma exceção Spring de acesso a dados.

Validação: ./mvnw test na raiz terminou com BUILD SUCCESS: 54 testes, 0 falhas, 0 erros, 0 ignorados. Cobertura: 36 pares de transição, destinos nulos, estados bloqueados, estados iniciais inválidos, mensagem de erro, persistência e releitura, nova conexão física SQLite, metadados opcionais, data preservada na atualização e unicidade. Testes de persistência usam diretório temporário; o teste de contexto usa SQLite em memória para não alterar o banco local.

A numeração desta solicitação não corresponde à seção 4 acima (checkpoint de exclusão); esse checkpoint continua pendente. Os itens amplos 2.3 e 3.2 permanecem pendentes: ainda não há separação no caminho data/arqfor.db, teste de reinício da aplicação nem camada de serviço. Senha/chave/salt e outros metadados futuros do ADR-0003 não foram adicionados além dos campos expressamente solicitados. Não houve cadastro web, cálculo de hash, movimentação, ZIP, criptografia, Docker, commit, push ou merge.

## HashService SHA-256 por streaming — 2026-09-08

Implementado somente HashService.calculateSha256(Path): leitura por InputStream com buffer fixo de 8192 bytes, fechamento automático do stream e retorno hexadecimal em minúsculas. Arquivo inexistente produz IOException com mensagem “Arquivo não encontrado: <path>” e preserva a causa NoSuchFileException. Outros erros de leitura são propagados, sem retornar hash parcial.

Validação: ./mvnw -Dtest=HashServiceTest test — BUILD SUCCESS, 4 testes, 0 falhas, 0 erros, 0 ignorados. Vetores conhecidos: abc, arquivo vazio e um milhão de caracteres a (múltiplos buffers); também testada mensagem para arquivo inexistente. Arquivos sintéticos em diretórios temporários; nenhum arquivo de storage foi alterado. A suíte completa não foi reexecutada nesta etapa.

O item 3.1 permanece pendente porque inclui cadastro e validações além deste serviço isolado. Não houve integração com cadastro, comparação de hashes, alteração de status ou implementação de tarefas posteriores.

## Caso de uso de cadastro — 2026-09-08

Implementado EvidenceRegistrationService.register(String evidenceIdentifier, Path currentPath, String informedHash). Valida identificador obrigatório, SHA-256 hexadecimal com 64 caracteres e arquivo .dd regular/legível dentro da raiz rápida, rejeitando symlinks e escape de path. A raiz pode ser configurada por forenstorage.storage.fast, com padrão storage/fast. Persiste o path real absoluto e preserva o hash informado; o calculado vem do HashService em minúsculas. Comparação sem distinção de caixa determina EM_ANALISE ou HASH_DIVERGENTE, sem aceitar status fornecido pelo chamador.

Duplicidade é verificada previamente e protegida pelo índice único SQLite; violação desse índice após a consulta inicial recebe mensagem clara de identificador já cadastrado. Demais erros de banco são propagados sem serem classificados como duplicidade. O arquivo é lido antes de iniciar a transação de persistência do repositório; falha na leitura não salva cadastro parcial. Arquivos não são alterados.

HASH_DIVERGENTE permanece bloqueado para todos os destinos, incluindo ARQUIVANDO, pela entidade existente. Não foi implementada ação de arquivamento. Validação de path ocorre antes da leitura, sem garantia contra troca concorrente de arquivos/symlinks por processos externos entre validação e abertura.

./mvnw test: BUILD SUCCESS, 75 testes, 0 falhas, 0 erros, 0 ignorados; 17 testes do caso de uso. Cobertura inclui hash correto (informado em maiúsculas), divergência persistida, bloqueios, duplicidade sem sobrescrita, violação de índice simulada após pré-consulta, falha distinta de banco, hash malformado, identificador vazio/nulo, arquivo inexistente, extensão inválida, diretório, path externo/escape, symlinks e leitura interrompida. SQLite e arquivos sintéticos temporários, sem dados reais.

Item 3.1 concluído para cadastro na camada de serviço. Telas, movimentação, ZIP, AES-GCM e tarefas posteriores não foram implementados. Nenhum commit, push ou merge foi realizado.

## Telas de cadastro, listagem e detalhes — 2026-09-08

Implementado o recorte solicitado do item 3.3: GET /evidences lista registros e oferece Adicionar; GET /evidences/new apresenta formulário com Cancelar/Cadastrar; POST /evidences utiliza o caso de uso existente e redireciona à listagem após sucesso; GET /evidences/{id} apresenta detalhes ou HTTP 404. A página inicial oferece acesso à listagem. Erros de cadastro preservam os valores do formulário e exibem mensagem; falhas de banco não expõem detalhes internos.

Detalhes exibem identificador, status, path atual, ambos os hashes, data e, quando presentes, path arquivado e mensagem de erro. HASH_DIVERGENTE recebe alerta visível e orientação para remover e cadastrar novamente. Arquivamento e alteração de status têm botões desabilitados para todos os estados nesta etapa, sem endpoints de execução. POST com status arbitrário é rejeitado. Não foi implementada remoção de registro (item 3.4).

O item amplo 3.3 permanece pendente quanto à exibição da senha persistida, campo ainda ausente do modelo; a tela informa “Ainda não gerada.” neste estágio sem criptografia. Os campos e as telas solicitados nesta rodada estão implementados.

Validação: ./mvnw test — BUILD SUCCESS, 88 testes, 0 falhas, 0 erros, 0 ignorados. Os 13 novos casos de controller usam MockMvc com renderização Thymeleaf e serviço/repositório simulados: listagem vazia/preenchida, navegação, cancelamento sem cadastro, sucesso/divergência, duplicidade, arquivo ausente, falha de banco, status adulterado/path vazio, detalhes/bloqueios, escape de HTML e evidência inexistente. A suíte existente de serviço, modelo e persistência também passou. Nenhum arquivamento real, movimentação, ZIP, AES-GCM, Docker, commit, push ou merge foi realizado.
