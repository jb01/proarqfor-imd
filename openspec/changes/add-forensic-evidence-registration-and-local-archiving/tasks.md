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

- [ ] 3.1 Implementar cadastro de .dd, validação de identificador/path/hash e SHA-256 por streaming; verificar JUnit 5 para hash igual/divergente, caixa hexadecimal, duplicidade, symlink/escape e leitura falha.
- [ ] 3.2 Implementar estados e transições permitidas no serviço; verificar todos os pares permitidos e proibidos, incluindo bloqueio total de HASH_DIVERGENTE/ERRO.
- [ ] 3.3 Implementar listagem, Adicionar, cadastro/Cancelar/Cadastrar e detalhes com todos os campos; verificar navegação MVC e erros de formulário, usando apenas ferramentas já aprovadas.
- [ ] 3.4 Implementar remoção somente de registro com confirmação e cancelamento; verificar registro removido, arquivo preservado e bloqueio durante operação em curso.

## 4. Checkpoint obrigatório antes de código de exclusão em fast

- [ ] 4.1 Apresentar fluxo de cópia, fechamento, comparação de tamanho, persistência e exclusão, com matriz de falhas e testes em temporários; verificar material revisável e ausência de hash posterior.
- [ ] 4.2 Parar e obter `APROVADO: exclusão-fast-storage`; verificar mensagem explícita antes de implementar qualquer exclusão em fast. Autorização de execução destrutiva permanece separada.

## 5. Arquivamento síncrono após checkpoint

- [ ] 5.1 Implementar cópia fast → work e exclusão condicionada ao sucesso aprovado; verificar cópia incompleta, fechamento falho, tamanho divergente, colisão e falha ao excluir, preservando a última cópia utilizável.
- [ ] 5.2 Implementar ZIP e cifra conforme ADR-0003 aprovado; verificar senha de 10 alfanuméricos, chave válida, parâmetros persistidos, IV novo e preservação do ZIP na falha de finalização.
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

Em 2026-09-07, o usuário autorizou registrar as interpretações consultadas como propostas para revisão. Em 2026-09-08, o usuário aprovou explicitamente o plano e os três ADRs nesta conversa:

```text
APROVADO: plano
APROVADO: ADR-0001
APROVADO: ADR-0002
APROVADO: ADR-0003
```

As tarefas 1.1–1.5 estão concluídas pela aprovação do plano apresentado e dos ADRs, agora Accepted. As tarefas 2.1–7.5 permanecem pendentes. Nenhuma implementação ou teste da aplicação foi realizado. Aprovação de dependências específicas, exclusão-fast-storage, execução do hook/Docker, ações destrutivas e merge não foi concedida por essas mensagens.
