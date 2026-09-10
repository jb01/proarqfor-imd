## 1. Revisão humana antes de qualquer implementação

- [x] 1.1 Revisar os artefatos e confirmar as interpretações de exclusão do registro, status por ações, path em fast, derivação AES, cópia .dd retida em work e retorno .zip.enc; verificar decisão humana registrada e coerência entre specs/design/ADRs.
- [x] 1.2 Obter `APROVADO: plano`; verificar mensagem humana explícita antes de avançar.
- [x] 1.3 Obter `APROVADO: ADR-0001`; verificar mensagem humana e só então atualizar status do ADR.
- [x] 1.4 Obter `APROVADO: ADR-0002`; verificar mensagem humana e só então atualizar status do ADR.
- [x] 1.5 Obter `APROVADO: ADR-0003`; verificar mensagem humana, incluindo derivação proposta, e só então atualizar status do ADR.

## 2. Preparação futura autorizada

- [ ] 2.1 Propor versões Spring Boot 3.x e dependências mínimas, inclusive driver SQLite e dialeto JPA, sem trocar a stack; verificar aprovação humana nominal antes de configuração.
- [ ] 2.2 Após 1 e 2.1, criar projeto Java 21/Maven com stack aprovada; verificar compilação e teste básico JUnit 5, sem Docker.
- [ ] 2.3 Implementar persistência SQLite e separação de diretórios de dados; verificar unicidade, campos opcionais criptográficos e releitura após reinício com banco temporário.
- [ ] 2.4 Revisar e, com autorização específica, habilitar/testar hook em ambiente descartável conforme README; verificar bloqueio antes de execução e registrar limitações de cobertura.

## 3. Cadastro, telas e estados

- [x] 3.1 Implementar cadastro de .dd, validação de identificador/path/hash e SHA-256 por streaming; verificar JUnit 5 para hash igual/divergente, caixa hexadecimal, duplicidade, symlink/escape e leitura falha.
- [ ] 3.2 Implementar estados e transições permitidas no serviço; verificar todos os pares permitidos e proibidos, incluindo bloqueio total de HASH_DIVERGENTE/ERRO.
- [ ] 3.3 Implementar listagem, Adicionar, cadastro/Cancelar/Cadastrar e detalhes com todos os campos; verificar navegação MVC e erros de formulário, usando apenas ferramentas já aprovadas.
- [ ] 3.4 Implementar remoção somente de registro com confirmação e cancelamento; verificar registro removido, arquivo preservado e bloqueio durante operação em curso.

## 4. Checkpoint obrigatório antes de código de exclusão em fast

- [ ] 4.1 Apresentar fluxo de cópia, fechamento, comparação de tamanho, persistência e exclusão, com matriz de falhas e testes em temporários; verificar material revisável e ausência de hash posterior.
- [ ] 4.2 Parar e obter `APROVADO: exclusão-fast-storage`; verificar mensagem explícita antes de implementar qualquer exclusão em fast. Autorização de execução destrutiva permanece separada.

## 5. Arquivamento síncrono após checkpoint

- [ ] 5.1 Implementar cópia fast → work e exclusão condicionada ao sucesso aprovado; verificar cópia incompleta, fechamento falho, tamanho divergente, colisão e falha ao excluir, preservando a última cópia utilizável.
- [ ] 5.2 Implementar ZIP e cifra conforme ADR-0003 aprovado; verificar senha de 10 alfanuméricos, chave válida, parâmetros persistidos, IV novo e preservação do ZIP na falha de finalização.
  - Recorte ZIP concluído em 2026-09-09 por solicitação explícita do usuário: ZipService e testes. O item completo permanece pendente de cifra e suas validações.
- [ ] 5.3 Implementar publicação .zip.enc, persistência de metadados e exclusão do ZIP aberto após sucesso; verificar arquivo final, path, senha/IV e ARQUIVADO, sem excluir .dd de trabalho automaticamente.
- [ ] 5.4 Implementar uma repetição por etapa segura e ERRO na segunda falha; verificar sucesso na segunda tentativa, limite de duas tentativas, retomada após origem removida e falha de SQLite/limpeza após cifra publicada.

## 6. Desarquivamento simulado

- [ ] 6.1 Implementar movimento de .zip.enc para fast com transições e atualização de path; verificar conteúdo e extensão preservados, sem descriptografia, descompactação ou recálculo de hash.
- [ ] 6.2 Implementar erro do movimento e rejeição de re-arquivamento do .zip.enc conforme decisão revisada; verificar ERRO na falha, preservação de metadados e orientação de novo cadastro .dd.

## 7. Demonstração e revisão final futuras

- [ ] 7.1 Preparar Docker Compose somente para demonstração final local, após implementação; verificar configuração revisada com dados e storages persistentes fora do container, sem executar Docker automaticamente.
- [ ] 7.2 Obter autorização específica para executar Docker e demonstração destrutiva com dados sintéticos; verificar autorização antes de executar e demonstrar persistência após reinicialização.
- [ ] 7.3 Executar validação integrada autorizada: cadastro correto/divergente, bloqueios, arquivamento, duas falhas e retorno simulado; verificar resultados reais registrados, arquivos e metadados esperados.
- [ ] 7.4 Apresentar alterações, testes executados, limitações, riscos e checklist; verificar relatório humano revisável sem alegar testes não executados.
- [ ] 7.5 Parar e obter `APROVADO: merge` antes de merge ou entrega da implementação; verificar mensagem explícita e autorização específica para qualquer commit/push/deploy necessário.

## Registro de aprovações

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
