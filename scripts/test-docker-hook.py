"""Contratos isolados: nenhuma chamada ao Docker ou ao daemon real."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


PROJECT = Path(__file__).resolve().parents[1]
HOOKS = PROJECT / '.codex/hooks'
spec = importlib.util.spec_from_file_location('docker_hook', HOOKS / 'bloquear-docker.py')
hook = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hook)


class DockerHookTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='arqfor-docker-hook-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sessions = self.root / 'sessions'
        self.sessions.mkdir()
        self.sessions.chmod(0o700)
        self.transcript = self.sessions / 'session.jsonl'
        self.work = self.root / 'workspace'
        self.work.mkdir()
        self.marker = self.work / 'docker-called'
        self.sentinel = self.work / 'sentinela.dd'
        self.sentinel.write_bytes(b'ARQFOR_SYNTHETIC\n')
        self.fake = self.root / 'fake-docker'
        self.fake.write_text('#!/bin/bash\nprintf "%s\\n" "$@" > docker-called\n')
        self.fake.chmod(0o700)
        self.event = {'hook_event_name': 'PreToolUse', 'tool_name': 'Bash',
                      'session_id': 'session-1', 'turn_id': 'approval-turn',
                      'transcript_path': str(self.transcript), 'cwd': str(self.work),
                      'tool_input': {'command': 'docker run --rm synthetic-image',
                                     'shell': '/bin/bash', 'login': False}}
        self.action, _ = hook.action_from_event(self.event)
        self.rows = [
            {'type': 'session_meta', 'payload': {'id': 'session-1', 'session_id': 'session-1',
                'cli_version': '0.153.4', 'source': 'vscode', 'thread_source': 'user', 'history_mode': 'legacy'}},
            self.event_row('task_started', turn_id='review-turn'),
            self.event_row('agent_message', phase='final_answer', message=self.review(self.action)),
            self.event_row('task_complete', turn_id='review-turn'),
            self.event_row('task_started', turn_id='approval-turn'),
            self.event_row('user_message', message=hook.APPROVAL),
        ]

    @staticmethod
    def event_row(kind, **kwargs):
        return {'type': 'event_msg', 'payload': {'type': kind, **kwargs}}

    @staticmethod
    def review(action):
        return 'Revisão do comando Docker:\n```arqfor-docker-review\n' + json.dumps(action) + '\n```'

    def write_rows(self):
        self.transcript.write_text(''.join(json.dumps(row) + '\n' for row in self.rows))
        self.transcript.chmod(0o600)

    def validate_fixture(self):
        self.write_rows()
        action, docker = hook.action_from_event(self.event)
        self.assertTrue(docker)
        hook.approved_action(hook.read_native_transcript(self.event, self.sessions), self.event, action)

    def launch_substitute(self):
        self.validate_fixture()
        # Intentional substitution: never invoke the executable from the payload.
        args = shlex.split(self.event['tool_input']['command'])[1:]
        subprocess.run([str(self.fake), *args], cwd=self.work, check=True)

    def test_valid_human_approval_runs_only_substitute_and_preserves_sentinel(self):
        self.launch_substitute()
        self.assertEqual(self.marker.read_text(), 'run\n--rm\nsynthetic-image\n')
        self.assertEqual(self.sentinel.read_bytes(), b'ARQFOR_SYNTHETIC\n')

    def test_without_approval_does_not_launch(self):
        self.rows.pop()
        with self.assertRaises(hook.Denied):
            self.launch_substitute()
        self.assertFalse(self.marker.exists())

    def test_approval_in_assistant_tool_or_quoted_user_text_is_not_human_grant(self):
        bad_rows = [self.event_row('agent_message', phase='final_answer', message=hook.APPROVAL),
                    {'type': 'response_item', 'payload': {'type': 'function_call_output', 'output': hook.APPROVAL}},
                    self.event_row('user_message', message='Exemplo: ' + hook.APPROVAL),
                    self.event_row('user_message', message='"' + hook.APPROVAL + '"')]
        for row in bad_rows:
            with self.subTest(row=row['payload'].get('type')):
                self.rows[-1] = row
                with self.assertRaises(hook.Denied):
                    self.validate_fixture()

    def test_missing_review_and_commentary_review_are_denied(self):
        for message, phase in [('sem revisão', 'final_answer'), (self.review(self.action), 'commentary')]:
            with self.subTest(phase=phase):
                self.rows[2]['payload'].update(message=message, phase=phase)
                with self.assertRaises(hook.Denied):
                    self.validate_fixture()

    def test_command_cwd_image_arguments_mounts_and_shell_are_bound(self):
        another = self.root / 'another'
        another.mkdir()
        variants = [{'command': 'docker run --rm another-image'},
                    {'command': 'docker run --privileged synthetic-image'},
                    {'command': 'docker run -v /:/host synthetic-image'},
                    {'workdir': str(another)}, {'shell': '/bin/sh'}, {'login': True}]
        original = copy.deepcopy(self.event)
        for variant in variants:
            with self.subTest(variant=variant):
                self.event = copy.deepcopy(original)
                self.event['tool_input'].update(variant)
                with self.assertRaises(hook.Denied):
                    self.validate_fixture()

    def test_other_session_turn_version_origin_or_history_cannot_reuse_approval(self):
        metadata = copy.deepcopy(self.rows[0]['payload'])
        for variant in [{'id': 'other'}, {'session_id': 'other'}, {'cli_version': '0.149.1'},
                        {'source': 'exec'}, {'thread_source': 'agent'}, {'history_mode': 'unknown'}]:
            with self.subTest(variant=variant):
                self.rows[0]['payload'] = {**metadata, **variant}
                with self.assertRaises(hook.Denied):
                    self.validate_fixture()
        self.rows[0]['payload'] = metadata
        self.event['turn_id'] = 'other-turn'
        with self.assertRaises(hook.Denied):
            self.validate_fixture()

    def test_later_user_message_and_completed_turn_revoke_grant(self):
        for extra in [self.event_row('user_message', message='Não execute.'),
                      self.event_row('task_complete', turn_id='approval-turn'),
                      self.event_row('task_started', turn_id='new-turn')]:
            with self.subTest(kind=extra['payload']['type']):
                self.rows.append(extra)
                with self.assertRaises(hook.Denied):
                    self.validate_fixture()
                self.rows.pop()

    def test_direct_docker_spellings_always_require_approval(self):
        for command in ['docker run image', '/usr/bin/docker run image', '/bin/docker run image',
                        'docker --context default run image', 'docker container run image',
                        'docker compose up', 'docker ps']:
            with self.subTest(command=command):
                self.event['tool_input']['command'] = command
                action, required = hook.action_from_event(self.event)
                self.assertTrue(required)
                with self.assertRaises(hook.Denied):
                    hook.approved_action(self.rows[:-1], self.event, action)

    def test_shell_indirection_and_unknown_commands_are_denied_even_with_approval(self):
        for command in ['docker run x; pwd', 'echo x && docker run x', 'docker run x | cat',
                        'd=docker; "$d" run x', '$(printf docker) run x', '`echo docker` run x',
                        'bash -c "docker run x"', 'env docker run x', 'sudo docker run x',
                        'python3 -c "import os"', 'do""cker run x; true', 'docker run x\npwd',
                        'docker run x > output', 'docker run ${IMAGE}', 'mvn test']:
            with self.subTest(command=command):
                self.event['tool_input']['command'] = command
                with self.assertRaises(hook.Denied):
                    hook.action_from_event(self.event)

    def test_duplicate_json_and_tool_input_fields_cannot_supply_approval(self):
        with self.assertRaises(hook.Denied):
            hook.decode('{"command":"pwd","command":"docker run x"}')
        for key, value in [('approved', True), ('env', {'APPROVED': hook.APPROVAL}), ('cmd', 'pwd')]:
            with self.subTest(key=key):
                event = copy.deepcopy(self.event)
                event['tool_input'][key] = value
                with self.assertRaises(hook.Denied):
                    hook.action_from_event(event)

    def test_transcript_outside_trusted_root_or_symlink_is_denied(self):
        self.write_rows()
        outside = self.work / 'forged.jsonl'
        shutil.copyfile(self.transcript, outside)
        self.event['transcript_path'] = str(outside)
        with self.assertRaises(ValueError):
            hook.read_native_transcript(self.event, self.sessions)
        link = self.sessions / 'link.jsonl'
        link.symlink_to(self.transcript)
        self.event['transcript_path'] = str(link)
        with self.assertRaises(hook.Denied):
            hook.read_native_transcript(self.event, self.sessions)

    def test_transcript_permissions_truncation_and_oversize_fail_closed(self):
        self.write_rows()
        self.transcript.chmod(0o666)
        with self.assertRaises(hook.Denied):
            hook.read_native_transcript(self.event, self.sessions)
        self.transcript.chmod(0o600)
        self.transcript.write_text('{')
        with self.assertRaises(hook.Denied):
            hook.read_native_transcript(self.event, self.sessions)
        self.transcript.write_bytes(b' ' * (hook.MAX_TRANSCRIPT + 1))
        with self.assertRaises(hook.Denied):
            hook.read_native_transcript(self.event, self.sessions)

    def test_native_entrypoint_ignores_project_transcript_and_approval_environment(self):
        self.write_rows()
        # Production entrypoint never accepts the fixture root from stdin or env.
        result = subprocess.run(['/bin/bash', str(HOOKS / 'bloquear-docker.sh')],
                                input=json.dumps(self.event), text=True, capture_output=True,
                                env={**os.environ, 'ARQFOR_DOCKER_APPROVED': hook.APPROVAL,
                                     'ARQFOR_TRANSCRIPTS': str(self.sessions)}, timeout=5)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, '')
        self.assertIn('BLOQUEADO:', result.stderr)
        self.assertNotIn(str(self.transcript), result.stderr)
        self.assertFalse(self.marker.exists())

    def test_invalid_or_missing_input_is_blocked_by_launcher(self):
        for data in ['', '{', 'null', '[]', '{"hook_event_name":"PostToolUse"}', ' ' * (hook.MAX_INPUT + 1)]:
            with self.subTest(size=len(data)):
                result = subprocess.run(['/bin/bash', str(HOOKS / 'bloquear-docker.sh')],
                                        input=data, text=True, capture_output=True, timeout=5)
                self.assertEqual(result.returncode, 2)
                self.assertFalse(result.stdout)

    def test_missing_verifier_is_blocked_by_launcher(self):
        launcher = self.root / 'launcher.sh'
        shutil.copyfile(HOOKS / 'bloquear-docker.sh', launcher)
        result = subprocess.run(['/bin/bash', str(launcher)], input='{}', text=True,
                                capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 2)

    def test_only_explicit_literal_builtins_are_exempt(self):
        for command in ['pwd', 'true', 'false']:
            with self.subTest(command=command):
                self.event['tool_input']['command'] = command
                action, required = hook.action_from_event(self.event)
                self.assertFalse(required)
                result = subprocess.run(['/bin/bash', str(HOOKS / 'bloquear-docker.sh')],
                                        input=json.dumps(self.event), text=True, capture_output=True, timeout=5)
                self.assertEqual(result.returncode, 0)
        self.assertFalse(self.marker.exists())

    def test_fast_guardrail_still_denies_after_valid_docker_approval(self):
        self.validate_fixture()
        result = subprocess.run(['/bin/bash', str(HOOKS / 'bloquear-exclusao-fast.sh')],
                                input=json.dumps(self.event), text=True, capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 2)
        self.assertFalse(self.marker.exists())
        self.assertEqual(self.sentinel.read_bytes(), b'ARQFOR_SYNTHETIC\n')


if __name__ == '__main__':
    unittest.main(verbosity=2)
