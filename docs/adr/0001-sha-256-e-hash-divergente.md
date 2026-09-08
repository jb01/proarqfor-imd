# ADR-0001 — SHA-256 e tratamento de hash divergente

## Status

Accepted

Aprovado pelo usuário nesta conversa em 2026-09-08: `APROVADO: ADR-0001`.

## Data

Proposta: 2026-09-07. Aprovação: 2026-09-08.

## Decisores

Responsável humano pelo projeto acadêmico (nome a confirmar). Codex auxilia a redação, sem poder de aprovação.

## Contexto e problema

O sistema precisa verificar a integridade do arquivo `.dd` no cadastro antes de permitir sua entrada no arquivamento.

## Critérios de decisão

Integridade facilmente demonstrável; algoritmo disponível na biblioteca padrão Java; algoritmo único no MVP; divergência visível ao usuário.

## Opções consideradas

1. SHA-256 como único algoritmo.
2. Aceitar SHA-256, MD5 e SHA-1.
3. Não calcular hash e confiar apenas no valor informado.

## Decisão

Usar apenas SHA-256, calculado por streaming no cadastro e comparado ao informado. Se coincidem, EM_ANALISE. Se divergem, permitir cadastro, salvar e exibir ambos, definir HASH_DIVERGENTE, bloquear arquivamento e qualquer mudança de status, orientar remoção e novo cadastro.

## Justificativa

SHA-256 oferece verificação clara e simples para demonstrar. Outros algoritmos ampliam escopo sem necessidade. Persistir divergência torna o problema visível sem permitir arquivar evidência não validada.

## Consequências positivas

Comparação reproduzível; uso de memória limitado pelo streaming; divergência visível e bloqueada.

## Consequências negativas, riscos e limitações

Não haverá recálculo após cópia, compactação, criptografia ou desarquivamento. Cadeia de custódia fica para evolução futura. A comparação confirma igualdade com o valor fornecido, não autenticidade da origem.

## Como a decisão será confirmada no MVP

Demonstrar hash correto gerando EM_ANALISE e incorreto gerando HASH_DIVERGENTE; verificar ambos os hashes, bloqueio no serviço e na tela e orientação de novo cadastro. Testar arquivo ausente/ilegível sem cadastro parcial.

## Relação com a OpenSpec, quando aplicável

Change add-forensic-evidence-registration-and-local-archiving; specs evidence-registration e evidence-lifecycle; design e tarefas de cadastro.
