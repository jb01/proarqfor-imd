#!/bin/bash
# Testa somente o contrato do script. Não comprova interceptação pelo Codex.
# Os payloads são texto: nunca usar eval nem executá-los como comandos.
set -euo pipefail

project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
hook_source="$project_root/.codex/hooks/bloquear-exclusao-fast.sh"
/bin/bash -n "$hook_source"
test_root=$(mktemp -d /tmp/arqfor-hook-contract.XXXXXX)
mkdir -p "$test_root/storage/fast"
cp -- "$hook_source" "$test_root/hook.sh"
printf '%s\n' 'ARQFOR_SENTINELA_SINTETICA' > "$test_root/storage/fast/sentinela.dd"

names=(leitura rm mv sobrescrita diretorio_pai absoluto cwd variavel symlink interpretador shell_indireto pipeline vazio json_invalido null exec_command)
mapfile -t payloads <<'JSON'
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"pwd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm storage/fast/sentinela.dd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"mv storage/fast/sentinela.dd movido.dd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"> storage/fast/sentinela.dd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm -rf storage"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm /tmp/arqfor-hook-fixture/storage/fast/sentinela.dd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"cd storage/fast && rm sentinela.dd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"alvo=storage/fast/sentinela.dd; rm \"$alvo\""}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rm atalho/sentinela.dd"}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"python3 -c \"from pathlib import Path; Path('storage/fast/sentinela.dd').unlink()\""}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"bash -c \"rm storage/fast/sentinela.dd\""}}
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"printf x | tee storage/fast/sentinela.dd"}}

{
null
{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"cmd":"pwd"}}
JSON

[[ ${#names[@]} -eq ${#payloads[@]} ]]
for i in "${!payloads[@]}"; do
    result=0
    (cd -- "$test_root" && /bin/bash "$test_root/hook.sh" <<< "${payloads[$i]}") \
        > "$test_root/${names[$i]}.stdout" 2> "$test_root/${names[$i]}.stderr" || result=$?
    [[ $result -eq 2 ]]
    [[ ! -s "$test_root/${names[$i]}.stdout" ]]
    [[ $(< "$test_root/${names[$i]}.stderr") == BLOQUEADO:* ]]
    [[ $(< "$test_root/storage/fast/sentinela.dd") == ARQFOR_SENTINELA_SINTETICA ]]
    printf 'PASS %s: exit=2, stdout vazio, sentinela preservada\n' "${names[$i]}"
done
printf 'Contrato: %s/%s casos passaram. Artefatos: %s\n' "${#names[@]}" "${#names[@]}" "$test_root"
printf '%s\n' 'Integração PreToolUse não é testada por este script.'
