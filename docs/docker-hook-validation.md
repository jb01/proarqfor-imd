# Hook Docker — implementação e validação parcial da task 2.5

## Atualização — integração real em 2026-09-11

A interceptação foi comprovada em cópia descartável: Docker sem aprovação, frase forjada e chamada indireta receberam `Command blocked by PreToolUse hook`. Com os dois hooks ativos, fast manteve a recusa. Sentinelas preservadas e substituto Docker não executado. A confiança da fixture e das definições foi registrada pela API nativa; essa pendência anterior foi resolvida.

**A task 2.5 continua aberta por incompatibilidade do contrato:** o runtime envia somente `command` em `tool_input`, omitindo `shell`/`login`; até `true` foi bloqueado. A sessão CLI também produz `exec`/`paginated`, em vez de `vscode`/`legacy`, e seu transcript tem permissão 0664. Os testes negativos param antes de validar a aprovação. Não foi comprovada liberação legítima, nem relaxada a política. [Relatório, payload observado e evidências nativas](hook-integration-2026-09-11.md).

Os 17 testes isolados Docker e 16/16 de fast foram reexecutados com sucesso. Os parágrafos seguintes preservam o histórico anterior; referências à ausência de confiança/interceptação foram superadas pelo resultado acima, não os limites do caminho positivo de aprovação.

## O que foi implementado

O usuário solicitou executar a task 2.5. Criados `.codex/hooks/bloquear-docker.sh` e `.codex/hooks/bloquear-docker.py`, com entrada própria em `.codex/hooks.json`. A fonte fica com matcher `^ARQFOR_DOCKER_HOOK_DESABILITADO$` até a validação da ativação em cópia descartável. O hook de fast e seu matcher foram preservados.

O launcher usa Bash e Python 3 com biblioteca padrão já disponíveis. Não foram instalados pacotes, adicionadas dependências da aplicação ou criados componentes Java. O verificador não executa Docker, shell recebido, arquivos de projeto ou código extraído do transcript. O launcher transforma falha do verificador em código 2. Erros de validação retornam uma mensagem genérica, sem imprimir conteúdo da conversa.

## Política conservadora e autorização

O hook aceita somente chamadas Docker diretas e literais, com `shell=/bin/bash`, `login=false` e cwd absoluto canônico. Toda chamada Docker nessa gramática exige autorização, inclusive `docker run`, `docker container run`, opções globais e Compose. Expansões, encadeamentos, redirecionamentos, variáveis e interpretadores indiretos são recusados, mesmo com aprovação.

**Impacto na usabilidade:** para comandos sem Docker, apenas os literais `pwd`, `true` e `false`, com Bash sem login, são reconhecidos como inofensivos. Outros comandos, inclusive Maven, são recusados por essa gramática conservadora. Não se promete identificação completa de Docker dentro de shell arbitrário. A regra não foi ativada no projeto de trabalho.

Antes de solicitar a aprovação, o agente deve apresentar um bloco único como este na resposta final, junto dos efeitos esperados do comando. É um exemplo de formato, não uma ação autorizada:

````text
```arqfor-docker-review
{
  "command": "docker run --rm imagem-revisada",
  "cwd": "/caminho/absoluto/revisado",
  "shell": "/bin/bash",
  "login": false
}
```
````

A resposta humana seguinte deve ser somente `APROVADO: Use o docker.`. Comando completo (incluindo imagem, argumentos e mounts), cwd, shell e login devem corresponder exatamente à revisão. O hook lê eventos nativos `agent_message` e `user_message` do transcript associado à sessão; não aceita a frase em comentários do shell, variáveis, arquivos do projeto, resultado de ferramenta ou mensagem do próprio agente.

A autorização é restrita à mesma ação e ao turno da resposta humana. Nova mensagem humana, outra sessão/turno ou conclusão desse turno encerra sua validade. Não há arquivo de grant, segredo criado pelo agente ou chave de liberação permanente. Dentro do mesmo turno, a autorização continua válida para a mesma chamada idêntica; o hook não implementa consumo único nem idempotência. Outros hooks e permissões continuam decidindo: uma aprovação Docker não supera a recusa do hook de fast.

## Fonte de confiança e compatibilidade

A única raiz de transcripts usada pela entrada de produção é `/home/josemberg/.codex/sessions`; ela não pode ser substituída por argumentos ou variáveis de ambiente. Outro computador exige revisão explícita desse caminho. O verificador exige arquivo JSONL regular, proprietário igual ao usuário do processo, ausência de escrita por grupo/outros e ausência de symlinks nos paths verificados. Valida id da sessão, turno, origem `vscode`, `thread_source=user`, `history_mode=legacy` e versão `0.153.4`, cujo formato foi observado localmente. Falta de transcript, campos duplicados, arquivo incompleto ou acima de 16 MiB mantém o bloqueio. A entrada do hook é limitada a 64 KiB.

A [documentação oficial](https://learn.chatgpt.com/docs/hooks) informa que o transcript não é uma interface estável. Esse adapter fica restrito ao formato observado; não implica suporte a versões futuras. `permissionDecision: ask` não foi utilizado, pois não é suportado e pode resultar em continuidade do comando após falha do hook. O sucesso do verificador retorna código 0 sem sobrescrever permissões; negação retorna 2.

O modelo de confiança depende de o runtime preservar a proveniência dos eventos e de o perfil pessoal ficar fora das raízes graváveis do agente. Não é uma sandbox do sistema operacional e não protege contra outros processos com acesso ao perfil, alterações externas no Docker/PATH ou ferramentas fora do matcher. O hook também não protege `write_stdin` de processos já iniciados. Não ampliar a sandbox ou falsificar transcripts para satisfazer as verificações.

## Testes executados

```bash
python3 -B scripts/test-docker-hook.py
bash scripts/test-hook-contract.sh
bash -n .codex/hooks/bloquear-docker.sh
```

17 testes Python passaram, incluindo casos parametrizados por subtests: aprovação válida com execução somente de substituto local, falta/forja de aprovação, revisão ausente, alteração de comando/cwd/imagem/argumentos/mounts/shell, outra sessão/turno/versão/origem, revogação, chamadas Docker diretas, indireções e comandos desconhecidos, campos duplicados, transcript fora da raiz/symlink, permissões inválidas, truncamento/tamanho, tentativa de liberação por variável de ambiente, entrada inválida, verificador ausente, comandos literais isentos e recusa mantida pelo guardrail de fast.

Os testes positivos injetam uma raiz privada de fixtures diretamente na função interna de leitura, sem opção equivalente no CLI ou no payload de produção. O fluxo de teste usa um executável substituto que apenas grava argumentos em um marcador temporário; não chama Docker, daemon ou download de imagem. A sentinela permanece intacta. Isso valida o contrato e o adapter, não o despacho de ferramentas pelo Codex.

A primeira execução teve dois erros porque a umask do ambiente criava fixtures graváveis pelo grupo; foram definidas permissões 0700/0600 somente nas fixtures e a reexecução passou. A política de produção não foi afrouxada. Os 16 testes anteriores do contrato de fast também passaram. Sintaxe Bash e validação estrita OpenSpec passaram. Nenhum JUnit foi reexecutado porque não houve alteração de comportamento Java.

## Por que a task 2.5 ainda está aberta

Falta comprovar a interceptação real em ambiente descartável. Preparada cópia mínima em `/tmp/arqfor-docker-validation/workspace`, com scripts, matcher Docker `^Bash$`, matcher fast preservado inerte e sentinela sintética; sem banco, evidências reais ou credenciais. A fonte nessa cópia ainda precisa ser carregada/confiada pelo runtime, portanto o matcher no arquivo não comprova ativação. A etapa depende da confiança do projeto e da definição do hook no Codex. A autorização para alterar a configuração pessoal de confiança foi solicitada em 2.4 e rejeitada pela revisão automática por exceder o escopo de ambiente descartável; não foi concedida nesta solicitação nem contornada nesta implementação.

Também foi observado que o CLI disponível é 0.149.1, enquanto o transcript da IDE é 0.153.4. O teste de integração precisa usar o runtime compatível da IDE. Os paths nativos abaixo permitem escrita por grupo, portanto não passam na política implementada:

- `/home/josemberg/.codex/sessions` — 0775;
- `/home/josemberg/.codex/sessions/2026` — 0775;
- `/home/josemberg/.codex/sessions/2026/09` — 0775;
- `/home/josemberg/.codex/sessions/2026/09/10` — 0775;
- `/home/josemberg/.codex/sessions/2026/09/10/rollout-2026-09-10T16-43-07-01a08cd8-671a-7290-956c-01ccd301ac84.jsonl` — 0664.

Nenhum desses paths teve suas permissões alteradas. Para o teste com esse transcript, a mudança mínima seria remover somente escrita de grupo desses cinco paths (0775 → 0755 e 0664 → 0644), preservando leitura; arquivos futuros podem demandar nova revisão. Essas mudanças afetam o perfil pessoal e precisam de autorização específica, assim como a configuração de confiança. Não aplicar chmod recursivo.

Após resolver essas condições: ativar somente na cópia descartável revisada, conferir o carregamento sem erros, submeter chamada Docker sem aprovação usando substituto seguro, conferir bloqueio antes da execução e testar resposta humana vinculada à ação. Registrar separadamente a integração, preservar as demais recusas e somente então fechar a task. Nenhum commit, operação real de Docker ou avanço para a demonstração foi realizado.
