#!/usr/bin/env python3
"""Run the separate validation mod against an already installed disposable server."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('server', type=Path)
    parser.add_argument('--mode', choices=['fresh', 'restart'], required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--timeout', type=int, default=300)
    args = parser.parse_args()
    server = args.server.resolve()
    report = server / f'validation-{args.mode}.json'
    if report.exists():
        parser.error(f'Refusing to reuse existing report: {report}')
    log = server / f'server-{args.mode}.log'
    command = [args.java, '-Xms1G', '-Xmx6G', '-Dfml.disableVersionCheck=true',
               f'-Dcsb.validation={args.mode}',
               '@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt', 'nogui']
    with log.open('w') as output:
        process = subprocess.Popen(command, cwd=server, stdout=output, stderr=subprocess.STDOUT,
                                   stdin=subprocess.PIPE, text=True)
        deadline = time.monotonic() + args.timeout
        failure = None
        while process.poll() is None:
            text = log.read_text(errors='replace')
            if any(marker in text for marker in ['Failed to load registries', 'Failed to start the minecraft server',
                                                 'CSB_VALIDATION_FAILED', 'ModLoadingException']):
                failure = 'Server/test failure; see log'
                break
            if time.monotonic() >= deadline:
                failure = 'Server validation timed out'
                break
            time.sleep(1)
        if process.poll() is None:
            try:
                process.communicate('stop\n', timeout=15)
            except (subprocess.TimeoutExpired, BrokenPipeError):
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        print(f'Exit: {process.returncode}; log: {log}')
        if failure:
            raise SystemExit(failure)
    if not report.exists():
        raise SystemExit('No validation report produced')
    result = json.loads(report.read_text())
    print(json.dumps(result, indent=2))
    if process.returncode != 0 or not result.get('passed'):
        raise SystemExit('Validation failed')
    if 'All dimensions are saved' not in log.read_text(errors='replace'):
        raise SystemExit('Missing clean shutdown evidence')


if __name__ == '__main__':
    main()
