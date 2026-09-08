## Purpose

Definir a navegação e o cadastro de evidências locais com integridade inicial visível ao usuário.

## ADDED Requirements

### Requirement: Listagem e detalhes

O sistema SHALL listar evidências, oferecer Adicionar e abrir detalhes ao clicar no registro. Detalhes SHALL exibir identificador, path atual, hashes informado e calculado, data, senha da criptografia, status e mensagem de erro existente.

#### Scenario: Abrir evidência
- **WHEN** o usuário clica numa evidência listada
- **THEN** os detalhes mostram os campos persistidos; antes de arquivar, a senha aparece como ainda não gerada

#### Scenario: Lista vazia
- **WHEN** não há evidências cadastradas
- **THEN** a tela informa lista vazia e permite Adicionar

### Requirement: Cadastro e cancelamento

O sistema SHALL receber identificador único, path atual, SHA-256 informado e status inicial pretendido EM_ANALISE; SHALL aceitar apenas um .dd regular e legível sob storage/fast, sem escape de raiz ou symlink. O estado persistido SHALL ser determinado pela verificação, nunca imposto pelo formulário.

#### Scenario: Cancelar
- **WHEN** o usuário pressiona Cancelar
- **THEN** retorna à listagem sem persistir registro

#### Scenario: Cadastrar
- **WHEN** o usuário envia dados válidos e identificador ainda não usado
- **THEN** o cadastro registra data do sistema, ambos os hashes e estado calculado, retornando à listagem

#### Scenario: Entrada inválida
- **WHEN** o identificador é duplicado, hash não tem 64 dígitos hexadecimais, arquivo não é .dd regular legível ou path escapa de fast
- **THEN** o formulário exibe erro e nenhum novo registro é persistido

#### Scenario: Estado forjado
- **WHEN** o formulário tenta impor ARQUIVADO ou outro estado inicial não permitido
- **THEN** o sistema rejeita a alteração e não ignora a verificação de integridade

### Requirement: SHA-256 inicial

O sistema SHALL calcular somente SHA-256 por streaming no cadastro, comparar com o valor informado sem distinção de caixa hexadecimal e armazenar ambos. Não SHALL recalcular hash em cópia, ZIP, cifra ou desarquivamento.

#### Scenario: Hash igual
- **WHEN** os hashes informado e calculado coincidem
- **THEN** o registro é cadastrado como EM_ANALISE

#### Scenario: Hash divergente
- **WHEN** os hashes diferem
- **THEN** o cadastro é permitido com HASH_DIVERGENTE; detalhes exibem ambos e orientam remover e cadastrar novamente

#### Scenario: Leitura interrompida
- **WHEN** a leitura falha antes de terminar o cálculo
- **THEN** a tela informa erro e não persiste cadastro incompleto

### Requirement: Remoção confirmada do registro

O sistema SHALL pedir confirmação com o identificador, por exemplo “Tem certeza que deseja deletar a evidência xxxxx/2026?”. Conforme plano aprovado, SHALL remover somente o registro, preservando arquivos físicos; SHALL permitir remover HASH_DIVERGENTE e ERRO e impedir remoção durante operação em curso.

#### Scenario: Confirmar remoção
- **WHEN** o usuário confirma remover registro sem operação em curso
- **THEN** o registro sai da listagem e os arquivos permanecem

#### Scenario: Desistir
- **WHEN** o usuário cancela a confirmação
- **THEN** registro e arquivos permanecem

#### Scenario: Remover bloqueado por operação
- **WHEN** há arquivamento ou desarquivamento em curso
- **THEN** a remoção é recusada sem interferir no fluxo
