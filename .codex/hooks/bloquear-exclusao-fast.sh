#!/bin/bash
# Guardrail acadêmico PreToolUse: não executar nesta etapa.
# Quando ligado a Bash, nega todo shell para proteger storage/fast também
# de mutações indiretas. Não avalia stdin, não executa comandos e não toca arquivos.
# A aprovação de exclusão não é interpretada pelo script nem cria bypass.
printf '%s\n' 'BLOQUEADO: shell desabilitado pelo guardrail de storage/fast. Revisão humana obrigatória; não contornar o hook.' >&2
exit 2
