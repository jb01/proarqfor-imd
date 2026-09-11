# Demonstração Docker — tarefa 7.2

Status: tarefa 7.2 concluída em 2026-09-11. Demonstração executada, persistência após reinício real comprovada e container parado. Preparação e impedimentos anteriores estão preservados abaixo como histórico.

## Resultado da execução — 2026-09-11

Build da imagem concluído e aplicação iniciada em 127.0.0.1:18080. Após o reinício do computador, a fixture temporária foi recriada no mesmo caminho e com os mesmos conteúdos autorizados. Nenhum dado do projeto foi montado. Aprovações anteriores de Docker e exclusões sintéticas foram respeitadas.

| Verificação | Resultado |
|---|---|
| Cadastro pela interface HTTP | TASK72-VALIDA em EM_ANALISE; TASK72-DIVERGENTE em HASH_DIVERGENTE |
| Arquivamento válido | ARQUIVADO, origem sintética e ZIP aberto removidos; .dd de trabalho e .zip.enc preservados |
| Arquivamento divergente | Bloqueado; registro permaneceu inalterado |
| Persistência SQLite | Todos os campos das duas linhas idênticos antes/depois, incluindo hashes, paths, datas, estados e parâmetros criptográficos |
| Persistência dos arquivos | Mesmos três paths e bytes: .dd de trabalho (21504 bytes), .dd divergente (25600 bytes) e .zip.enc (244 bytes) |
| Interface após reinício | HTTP 200 e identificador/estado corretos nos detalhes dos dois registros |
| Encerramento | Serviço da demonstração parado; container, imagem, rede e dados preservados |

O mesmo container iniciou primeiro em `2026-09-11T23:07:38.388205507Z` e, após `docker compose restart web`, em `2026-09-11T23:11:58.094338066Z` (UTC). O mount externo apontou para `/tmp/arqfor-task72-n16tdmfb/data` durante a demonstração.

Evidências: [resultado estruturado sem chave/senha](evidence/docker-task72/result.json), [execução das verificações](evidence/docker-task72/verification.log), [status final](evidence/docker-task72/final-status.txt), [Compose usado](evidence/docker-task72/compose.json), [build](evidence/docker-task72/build-summary.log) e [script da retomada](evidence/docker-task72/verify.py). O script depende da fixture cadastrada nesta execução e não deve ser reexecutado sobre dados arbitrários.

A primeira verificação do .dd de trabalho falhou porque o processo no host não podia atravessar um diretório privado criado pelo container. [Registro inicial](evidence/docker-task72/initial-verification.log) preservado. A inspeção foi corrigida usando `docker cp` somente dos storages sintéticos para snapshots locais; não houve alteração de permissões, repetição de cadastro/arquivamento ou remoção adicional. A comparação foi feita diretamente entre bytes, sem recálculo de hash posterior ao cadastro.

Limites: demonstração HTTP automatizada, sem sessão visual de navegador; comprova reinício normal do container, não recuperação após crash nem atomicidade SQLite/filesystem. Dados em /tmp sobrevivem ao reinício do container, mas podem ser apagados no reinício/limpeza do host. O build usou `-DskipTests`: houve compilação, mas nenhuma nova execução JUnit. Os testes desta rodada são as verificações de integração descritas acima. Nenhum commit, push, merge ou deploy; a task 7.5 permanece pendente.

## Ambiente preparado

- Compose isolado: `/tmp/arqfor-task72-n16tdmfb/compose.json` (JSON compatível com Compose).
- Build: Dockerfile existente, contexto `/home/josemberg/Documentos/MESTRADO/DEV-ia/proarqfor-imd`; Maven 3.9.11/Java 21 e runtime eclipse-temurin:21-jre. O Dockerfile copia somente pom.xml e src.
- Imagem local: `arqfor-task72-demo:local`; pode baixar imagens base e dependências Maven.
- Projeto Compose: `arqfor-task72-demo`; serviço `web`.
- Acesso: `http://127.0.0.1:18080`; disponibilidade da porta ainda não comprovada, pois a sandbox negou criação de socket.
- Único bind mount: `/tmp/arqfor-task72-n16tdmfb/data` → `/var/lib/arqfor`.
- Dois arquivos sintéticos preparados em storage/fast; banco será criado pela aplicação nesse mount. Hash informado da amostra válida calculado na preparação; amostra divergente usa 64 zeros. Manifesto em `/tmp/arqfor-task72-n16tdmfb/fixtures.json`.
- Nenhum banco ou storage existente do projeto será montado. Dados da demonstração ficam preservados após parar o serviço.

## Comandos concretos para revisão e aprovação

Executar a partir de `/home/josemberg/Documentos/MESTRADO/DEV-ia/proarqfor-imd`, com Bash sem login. Não foram executados:

```bash
docker version
docker compose version
docker context show
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json config
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json up --build -d web
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json ps -a
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json logs --no-color --tail 100 web
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json restart web
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json ps -a
docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json stop web
```

Consultas de estado/logs podem ser repetidas durante a inicialização para verificar disponibilidade e diagnosticar falhas. Não há prune, down, remoção de volumes ou limpeza dos dados. Não iniciar/instalar daemon automaticamente caso indisponível. Mudança de contexto, mount ou comandos será apresentada novamente para revisão.

## Roteiro de verificação após autorização

1. Confirmar contexto local e ausência de serviço prévio com esse nome antes de subir; validar a configuração efetiva e o mount isolado.
2. Aguardar resposta HTTP da aplicação. Cadastrar pelo formulário POST /evidences `TASK72-VALIDA` e `TASK72-DIVERGENTE`, usando paths internos e hashes do manifesto. Esperado: EM_ANALISE e HASH_DIVERGENTE, respectivamente.
3. Com autorização destrutiva específica, arquivar somente `TASK72-VALIDA` por POST /evidences/{id}/archive. A aplicação copia o .dd sintético para work, confirma cópia, exclui exclusivamente sua origem em fast e o ZIP intermediário após cifra bem-sucedida; preserva .dd em work e .zip.enc em archive. Nunca acionar outros registros.
4. Guardar snapshot em memória dos registros SQLite, incluindo identificadores, hashes armazenados, estados, paths, datas e parâmetros criptográficos; guardar bytes dos arquivos sintéticos finais para comparação direta, sem recalcular hashes. Salvar relatório público apenas com resultados de igualdade, sem expor chave/senha.
5. Reiniciar o serviço pelo comando revisado. Aguardar nova disponibilidade; comparar os registros e bytes, verificar ambos os cadastros pela interface e ARQUIVADO/HASH_DIVERGENTE preservados.
6. Registrar evidências de antes/depois, configuração do mount, reinício e resultados reais; parar somente o serviço da demonstração. Marcar 7.2 concluída apenas se a persistência for comprovada. Não avançar para 7.5.

## Checkpoints e impedimento atual

A task 7.2 e AGENTS.md exigem `APROVADO: Use o docker.` vinculada aos comandos acima. A operação do passo 3 exige autorização expressa para exclusão do arquivo sintético `/tmp/arqfor-task72-n16tdmfb/data/storage/fast/valida.dd` e do seu ZIP intermediário pelo fluxo da aplicação. A aprovação anterior de implementação de exclusão não autoriza essa execução concreta.

Após o usuário informar a instalação, a nova inspeção localizou `/usr/bin/docker` no PATH e confirmou a presença do Compose preparado, manifesto e duas amostras sintéticas em `/tmp/arqfor-task72-n16tdmfb`. O impedimento de executável ausente foi resolvido. Disponibilidade do daemon e do plugin Compose ainda não foi testada: os comandos Docker aguardam a frase de aprovação exigida por 7.2. Nenhuma exclusão sintética foi executada.

Hooks do projeto permanecem inertes conforme configuração existente; nenhuma política foi modificada. A retirada da tarefa 2.5 não substitui autorização de execução nem permite contornar recusas. Preparação não equivale a aprovação ou teste realizado.

## Aprovações recebidas e tentativa de execução

O usuário enviou `APROVADO: Use o docker.` e respondeu `Autorizo as exclusões sintéticas descritas` à pergunta que identificou `/tmp/arqfor-task72-n16tdmfb/data/storage/fast/valida.dd` e o ZIP intermediário da demonstração. Autorizações restritas ao roteiro acima; não precisam ser solicitadas novamente para esse mesmo escopo.

Resultados realmente observados:

| Comando | Resultado |
|---|---|
| `docker version` | Cliente 29.8.0 disponível; acesso ao servidor negado |
| `docker compose version` | Docker Compose v5.5.1 |
| `docker context show` | `default` |
| `docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json config` | Configuração válida; mount sintético e publicação 127.0.0.1:18080 confirmados |
| `docker compose -p arqfor-task72-demo -f /tmp/arqfor-task72-n16tdmfb/compose.json ps -a` | Acesso ao daemon negado |
| `sudo -n docker version` | Autenticação interativa exigida; nenhuma senha solicitada pelo agente |

Erro do daemon, reproduzido também na chamada com permissão de execução ampliada pela revisão automática:

```text
permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

Tentativa administrativa não interativa:

```text
sudo: interactive authentication is required
```

A revisão automática permitiu as consultas; não houve rejeição pelo auto-review ou pelos hooks. O impedimento é a permissão efetiva de acesso ao daemon. Nenhum grupo, permissão de socket ou configuração pessoal foi alterado. Não houve build, início ou reinício de container, cadastro, arquivamento ou exclusão sintética. Para retomar, o ambiente da sessão precisa conseguir consultar o servidor com `docker version`; as aprovações do roteiro continuam registradas.
