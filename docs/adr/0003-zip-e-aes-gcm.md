# ADR-0003 — ZIP seguido de AES-GCM para arquivamento local

## Status

Accepted

Aprovado pelo usuário nesta conversa em 2026-09-08: `APROVADO: ADR-0003`.

## Data

Proposta: 2026-09-07. Aprovação: 2026-09-08.

## Decisores

Responsável humano pelo projeto acadêmico (nome a confirmar). Codex auxilia a redação, sem poder de aprovação.

## Contexto e problema

Após mover a evidência ao storage lento, reduzir o tamanho do artefato e protegê-lo contra leitura ou alteração não autorizada dentro do MVP local.

## Critérios de decisão

Viável em Java padrão; demonstração curta e funcional; confidencialidade e detecção de alteração do arquivo final; arquivo final único; sem KMS, nuvem, rotação ou gestão corporativa de segredos.

## Opções consideradas

1. Manter .dd sem compactação ou criptografia.
2. Compactar somente em ZIP.
3. Criptografar .dd diretamente.
4. Compactar ZIP e depois cifrar com AES-GCM.
5. ZIP com senha como única proteção.

## Decisão

Copiar para storage/cold/work, compactar em ZIP e cifrar com AES/GCM/NoPadding, gerando .zip.enc em storage/cold/archive. Gerar senha de 10 caracteres alfanuméricos com SecureRandom, persistir e mostrar nos detalhes. Usar IV aleatório novo de 12 bytes em cada tentativa de criptografia e tag de 128 bits. Excluir ZIP aberto somente após finalização e fechamento bem-sucedidos do cifrado.

Decisão técnica aprovada em 2026-09-08: 10 caracteres não são chave AES válida (AES exige 16, 24 ou 32 bytes). Derivar chave AES de 256 bits usando PBKDF2WithHmacSHA256, salt aleatório de 16 bytes e 600000 iterações, via Java padrão; persistir senha, chave codificada em Base64, salt, iterações, IV e versão do formato. Não usar diretamente os 10 bytes como chave nem alegar que a derivação aumenta a entropia da senha. Esses parâmetros são uma escolha didática aprovada, não recomendação de produção.

## Justificativa

Compactar antes permite compressão; dados cifrados normalmente não comprimem de forma útil. AES-GCM combina confidencialidade com autenticação/integridade. ZIP com senha não é a proteção principal. Derivação resolve a incompatibilidade do comprimento da senha sem biblioteca adicional.

## Consequências positivas

Artefato final único cifrado; bibliotecas padrão Java; tag GCM disponível para futura verificação, sem implementar restauração nesta versão.

## Consequências negativas, riscos e limitações

Chave AES, senha e IV no SQLite viabilizam apenas o MVP: não é adequado para produção, onde seriam necessários KMS, separação de chaves, controle de acesso, rotação e auditoria (fora do escopo). Sem descriptografia/restauração real; a aplicação não verificará a tag ao desarquivar. Não há garantia de redução de tamanho para todo .dd. A cópia .dd em work permanece aberta nesta proposta; proteção se aplica ao artefato final, não a todas as cópias. Senha visível e ausência de login limitam confidencialidade.

## Como a decisão será confirmada no MVP

Demonstrar .zip.enc existente, ZIP aberto ausente, path final e IV persistidos e ARQUIVADO. Testar IV diferente por tentativa, tamanho válido da chave, senha alfanumérica com 10 caracteres e preservação do ZIP se a criptografia falhar. Não criar função de descriptografia para demonstração.

## Relação com a OpenSpec, quando aplicável

Change add-forensic-evidence-registration-and-local-archiving; spec local-archiving e design; checkpoint exclusão-fast-storage separado.
