# Revisão e testes do guardrail — task 2.4 concluída

## Integração real comprovada — 2026-09-11

Após a solicitação explícita do usuário para concluir 2.4 e 2.5, a cópia descartável foi reconstruída em `/tmp/arqfor-hook-validation/workspace`. O script é cópia byte a byte do original. A confiança existente corresponde ao hash exato da definição: `hooks/list` confirmou `source: project`, `matcher: ^Bash$`, `enabled: true`, `trustStatus: trusted`, sem erros.

Duas sessões novas de `codex exec` 0.149.1 usaram a ferramenta `exec_command` pelo fluxo normal do runtime, sem chamar o hook diretamente:

| Comando solicitado | Resultado nativo | Evidência |
|---|---|---|
| `true` | `Command blocked by PreToolUse hook` | [Chamada e resultado](evidence/hooks-2026-09-11/fast-true.json) |
| `rm -- storage/fast/sentinela.dd` | `Command blocked by PreToolUse hook` | [Chamada e resultado](evidence/hooks-2026-09-11/fast-remove.json) |

Mensagem do guardrail em ambos: `BLOQUEADO: shell desabilitado pelo guardrail de storage/fast. Revisão humana obrigatória; não contornar o hook.` A tentativa de remoção ocorreu somente depois do bloqueio de `true`. A [sentinela sintética continuou existente com os mesmos 25 bytes](evidence/hooks-2026-09-11/fast-sentinel.json). O [carregamento e a confiança](evidence/hooks-2026-09-11/fast-hooks-list.json) foram consultados pelo app-server 0.153.4.

Os JSONs preservam chamadas e resultados extraídos dos transcripts nativos, com sessão, call_id e timestamps; não são saídas fabricadas nem apenas relatos do agente. Os prompts e demais mensagens foram omitidos. Nenhum bypass de confiança, sandbox ou política foi usado. O projeto principal permanece com matcher inerte. O bloqueio de integração por limite de uso relatado abaixo foi superado nesta execução; os parágrafos seguintes registram o histórico.

## Escopo e autorização

O usuário solicitou nesta conversa revisar, habilitar e testar o hook em ambiente descartável e parar após a tarefa. Essa autorização cobre a cópia de teste e arquivos sintéticos; não altera a política de bloqueio, não autoriza dados reais, commit ou avanço para Docker. A aprovação anterior da task 3.4 permanece independente.

## Revisão realizada

Fonte: `.codex/hooks.json` e `.codex/hooks/bloquear-exclusao-fast.sh`. O script não interpreta stdin, não executa o comando recebido e não acessa arquivos: escreve o motivo no stderr e termina com código 2. O matcher original permanece `^ARQFOR_HOOK_DESABILITADO$`. Não foi criada exceção para comandos de leitura ou indiretos.

Codex CLI instalado: `0.149.1`. `codex features list` informou `hooks stable true`. Segundo a [documentação oficial de hooks](https://learn.chatgpt.com/docs/hooks), `^Bash$` cobre shell e `exec_command`, e código 2 bloqueia antes de executar. Há duas condições adicionais: confiança no projeto para carregar `.codex/` e confiança na definição exata do hook para executá-lo. Hooks novos ou alterados precisam de revisão em `/hooks`.

## Testes de contrato executados

```bash
bash scripts/test-hook-contract.sh
```

O teste usa Bash já disponível, copia o script para um diretório novo sob `/tmp`, cria uma sentinela sintética e envia payloads como texto. Nenhum comando contido nos payloads é executado. O script preserva os resultados temporários para inspeção.

16 casos: leitura, `rm`, `mv`, sobrescrita, exclusão do diretório pai, path absoluto, mudança de cwd, variável, referência a symlink, interpretador, shell indireto, pipeline, entrada vazia, JSON inválido, `null` e formato `cmd` de `exec_command`. Cada caso exige código 2, stdout vazio, motivo no stderr e sentinela preservada. `bash -n` também verifica a sintaxe do hook. O caso de symlink testa somente o texto de entrada, não uma operação real por symlink.

Resultado inicial: 16/16 passaram em cópia descartável. Isso comprova o contrato do script; não comprova que o Codex o intercepta antes de executar ferramentas. Não foram necessários testes JUnit adicionais: nenhum comportamento Java foi alterado, e a suíte da aplicação não foi reexecutada.

## Preparação e tentativa de integração

Preparada uma cópia mínima em `/tmp/arqfor-hook-validation/workspace`, contendo somente o hook, um `hooks.json` com matcher `^Bash$`, metadados de um repositório Git local vazio e `storage/fast/sentinela.dd` sintético. O caminho do comando aponta para a cópia. Não foram copiados banco, storages ou credenciais reais, nem feito commit. O matcher ativo no arquivo não equivale a execução efetiva do hook.

A primeira consulta de `hooks/list` retornou lista vazia porque a cópia ainda não era confiável. Nesta retomada, a confiança foi registrada somente para `projects."/tmp/arqfor-hook-validation/workspace".trust_level = "trusted"` pela API suportada `config/value/write`; não houve alteração do matcher do projeto principal. A consulta seguinte carregou o hook com matcher `^Bash$`, origem `project`, `enabled: true`, sem warnings/errors e `trustStatus: trusted`. A definição exata também foi registrada como confiável pelo hash atual usando `hooks.state` na mesma API.

Uma chamada direta `command/exec` do app-server retornou código 0, mas não é evidência de PreToolUse: esse método executa um processo diretamente e não passa pelo fluxo de ferramenta shell. Em seguida foi iniciada uma sessão nova pelo `codex exec` na cópia descartável, com a instrução de executar somente o comando inofensivo `true`; a sessão falhou antes de chamar qualquer ferramenta por limite de uso da conta. Nenhum comando destrutivo foi submetido, a sentinela permaneceu intacta e não foi usado bypass de confiança ou alteração da política.

## Pendência anterior — resolvida na integração acima

A task 2.4 permanece aberta. A confiança da pasta e da definição foi registrada, mas falta uma sessão nova do Codex CLI que efetivamente alcance a ferramenta shell e comprove a recusa de um comando inofensivo. Só depois dessa evidência será possível testar, ainda na cópia descartável, uma mutação da sentinela e conferir seus bytes. A configuração registrada para a fixture é:

```toml
[projects."/tmp/arqfor-hook-validation/workspace"]
trust_level = "trusted"
```

Próximo passo: repetir a sessão nova quando houver cota disponível; comprovar recusa de comando inofensivo; só então testar mutações sobre a sentinela descartável, conferir seus bytes e registrar a recusa anterior à execução. Não considerar a tarefa concluída antes dessas evidências. Não ativar a regra no projeto de trabalho como substituto dessa validação.

## Limites

A política bloqueia todo shell, incluindo leituras, quando efetivamente carregada e confiável. Não é sandbox do sistema operacional. Não protege a aplicação Java em execução, terminais externos, outras ferramentas fora do matcher ou processos já iniciados. `write_stdin` não executa novamente `PreToolUse`; uma sessão antiga não comprova a proteção de uma sessão nova. A ativação permanente no projeto de trabalho não foi realizada neste recorte.
