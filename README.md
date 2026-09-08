# arqfor_v1.0

Sistema de Gestão de Armazenamento de Evidências Forenses Digitais — MVP acadêmico local.

**Etapa atual: plano aprovado em 2026-09-08. Não há aplicação implementada.** Nenhum projeto Maven, dependência, Docker Compose ou deploy foi criado. Os três ADRs estão Accepted; o checkpoint inicial foi concluído. Os checkpoints posteriores continuam pendentes.

## Stack e objetivo

Java 21 LTS, Spring Boot 3.x, Maven, Thymeleaf, Spring Data JPA, SQLite e JUnit 5. Git/GitHub, Codex e OpenSpec apoiam o desenvolvimento. Docker Compose será preparado apenas para a demonstração local final; sua execução exige aprovação. Tecnologias ou dependências relevantes adicionais exigem proposta e aprovação humana.

Cada evidência representa um único arquivo `.dd`. O MVP verificará SHA-256 no cadastro, exibirá registros/detalhes e arquivará sincronamente em diretórios locais:

| Caminho planejado | Papel |
|---|---|
| storage/fast | Arquivo inicial; retorno do artefato cifrado na simulação |
| storage/cold/work | Cópia de trabalho .dd e ZIP intermediário |
| storage/cold/archive | Arquivo final .zip.enc |
| data/arqfor.db | Metadados SQLite, separados da aplicação |

Esses diretórios e arquivos não foram criados nesta etapa e não devem ser versionados.

## Comportamento planejado

Listagem com Adicionar e registros clicáveis. Cadastro recebe identificador único, path, hash SHA-256 e intenção de status inicial; Cancelar volta à lista sem gravar e Cadastrar grava e volta. Detalhes mostram identificador, path atual, hashes original/informado e conferido/calculado, data, senha, status e erro, se houver. Senha é gerada no arquivamento; antes disso, indicar ausência.

Hash igual resulta em EM_ANALISE. Divergência permite cadastro em HASH_DIVERGENTE, mostra ambos os hashes, bloqueia arquivamento e qualquer mudança de status e orienta remoção e novo cadastro. ERRO também fica bloqueado. A decisão de remoção aprovada apaga somente o registro após confirmação com identificador, preservando arquivos físicos.

Transições exclusivas: EM_ANALISE → ARQUIVANDO → ARQUIVADO → DESARQUIVANDO → EM_ANALISE. Qualquer desses quatro estados operacionais pode ir para ERRO; HASH_DIVERGENTE e ERRO não têm saída. O usuário solicita mudanças por ações válidas, sem edição arbitrária de status.

Arquivar copia fast → work, confirma término/fechamento/tamanho, exclui origem somente após sucesso e checkpoint, compacta ZIP, cifra com AES/GCM/NoPadding, publica .zip.enc em archive, persiste metadados, remove ZIP aberto somente após cifra bem-sucedida e conclui ARQUIVADO. Uma primeira falha gera mais uma tentativa da etapa segura; segunda falha resulta em ERRO e mensagem quando o banco estiver disponível. A cópia .dd em work é preservada na proposta para não introduzir limpeza destrutiva adicional.

Desarquivar é somente mover .zip.enc de archive para fast, sem descriptografar, descompactar, renomear como .dd ou recalcular hash. Retorna EM_ANALISE, mas não restaura a evidência original. O plano aprovado determina impedir re-arquivamento desse .zip.enc e orientar novo cadastro de .dd.

## Documentação e planejamento

- [Governança do agente](AGENTS.md).
- [Arquitetura e diagrama Mermaid](docs/architecture.md): Browser, Spring Boot/Thymeleaf, Controllers, Services, SQLite e três storages; separação entre aplicação e dados.
- [Template MADR simplificado](docs/adr/template.md).
- [ADR-0001 — SHA-256 e divergência](docs/adr/0001-sha-256-e-hash-divergente.md), Accepted.
- [ADR-0002 — Storages locais e SQLite](docs/adr/0002-storages-locais-e-sqlite.md), Accepted.
- [ADR-0003 — ZIP e AES-GCM](docs/adr/0003-zip-e-aes-gcm.md), Accepted.
- [Proposta OpenSpec](openspec/changes/add-forensic-evidence-registration-and-local-archiving/proposal.md), [design](openspec/changes/add-forensic-evidence-registration-and-local-archiving/design.md) e [tarefas](openspec/changes/add-forensic-evidence-registration-and-local-archiving/tasks.md).
- Delta specs: [cadastro](openspec/changes/add-forensic-evidence-registration-and-local-archiving/specs/evidence-registration/spec.md), [estados](openspec/changes/add-forensic-evidence-registration-and-local-archiving/specs/evidence-lifecycle/spec.md), [arquivamento](openspec/changes/add-forensic-evidence-registration-and-local-archiving/specs/local-archiving/spec.md) e [governança](openspec/changes/add-forensic-evidence-registration-and-local-archiving/specs/agent-governance/spec.md).

Para validar somente os artefatos com o OpenSpec já instalado:

```bash
OPENSPEC_TELEMETRY=0 openspec validate add-forensic-evidence-registration-and-local-archiving --strict
OPENSPEC_TELEMETRY=0 openspec status --change add-forensic-evidence-registration-and-local-archiving
```

Artefatos completos no OpenSpec não significam implementação concluída ou aprovada. Não existem instruções de execução da aplicação nesta etapa.

## Checkpoints

Checkpoint inicial concluído: mensagens recebidas do usuário em 2026-09-08, registradas em tasks.md:

```text
APROVADO: plano
APROVADO: ADR-0001
APROVADO: ADR-0002
APROVADO: ADR-0003
```

Antes de implementar código que exclua arquivo em fast, apresentar fluxo, condição de sucesso da cópia, falhas e testes, e aguardar `APROVADO: exclusão-fast-storage`. Essa aprovação não desabilita o hook nem autoriza automaticamente comandos destrutivos.

Antes de merge ou entrega da implementação, apresentar testes realmente executados, mudanças, limitações, riscos e checklist e aguardar `APROVADO: merge`. Commit, push, merge, deploy, Docker e ações destrutivas exigem aprovação humana específica. Nenhuma dessas ações é realizada nesta etapa.

## Guardrail técnico — estrutura não executada

Arquivos: [.codex/hooks.json](.codex/hooks.json) e [bloquear-exclusao-fast.sh](.codex/hooks/bloquear-exclusao-fast.sh). O matcher inicial `^ARQFOR_HOOK_DESABILITADO$` não corresponde a ferramentas shell: a estrutura fica inerte até ativação humana. Não usar esse nome como ferramenta. O script não interpreta nem executa o comando recebido; devolve recusa via código 2.

A política inicial, deliberadamente conservadora, **bloqueia todo shell quando ativada**, incluindo leituras. Assim não depende de reconhecer `rm`: abrange comandos indiretos, redirecionamentos, `mv`, interpretadores e caminhos via variáveis ou symlinks. Uma política seletiva de liberação exigiria nova revisão e testes; não se presume segura uma regex de comandos destrutivos.

### Habilitar futuramente

Somente após revisão humana, em ambiente descartável sem evidências reais:

1. Conferir versão e suporte a hooks na instalação do Codex. A versão observada durante o planejamento foi 0.149.1; a integração não foi testada.
2. No editor, substituir somente o matcher inerte de hooks.json por `^Bash$`. Ajustar o caminho absoluto do script no campo command se o repositório tiver sido movido. O uso de `/bin/bash` dispensa tornar o script executável.
3. Garantir `[features] hooks = true` no config.toml da configuração local apropriada; não há config.toml criado por esta etapa. Reiniciar a sessão e revisar/confiar na fonte do hook conforme a interface instalada, sem ignorar a verificação de confiança.
4. Encerrar sessões shell anteriores: envio a processos já iniciados não passa necessariamente por nova interceptação. Confirmar a integração em diretório descartável antes de confiar na proteção.

Segundo a [documentação oficial de hooks do Codex](https://learn.chatgpt.com/docs/hooks), `PreToolUse` cobre shell com alias `Bash`, inclusive `exec_command`; saída 2 bloqueia. Hooks não são uma barreira completa de isolamento, e `write_stdin` não reavalia comandos de uma sessão existente.

### Testar futuramente — não executar agora

Primeiro, após autorização, validar o contrato isoladamente num terminal humano. Este comando envia apenas texto JSON ao hook; **não executa o rm contido no texto**:

```bash
printf '%s\n' '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm storage/fast/ficticio.dd"}}' | /bin/bash .codex/hooks/bloquear-exclusao-fast.sh
```

Esperado: código 2 e mensagem de bloqueio no stderr. Repetir com JSON representando `mv`, sobrescrita por redirecionamento, exclusão do diretório pai, caminho absoluto, `cd storage/fast`, variável, symlink, interpretador e comando inofensivo: todos devem ser negados. Entrada vazia ou inválida também é negada; o script não precisa analisar JSON.

Depois, testar a integração em **cópia descartável do repositório**, com arquivos fictícios, caminho do hook ajustado e sem acesso a evidências reais. Confirmar que um comando inofensivo é negado antes de qualquer teste destrutivo; então solicitar mutações apenas sobre um arquivo sentinela descartável e verificar que permanece intacto. Autorizar esses testes separadamente: se a integração estiver incorreta, o comando pode executar. Nunca testar bloqueio com dados reais.

### Desabilitar futuramente

Com aprovação humana, restaurar no editor o matcher `^ARQFOR_HOOK_DESABILITADO$` e reiniciar a sessão. Isso desativa apenas esta regra e preserva outros hooks. Desativação não autoriza exclusão de fast; o checkpoint continua obrigatório. Não usar o agente para contornar o próprio bloqueio.

O hook cobre chamadas shell interceptadas pelo Codex; não protege Java em execução, terminais externos ou ferramentas fora da cobertura. A implementação deve ter suas próprias condições de cópia segura. O hook não foi habilitado nem executado e sua eficácia em integração ainda precisa ser confirmada.

## Limitações e decisões aprovadas

Sem login, autenticação, autorização, perfis, REST, microserviços, nuvem, limitação de throughput, filas, jobs assíncronos, MCP, integração externa, cadeia de custódia ou auditoria. Sem hash posterior ao cadastro e sem restauração real. Diretórios não simulam desempenho físico, redundância, backup, disponibilidade, retenção judicial ou escalabilidade; SQLite não representa banco corporativo concorrente.

A senha alfanumérica de 10 caracteres não tem tamanho válido como chave AES. ADR-0003, aprovado em 2026-09-08, determina derivar uma chave de 256 bits com PBKDF2 disponível no Java e persistir parâmetros. Senha e chave no SQLite, senha exibida sem login e cópia .dd aberta em work são limitações acadêmicas, não práticas recomendadas para produção. GCM gera tag, mas o MVP não verificará essa tag por meio de restauração. ZIP pode não reduzir o tamanho de todo arquivo.

O usuário aprovou o plano e os três ADRs em 2026-09-08, incluindo remoção somente do registro, status por ações, derivação de senha, bloqueio de re-arquivamento do .zip.enc, restrição de path sob fast e retenção do .dd de trabalho. Dependências mínimas SQLite/JPA ainda exigem proposta nominal e aprovação antes de configuração. Banco indisponível impede garantir gravação de ERRO; queda de processo não tem recuperação automática neste MVP.
