#!/bin/bash
# O verificador nunca executa o comando recebido. Falhas também negam a chamada.
if [[ ! -x /usr/bin/python3 ]]; then
    printf '%s\n' 'BLOQUEADO: verificador Docker indisponível.' >&2
    exit 2
fi
hook_dir=$(cd -- "${BASH_SOURCE[0]%/*}" && pwd) || exit 2
/usr/bin/python3 -I "$hook_dir/bloquear-docker.py"
result=$?
if [[ $result -eq 0 ]]; then
    exit 0
fi
printf '%s\n' 'BLOQUEADO: Docker exige revisão da ação e APROVADO: Use o docker. Não contornar outros guardrails.' >&2
exit 2
