# Revisão e testes do guardrail — task 2.4

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

A inicialização restrita do runtime falhou por limitações de filesystem/sandbox. Uma consulta autorizada com `codex app-server` e `hooks/list`, sem criar turno de modelo, iniciou e retornou lista vazia: a camada de configuração da cópia não foi carregada por falta de confiança no projeto. Nenhum comando destrutivo foi submetido às ferramentas do Codex; a etapa exige primeiro comprovar o bloqueio de um comando inofensivo.

A revisão automática de aprovação rejeitou o passo seguinte: usar `config/value/write` para adicionar somente `projects."/tmp/arqfor-hook-validation/workspace".trust_level = "trusted"` na configuração pessoal do Codex. O motivo foi que a alteração persistente fora do ambiente descartável excede a autorização recebida. Essa escrita não foi executada. Não foram usados bypass de confiança, alteração da política ou ferramenta alternativa para contornar a recusa.

## Pendência concreta

A task 2.4 permanece aberta. Para continuar, é necessária autorização específica para registrar na configuração pessoal do Codex a confiança da pasta descartável e, após inspeção, da definição exata do hook copiado. A configuração pretendida do projeto é:

```toml
[projects."/tmp/arqfor-hook-validation/workspace"]
trust_level = "trusted"
```

Após a autorização: verificar que `hooks/list` carrega a fonte correta sem erros; revisar/confiar na definição do hook pelo fluxo suportado; iniciar uma sessão nova na cópia; comprovar recusa de comando inofensivo; só então testar mutações sobre a sentinela descartável, conferir seus bytes e registrar a recusa anterior à execução. Não considerar a tarefa concluída antes dessas evidências. Não ativar a regra no projeto de trabalho como substituto dessa validação.

## Limites

A política bloqueia todo shell, incluindo leituras, quando efetivamente carregada e confiável. Não é sandbox do sistema operacional. Não protege a aplicação Java em execução, terminais externos, outras ferramentas fora do matcher ou processos já iniciados. `write_stdin` não executa novamente `PreToolUse`; uma sessão antiga não comprova a proteção de uma sessão nova. A ativação permanente no projeto de trabalho não foi realizada neste recorte.
