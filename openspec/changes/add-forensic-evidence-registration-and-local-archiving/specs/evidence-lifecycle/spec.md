## Purpose

Restringir o ciclo da evidência a estados e transições explícitos, impedindo liberação de registros bloqueados.

## ADDED Requirements

### Requirement: Estados exclusivos

O sistema SHALL usar somente EM_ANALISE, ARQUIVANDO, ARQUIVADO, DESARQUIVANDO, HASH_DIVERGENTE e ERRO.

#### Scenario: Estado desconhecido
- **WHEN** é solicitada persistência de um estado fora do conjunto
- **THEN** a solicitação é rejeitada

### Requirement: Transições operacionais

O sistema SHALL permitir somente EM_ANALISE → ARQUIVANDO, ARQUIVANDO → ARQUIVADO, ARQUIVADO → DESARQUIVANDO, DESARQUIVANDO → EM_ANALISE e cada um desses quatro estados operacionais → ERRO. A mudança pelo usuário SHALL ocorrer pelas ações permitidas; etapas intermediárias e conclusões pertencem ao fluxo síncrono.

#### Scenario: Arquivar
- **WHEN** o usuário solicita Arquivar para EM_ANALISE elegível
- **THEN** o fluxo passa por ARQUIVANDO e só conclui ARQUIVADO após sucesso

#### Scenario: Desarquivar
- **WHEN** o usuário solicita Desarquivar para ARQUIVADO
- **THEN** o fluxo passa por DESARQUIVANDO e só conclui EM_ANALISE após movimento bem-sucedido

#### Scenario: Salto manual
- **WHEN** o usuário tenta mudar EM_ANALISE diretamente para ARQUIVADO, ou pede outra transição não listada
- **THEN** o serviço rejeita a mudança sem movimentar arquivos

#### Scenario: Falha definitiva
- **WHEN** uma operação falha definitivamente em qualquer dos quatro estados operacionais e o banco está disponível
- **THEN** o estado passa a ERRO e uma mensagem é persistida

### Requirement: Estados bloqueados

O sistema SHALL bloquear arquivamento, desarquivamento e qualquer mudança de status para HASH_DIVERGENTE e ERRO, na interface e no processamento. Remoção do registro não é transição de estado.

#### Scenario: Divergência bloqueada
- **WHEN** uma ação ou formulário tenta mudar HASH_DIVERGENTE
- **THEN** a ação é rejeitada e há orientação de remoção e novo cadastro

#### Scenario: Erro bloqueado
- **WHEN** uma ação ou formulário tenta mudar ERRO
- **THEN** a ação é rejeitada, o erro continua visível e não há recuperação de status no MVP
