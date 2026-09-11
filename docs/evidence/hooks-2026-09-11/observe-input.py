# Observador da fixture: preserva stdin, stdout, stderr e exit code do hook original.
import sys,subprocess,json
from pathlib import Path
raw=sys.stdin.buffer.read()
try:
 event=json.loads(raw)
 selected=dict(event)
 with Path('/tmp/arqfor-docker-validation/results/runtime-inputs.jsonl').open('a') as out:
  out.write(json.dumps(selected,ensure_ascii=False)+'\n')
except Exception:
 pass
result=subprocess.run(['/bin/bash','/tmp/arqfor-docker-validation/workspace/.codex/hooks/bloquear-docker.sh'],input=raw)
sys.exit(result.returncode)
