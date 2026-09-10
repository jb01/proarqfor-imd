"""Guardrail conservador; não é uma sandbox nem um executor de Docker."""
import json
import os
from pathlib import Path
import re
import shlex
import stat
import sys


TRANSCRIPTS = Path('/home/josemberg/.codex/sessions')
APPROVAL = 'APROVADO: Use o docker.'
REVIEW = re.compile(r'```arqfor-docker-review\n(.*?)\n```', re.DOTALL)
SUPPORTED_VERSION = '0.153.4'
MAX_INPUT = 65536
MAX_TRANSCRIPT = 16 * 1024 * 1024


class Denied(Exception):
    pass


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise Denied('JSON com campo duplicado.')
        result[key] = value
    return result


def decode(text):
    return json.loads(text, object_pairs_hook=unique_object)


def action_from_event(event):
    if not isinstance(event, dict) or event.get('hook_event_name') != 'PreToolUse':
        raise Denied('Evento inválido.')
    if event.get('tool_name') not in ('Bash', 'exec_command'):
        raise Denied('Ferramenta fora do contrato.')
    args = event.get('tool_input')
    if not isinstance(args, dict):
        raise Denied('Argumentos inválidos.')
    # Extra fields cannot carry approval, environment overrides or another command.
    allowed = {'command', 'cmd', 'workdir', 'shell', 'login', 'max_output_tokens',
               'yield_time_ms', 'sandbox_permissions', 'justification', 'prefix_rule'}
    if set(args) - allowed or ('command' in args) == ('cmd' in args):
        raise Denied('Argumentos não reconhecidos ou ambíguos.')
    command = args.get('command', args.get('cmd'))
    cwd = args.get('workdir', event.get('cwd'))
    if not isinstance(command, str) or not command or len(command) > 16384:
        raise Denied('Comando inválido.')
    if not isinstance(cwd, str) or not Path(cwd).is_absolute():
        raise Denied('Diretório absoluto obrigatório.')
    if str(Path(cwd).resolve(strict=True)) != cwd:
        raise Denied('Diretório precisa ser canônico, sem symlink ou escape.')
    if args.get('shell') != '/bin/bash' or args.get('login') is not False:
        raise Denied('Use shell /bin/bash e login=false para evitar perfis não revisados.')
    # A deliberately small language: even metacharacters inside quotes are denied.
    if any(c in command for c in '\n\r\x00;&|<>$`\\(){}*?[]!~'):
        raise Denied('Expansão, encadeamento ou shell indireto não suportado.')
    words = shlex.split(command, posix=True)
    if not words:
        raise Denied('Comando vazio.')
    action = {'command': command, 'cwd': cwd, 'shell': '/bin/bash', 'login': False}
    if command in ('pwd', 'true', 'false'):
        return action, False
    if words[0] not in ('docker', '/usr/bin/docker', '/bin/docker'):
        raise Denied('Comando fora da gramática conservadora do hook Docker.')
    if len(words) < 2:
        raise Denied('Comando Docker incompleto.')
    return action, True


def read_native_transcript(event, root=TRANSCRIPTS):
    """The root parameter is for isolated unit fixtures, never stdin/CLI/env."""
    value = event.get('transcript_path')
    if not isinstance(value, str):
        raise Denied('Transcript nativo indisponível.')
    path = Path(value)
    if not path.is_absolute() or path.suffix != '.jsonl':
        raise Denied('Transcript inválido.')
    root = Path(root)
    if root.resolve(strict=True) != root or path.resolve(strict=True) != path:
        raise Denied('Symlink ou path não canônico no transcript.')
    path.relative_to(root)  # A project file cannot supply a grant.
    for item in [root, *path.relative_to(root).parents]:
        candidate = item if item.is_absolute() else root / item
        info = candidate.stat()
        if info.st_uid != os.getuid() or info.st_mode & 0o022:
            raise Denied('Diretório do transcript sem proteção de escrita.')
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, 'rb') as source:
        info = os.fstat(source.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o022:
            raise Denied('Transcript não regular ou gravável por terceiros.')
        data = source.read(MAX_TRANSCRIPT + 1)
    if len(data) > MAX_TRANSCRIPT or not data.endswith(b'\n'):
        raise Denied('Transcript incompleto ou acima do limite.')
    return [decode(line) for line in data.decode('utf-8').splitlines()]


def approved_action(rows, event, action):
    session = event.get('session_id')
    turn = event.get('turn_id')
    if not isinstance(session, str) or not session or not isinstance(turn, str) or not turn:
        raise Denied('Sessão ou turno ausente.')
    verified = False
    current_turn = None
    review = None
    grant = None
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get('payload'), dict):
            raise Denied('Formato de transcript desconhecido.')
        payload = row['payload']
        if row.get('type') == 'session_meta':
            if (payload.get('id') != session or payload.get('session_id', session) != session
                    or payload.get('cli_version') != SUPPORTED_VERSION
                    or payload.get('source') != 'vscode' or payload.get('thread_source') != 'user'
                    or payload.get('history_mode') != 'legacy'):
                raise Denied('Identidade, origem ou versão de transcript não suportada.')
            verified = True
        elif row.get('type') == 'event_msg':
            kind = payload.get('type')
            if kind == 'task_started':
                current_turn = payload.get('turn_id')
                grant = None
            elif kind == 'user_message':
                grant = None
                if verified and payload.get('message', '').strip() == APPROVAL and current_turn == turn:
                    if payload.get('images') or payload.get('local_images') or payload.get('audio') or payload.get('local_audio'):
                        raise Denied('Aprovação deve ser uma resposta textual isolada.')
                    grant = review
                review = None
            elif kind == 'agent_message' and payload.get('phase') == 'final_answer':
                matches = REVIEW.findall(payload.get('message', ''))
                review = decode(matches[0]) if len(matches) == 1 else None
            elif kind == 'task_complete' and payload.get('turn_id') == turn:
                grant = None
    if not verified or current_turn != turn or grant != action:
        raise Denied('Não há aprovação humana verificável para esta ação e este turno.')


def evaluate(event):
    action, docker = action_from_event(event)
    if docker:
        approved_action(read_native_transcript(event), event, action)


def main():
    try:
        data = sys.stdin.buffer.read(MAX_INPUT + 1)
        if len(data) > MAX_INPUT:
            raise Denied('Entrada acima do limite.')
        evaluate(decode(data.decode('utf-8')))
        return 0  # No "allow" override: the other hooks and runtime permissions still apply.
    except BaseException:
        # Never echo a transcript, command, host path or secret in the error.
        print('BLOQUEADO: aprovação Docker ausente/inválida ou comando fora do contrato. '
              'Apresente a ação para APROVADO: Use o docker.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
