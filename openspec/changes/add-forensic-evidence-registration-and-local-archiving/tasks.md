## Situação atual — checklist reconciliado com a implementação

13 das 26 tarefas numeradas estão integralmente concluídas; 13 continuam abertas, incluindo atividades parcialmente implementadas e checkpoints. Essa contagem não representa percentual de código pronto. As notas abaixo distinguem o que já existe do que falta, sem criar novas aprovações nem alterar os critérios originais.

Concluído tecnicamente: fundação Java 21/Spring Boot 3.5.16/Maven; entidade/repositório SQLite e metadados criptográficos; cadastro SHA-256; listagem/formulário/detalhes com senha persistida; matriz de estados na entidade; ZIP; AES-GCM; cópia segura; arquivamento síncrono com uma retomada global e ERRO, integrado à ação Arquivar na interface. Aprovações do plano, dos três ADRs e da exclusão-fast-storage estão registradas.

Pendências práticas principais: implementar remoção confirmada somente do registro e desarquivamento simulado com sua integração web, concluir organização/configuração dos dados e teste de reinício, revisar os registros nominais de dependências, validar hook quando autorizado e realizar demonstração/revisão final.

Última execução comprovada: 183 testes, 0 falhas, 0 erros, 0 ignorados, conforme relatórios Surefire e registro da integração web abaixo. Nenhuma nova dependência ou commit. Os relatos posteriores neste arquivo são históricos: expressões como “pendente” ou “não implementado” neles descrevem a ocasião do registro, não substituem este checklist atual.

## 1. Revisão humana antes de qualquer implementação

- [x] 1.1 Revisar os artefatos e confirmar as interpretações de exclusão do registro, status por ações, path em fast, derivação AES, cópia .dd retida em work e retorno .zip.enc; verificar decisão humana registrada e coerência entre specs/design/ADRs.
- [x] 1.2 Obter `APROVADO: plano`; verificar mensagem humana explícita antes de avançar.
- [x] 1.3 Obter `APROVADO: ADR-0001`; verificar mensagem humana e só então atualizar status do ADR.
- [x] 1.4 Obter `APROVADO: ADR-0002`; verificar mensagem humana e só então atualizar status do ADR.
- [x] 1.5 Obter `APROVADO: ADR-0003`; verificar mensagem humana, incluindo derivação proposta, e só então atualizar status do ADR.

## 2. Preparação futura autorizada

- [ ] 2.1 Propor versões Spring Boot 3.x e dependências mínimas, inclusive driver SQLite e dialeto JPA, sem trocar a stack; verificar aprovação humana nominal antes de configuração.
  - Parcial: Spring Boot 3.5.16/Java 21 alinhados por solicitação registrada; driver sqlite-jdbc e hibernate-community-dialects já estão no pom.xml. Falta evidência nominal completa da aprovação das dependências exigida por este item; a existência no código não equivale a essa aprovação.
- [ ] 2.2 Após 1 e 2.1, criar projeto Java 21/Maven com stack aprovada; verificar compilação e teste básico JUnit 5, sem Docker.
  - Implementação técnica concluída e compilação/contexto testados. Item mantido aberto apenas pela dependência formal do fechamento de 2.1; não é necessário recriar a fundação.
- [ ] 2.3 Implementar persistência SQLite e separação de diretórios de dados; verificar unicidade, campos opcionais criptográficos e releitura após reinício com banco temporário.
  - Parcial: entidade/repositório, unicidade, campos criptográficos e releitura por nova conexão SQLite implementados/testados. Falta alinhar o banco atual jdbc:sqlite:forenstorage.db com data/arqfor.db e a raiz de dados planejada, além de comprovar releitura após reinício da aplicação (nova conexão isolada não é reinício).
- [ ] 2.4 Revisar e, com autorização específica, habilitar/testar hook em ambiente descartável conforme README; verificar bloqueio antes de execução e registrar limitações de cobertura.
  - Estrutura já criada; matcher permanece inerte. Habilitação e testes de integração continuam pendentes de autorização específica.

## 3. Cadastro, telas e estados

- [x] 3.1 Implementar cadastro de .dd, validação de identificador/path/hash e SHA-256 por streaming; verificar JUnit 5 para hash igual/divergente, caixa hexadecimal, duplicidade, symlink/escape e leitura falha.
- [ ] 3.2 Implementar estados e transições permitidas no serviço; verificar todos os pares permitidos e proibidos, incluindo bloqueio total de HASH_DIVERGENTE/ERRO.
  - Parcial: matriz completa testada na entidade; cadastro/arquivamento e seus bloqueios implementados nos serviços. Faltam as transições operacionais e falhas do serviço de desarquivamento (6.1/6.2), sem adicionar seletor livre de status.
- [x] 3.3 Implementar listagem, Adicionar, cadastro/Cancelar/Cadastrar e detalhes com todos os campos; verificar navegação MVC e erros de formulário, usando apenas ferramentas já aprovadas.
  - Navegação, cadastro, listagem e detalhes completos, incluindo senha persistida com escape HTML e indicação de ausência. Arquivar conectado ao ArchivingService por POST, com mensagens e bloqueios testados. A integração de Desarquivar acompanha 6.1/6.2 e continua pendente; não há edição arbitrária de status.
- [ ] 3.4 Implementar remoção somente de registro com confirmação e cancelamento; verificar registro removido, arquivo preservado e bloqueio durante operação em curso.
  - Pendente: não há fluxo de remoção implementado; incluir confirmação com identificador, cancelamento, preservação dos arquivos e bloqueio durante operação.

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

- [ ] 6.1 Implementar movimento de .zip.enc para fast com transições e atualização de path; verificar conteúdo e extensão preservados, sem descriptografia, descompactação ou recálculo de hash.
  - Pendente: serviço de desarquivamento e sua ação web ainda não existem. O retorno deve ser apenas simulado, preservando o artefato cifrado e os metadados.
- [ ] 6.2 Implementar erro do movimento e rejeição de re-arquivamento do .zip.enc conforme decisão revisada; verificar ERRO na falha, preservação de metadados e orientação de novo cadastro .dd.
  - Parcial: ArchivingService já recusa .zip.enc/artefato anteriormente arquivado e orienta novo cadastro. Falta tratamento/teste da falha de movimento no desarquivamento e validação do ciclo completo de retorno.

## 7. Demonstração e revisão final futuras

- [ ] 7.1 Preparar Docker Compose somente para demonstração final local, após implementação; verificar configuração revisada com dados e storages persistentes fora do container, sem executar Docker automaticamente.
  - Pendente: nenhum Dockerfile/Compose encontrado no projeto; preparar somente na etapa final prevista.
- [ ] 7.2 Obter autorização específica para executar Docker e demonstração destrutiva com dados sintéticos; verificar autorização antes de executar e demonstrar persistência após reinicialização.
- [ ] 7.3 Executar validação integrada autorizada: cadastro correto/divergente, bloqueios, arquivamento, duas falhas e retorno simulado; verificar resultados reais registrados, arquivos e metadados esperados.
  - Parcial: 183 testes cobrem os recortes implementados, incluindo cadastro/arquivamento/detalhes via MVC com serviços e SQLite reais temporários; falta validação do fluxo final com retorno simulado. Não confundir a suíte atual com a demonstração completa do MVP.
- [ ] 7.4 Apresentar alterações, testes executados, limitações, riscos e checklist; verificar relatório humano revisável sem alegar testes não executados.
  - Relatórios por recorte e este checklist já existem. A revisão final do MVP permanece pendente até completar o escopo e executar sua validação integrada.
- [ ] 7.5 Parar e obter `APROVADO: merge` antes de merge ou entrega da implementação; verificar mensagem explícita e autorização específica para qualquer commit/push/deploy necessário.

## Registro de aprovações

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
