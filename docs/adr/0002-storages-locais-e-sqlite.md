# ADR-0002 — Diretórios locais e SQLite para simular armazenamentos persistentes

## Status

Accepted

Aprovado pelo usuário nesta conversa em 2026-09-08: `APROVADO: ADR-0002`.

## Data

Proposta: 2026-09-07. Aprovação: 2026-09-08.

## Decisores

Responsável humano pelo projeto acadêmico (nome a confirmar). Codex auxilia a redação, sem poder de aprovação.

## Contexto e problema

Demonstrar camadas rápida, lenta e arquivo final sem servidores físicos, SAN, NAS, nuvem ou banco externo.

## Critérios de decisão

Execução no computador local; configuração simples e rápida; sem custo de nuvem; fácil demonstração no Docker Desktop; persistência entre reinicializações do container.

## Opções consideradas

1. Diretórios locais + SQLite.
2. PostgreSQL + volumes Docker.
3. Serviços de nuvem como S3.
4. Movimentação apenas por status no banco, sem arquivos reais.

## Decisão

Usar storage/fast, storage/cold/work e storage/cold/archive como storages simulados. SQLite guarda dados da evidência, hashes, status, paths, IV, senha da criptografia e chave AES didática. Propor data/arqfor.db e persistência dos diretórios no host para Compose futuro.

## Justificativa

É a opção mais simples de configurar, demonstra movimentação real e dispensa serviços externos, atendendo prazo e apresentação local.

## Consequências positivas

Baixo custo; dados persistentes separados da aplicação; movimentação observável em diretórios locais.

## Consequências negativas, riscos e limitações

Não representa desempenho real de NVMe/storage lento, redundância, backup, disponibilidade, permissões reais, retenção judicial ou escalabilidade. SQLite não representa banco corporativo concorrente. Filesystem e banco não têm atomicidade conjunta. Driver SQLite e dialeto compatível com JPA exigem proposta e aprovação das dependências antes de configurar. Chave e senha no banco são limitação do MVP.

## Como a decisão será confirmada no MVP

Demonstrar saída de fast, passagem por work e .zip.enc em archive com metadados persistidos. Na etapa final autorizada, reiniciar container e demonstrar retenção do banco e arquivos em mounts do host.

## Relação com a OpenSpec, quando aplicável

Change add-forensic-evidence-registration-and-local-archiving; spec local-archiving; docs/architecture.md e tarefas de persistência/demonstração.
