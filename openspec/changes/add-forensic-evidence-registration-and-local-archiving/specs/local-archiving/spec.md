## Purpose

Demonstrar armazenamento persistente local, arquivamento síncrono e retorno simulado de artefatos cifrados.

## ADDED Requirements

### Requirement: Persistência local

O sistema SHALL manter arquivos em storage/fast, storage/cold/work e storage/cold/archive, separados da aplicação, e metadados em SQLite. A futura demonstração local SHALL preservar ambos entre reinicializações do container.

#### Scenario: Reinício autorizado
- **WHEN** o container da demonstração é reiniciado mantendo os mounts de dados
- **THEN** registros, hashes, estados, paths e artefatos continuam disponíveis

### Requirement: Cópia segura antes de exclusão

Para EM_ANALISE com .dd elegível, o sistema SHALL persistir ARQUIVANDO, copiar de fast para work e excluir a origem somente após cópia concluída, fechada sem erro e tamanho confirmado, sem hash adicional. A implementação da exclusão SHALL depender do checkpoint humano específico.

#### Scenario: Cópia concluída
- **WHEN** cópia e fechamento terminam sem erro e tamanhos coincidem
- **THEN** a origem pode ser excluída pelo fluxo aprovado, preservando a cópia de trabalho

#### Scenario: Cópia incompleta
- **WHEN** a cópia falha, não fecha ou apresenta tamanho diferente
- **THEN** a origem permanece intacta e a cópia parcial não é tratada como sucesso

#### Scenario: Colisão de destino
- **WHEN** o destino contém arquivo não reconhecido como pertencente à operação
- **THEN** o sistema não o sobrescreve e trata a condição como falha

### Requirement: ZIP e AES-GCM

O sistema SHALL compactar a cópia em ZIP, depois cifrar com AES/GCM/NoPadding e IV aleatório novo por operação de cifra, gerando .zip.enc em archive. SHALL gerar senha de 10 caracteres alfanuméricos, usar chave AES de tamanho válido e persistir senha, chave didática, IV e parâmetros necessários. SHALL excluir ZIP aberto somente após cifra finalizada e fechada com sucesso; SHALL concluir ARQUIVADO somente com path final e metadados persistidos e ZIP aberto removido.

#### Scenario: Arquivamento completo
- **WHEN** a operação termina com sucesso
- **THEN** existe .zip.enc em archive, não existe ZIP aberto, detalhes mostram path final e senha e o estado é ARQUIVADO

#### Scenario: Falha na cifra
- **WHEN** a cifra ou seu fechamento falha
- **THEN** o ZIP aberto e a cópia de trabalho são preservados e o arquivo parcial não conta como arquivo final

#### Scenario: Nova tentativa de cifra
- **WHEN** é necessário cifrar novamente após falha
- **THEN** a nova operação usa IV aleatório novo

#### Scenario: Metadados disponíveis
- **WHEN** o arquivo cifrado foi concluído e o banco está disponível
- **THEN** senha, chave, IV e parâmetros correspondem ao artefato final; antes de apagar ZIP esses dados são persistidos

### Requirement: Uma repetição e erro

O sistema SHALL realizar no máximo duas tentativas totais de arquivamento, na mesma chamada síncrona; SHALL retomar da etapa segura preservando dados. A segunda falha SHALL produzir ERRO e mensagem exibível quando o banco estiver disponível.

#### Scenario: Falha inicial recuperável
- **WHEN** a primeira tentativa falha e a segunda conclui
- **THEN** o resultado é ARQUIVADO sem terceira tentativa

#### Scenario: Falha depois de excluir fast
- **WHEN** a origem já foi removida e a etapa posterior falha
- **THEN** a segunda tentativa usa a cópia preservada em work, sem exigir a origem apagada

#### Scenario: Segunda falha
- **WHEN** ambas as tentativas falham com banco disponível
- **THEN** o resultado é ERRO com mensagem, preservando última cópia utilizável

#### Scenario: Banco indisponível
- **WHEN** não é possível persistir ERRO
- **THEN** a resposta informa a falha de persistência, preserva artefatos e não declara sucesso ou estado persistido que não foi gravado

### Requirement: Desarquivamento somente simulado

O sistema SHALL fazer ARQUIVADO → DESARQUIVANDO → EM_ANALISE movendo apenas o .zip.enc para fast e atualizando path. Não SHALL descriptografar, descompactar, recalcular hash ou alterar extensão para .dd. Conforme plano aprovado, SHALL recusar re-arquivamento desse .zip.enc e orientar novo cadastro de .dd.

#### Scenario: Retorno simulado
- **WHEN** o movimento de archive para fast é bem-sucedido
- **THEN** o .zip.enc está em fast, o path foi atualizado, o estado é EM_ANALISE e os metadados criptográficos permanecem

#### Scenario: Retorno falha
- **WHEN** a movimentação falha e o banco está disponível
- **THEN** o estado é ERRO e a mensagem explica a falha, sem simular restauração

#### Scenario: Tentar arquivar cifrado
- **WHEN** o usuário pede Arquivar para o .zip.enc retornado
- **THEN** a ação é recusada sem mudar EM_ANALISE e orienta novo cadastro de arquivo .dd
