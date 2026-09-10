## Context

Ver proposal.md para motivação e docs/architecture.md para componentes e fluxo. Plano aprovado em 2026-09-08; ADR-0001, ADR-0002 e ADR-0003 estão Accepted. Cadastro, ZIP/cifra e orquestração foram implementados por recortes. O usuário aprovou explicitamente exclusão-fast-storage após revisão do fluxo; evidências e resultados estão em tasks.md. Checkpoints de merge e demonstração permanecem pendentes.

## Goals / Non-Goals

**Goals:** separar telas MVC, regras de negócio e persistência; executar fluxo síncrono local com retomada limitada e preservação da última cópia utilizável.

**Non-Goals:** transação distribuída, recuperação após queda de processo, restauração real ou infraestrutura além da stack aprovada. Exclusões completas em proposal.md.

## Decisions

### Cadastro e interface

Um registro referencia um único arquivo regular `.dd` legível dentro de storage/fast. Rejeitar symlinks e caminhos que escapem da raiz após normalização/resolução, evitando caminhos arbitrários. Identificador é texto único (exemplo xxxxx/2026), nunca nome de diretório; usar identificador interno para artefatos e evitar colisões. Validar SHA-256 com 64 dígitos hexadecimais, comparar sem distinção de caixa. Duplicidade, arquivo ausente/ilegível ou hash malformado retornam erro de formulário, sem registro incompleto. Data de cadastro é gerada pelo sistema.

Listagem tem Adicionar e registros clicáveis. Cadastro recebe identificador, path, hash informado e campo status com EM_ANALISE como intenção inicial; o resultado da comparação sempre determina o estado persistido. Cancelar volta à listagem sem gravar; Cadastrar volta após sucesso, inclusive divergência. Detalhes mostram identificador, path atual, hash original/informado, hash conferido/calculado, data, senha (ainda não gerada antes do arquivamento), status e erro, se houver.

Decisão aprovada no plano: edição de status ocorre pelas ações Arquivar e Desarquivar e pelas transições operacionais correspondentes, sem seletor livre ou avanço manual para ARQUIVADO. Remover pede “Tem certeza que deseja deletar a evidência xxxxx/2026?” e remove só o registro, inclusive HASH_DIVERGENTE/ERRO; não apaga arquivo físico. Cancelamento preserva tudo. Impedir remoção durante operações em curso. Essas interpretações integram o plano aprovado em 2026-09-08.

### Estados

| Origem | Destino | Gatilho |
|---|---|---|
| EM_ANALISE | ARQUIVANDO | Ação Arquivar elegível |
| ARQUIVANDO | ARQUIVADO | Fluxo concluído |
| ARQUIVADO | DESARQUIVANDO | Ação Desarquivar |
| DESARQUIVANDO | EM_ANALISE | Movimento simulado concluído |
| EM_ANALISE, ARQUIVANDO, ARQUIVADO, DESARQUIVANDO | ERRO | Falha operacional definitiva |

HASH_DIVERGENTE e ERRO não permitem nenhuma saída. Primeira falha de arquivamento mantém ARQUIVANDO durante a segunda tentativa; não cria transição extra para EM_ANALISE. O serviço rejeita transições indevidas mesmo se forjado o formulário. Mudança de estado e ação de arquivo não são operações independentes para o usuário.

### Persistência e criptografia

Aplicar ADR-0001/0002/0003, aprovados em 2026-09-08, na futura implementação. Modelo guarda identificador interno, identificador único informado, path atual, dois hashes, data, status, erro, senha, chave AES, IV, salt, iterações e versão do formato. Campos criptográficos são ausentes antes de gerados. A senha de 10 caracteres precisa de derivação para tamanho AES válido; decisão aprovada no ADR-0003. Não configurar ainda driver/dialeto: apresentar coordenadas e compatibilidade para aprovação humana futura, mantendo SQLite/JPA.

### Arquivamento e falhas

Implementado na camada de serviço por ArchivingService/StorageCopyService e adaptação de ZipService/CryptoService. O [documento de revisão](../../../docs/archiving-orchestration-review.md) registra o fluxo aprovado, critérios de exclusão, matriz de testes e proibição de alterações fora do recorte. A atualização condicional do estado inicial e o bloqueio por id impedem início duplicado; o contexto de etapas fica na chamada síncrona, sem novos estados persistidos. Os nomes de tentativa preservam parciais e não sobrescrevem artefatos desconhecidos. Em recorte posterior, POST /evidences/{id}/archive passou a chamar esse serviço pela interface, sem alterar a orquestração. Detalhes exibem senha persistida com escape HTML, estado atualizado e mensagens após redirecionamento. Não há repetição no controller ou edição manual de status; falha de consulta SQLite recebe HTTP 503 e registro inexistente recebe HTTP 404.

Seguir as seis etapas de docs/architecture.md. Sucesso da cópia exige leitura/escrita concluídas, fechamento sem erro e mesmo tamanho; nenhum novo hash. Persistir localização de trabalho após cópia confirmada e antes de excluir fast. Se excluir fast falhar, não fingir sucesso: retentar com destino já confirmado. Um marcador de etapa da operação síncrona distingue parcial de completo; não confiar apenas em existência de arquivo. Não exigir reinício integral depois de cada falha.

No máximo duas tentativas totais; a segunda só ocorre se a primeira falhar. Cópia parcial nunca autoriza exclusão; segunda tentativa usa origem preservada. Depois da remoção de fast, segunda tentativa usa .dd de work. Falha no ZIP preserva .dd. Falha na cifra preserva ZIP; nova cifra usa novo IV e destino temporário separado. .zip.enc só é publicado após tag GCM finalizada e fechamento. Se publicação já ocorreu e falha a persistência ou limpeza do ZIP, repetir só a etapa pendente, sem cifrar de novo artefato concluído. Persistir metadados antes de remover ZIP; ARQUIVADO somente quando também removido o ZIP. Não sobrescrever destinos desconhecidos. Manter .dd de work, sem limpeza adicional automática.

Falha final persiste ERRO e mensagem legível, sem incluir segredos. Se SQLite estiver indisponível, não é possível garantir gravar ERRO: mostrar erro na resposta e preservar artefatos; informar a limitação, sem declarar operação concluída. Sem mecanismo automático pós-crash; interrupções podem deixar estado intermediário e exigem avaliação humana fora do fluxo automático do MVP.

### Desarquivamento simulado

Implementado por UnarchivingService.unarchive(Long), somente na camada de serviço. Uma atualização condicional de estado/path e um bloqueio por id impedem chamadas duplicadas. A origem deve ser arquivo regular/legível .zip.enc em archive/<id-interno>, sem symlinks; destino fast/<id-interno> não permite sobrescrita. A transação inicial é confirmada antes do movimento; a final atualiza path e estado. archivedPath é mantido como referência histórica. Após movimento concluído e commit final falho, a tentativa de registrar ERRO também atualiza currentPath para fast. Falha nessa gravação informa estado não confirmado; não há rollback de arquivos, remoção de parciais ou recuperação automática. Integração web permanece pendente neste recorte.

Mover .zip.enc de archive para fast e atualizar path, mantendo senha, IV e demais metadados. Persistir DESARQUIVANDO antes e EM_ANALISE após sucesso. Falha leva a ERRO, sem repetição obrigatória (retry é exclusivo do arquivamento). Não renomear para .dd, descriptografar, descompactar ou recalcular hash. Decisão aprovada: novo arquivamento requer .dd original; rejeitar .zip.enc devolvido e orientar remover registro e cadastrar novamente um .dd, sem criar estado extra.

### Guardrail

PreToolUse deve bloquear shell antes da execução. Estrutura inicial conservadora bloqueia todo shell quando habilitada; isso cobre exclusão/mutação em fast, inclusive via variáveis, cwd, symlinks e interpretadores, sem tentar analisar toda a linguagem shell. Custo: bloqueia também leitura e comandos inofensivos. Matcher inicialmente inerte impede execução nesta etapa. Não confundir hook com controle da aplicação ou com autorização para excluir. README descreve ativação, teste isolado futuro e desativação humana.

## Risks / Trade-offs

- Cópia sem hash posterior pode passar com alteração concorrente de mesmo tamanho → usar somente arquivos sintéticos estáveis na demonstração; limitação aceita.
- Dados/banco sem atomicidade → preservar artefatos, retomar da etapa segura na requisição e informar falha definitiva.
- Chave no banco e .dd em work → confidencialidade limitada; documentar, sem adicionar infraestrutura de produção.
- Senha de 10 caracteres não é chave AES → derivação aprovada no ADR-0003.
- Desarquivamento retorna cifrado mas estado é EM_ANALISE → indicação explícita na tela e bloqueio de re-arquivamento de .zip.enc aprovado.
- Hook não é isolamento de filesystem → checkpoints e sandbox permanecem necessários; nunca contornar bloqueios.

## Migration Plan

Não há migração, deploy ou rollback a executar agora. Após aprovações: implementar incrementalmente, testar com temporários e só preparar demonstração Compose ao final. Execução de Docker e alterações destrutivas exigem autorização específica. Sem dados reais para migração; preservar dados de demonstração entre reinicializações futuras.

## Resultado da revisão

Plano e ADRs aprovados pelo usuário em 2026-09-08, incluindo exclusão somente do registro, status controlado pelas ações, path sob fast, derivação de senha, retenção do .dd de trabalho e bloqueio de re-arquivamento do .zip.enc desarquivado. Não há requisito de restauração real implícito nessas decisões. O checkpoint exclusão-fast-storage foi posteriormente aprovado para o fluxo revisado; a orquestração e os testes sintéticos foram concluídos. Merge e checkpoints restantes continuam pendentes, conforme tasks.md.
