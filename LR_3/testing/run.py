"""Run the real application against an ephemeral, separately named test database."""
from pathlib import Path
import argparse
import base64
import contextlib
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import project

PROJECT = 'itmo-lr3-tests'
CONFIG = ROOT / 'testing/compose.yaml'
LOGS = ROOT / '.runtime/testing'


def configuration():
    settings = project.read_env(ROOT / 'testing/.env.example')
    local = ROOT / 'testing/.env'
    if local.exists():
        overrides = project.read_env(local)
        if overrides.keys() - settings.keys():
            raise ValueError('testing/.env supports only JAVA_HOME and the three test ports.')
        settings.update(overrides)
    ports = [int(settings[key]) for key in ('TEST_DB_PORT', 'TEST_API_PORT', 'TEST_FRONTEND_PORT')]
    if len(set(ports)) != 3 or any(not 1024 <= port <= 65535 for port in ports):
        raise ValueError('Use three distinct test ports between 1024 and 65535.')
    # Never load server/.env or accept an arbitrary database URL/name.
    env = project.child_environment(settings)
    prefixes = ('DB_', 'TEST_', 'JWT_', 'BOOTSTRAP_', 'IMPORT_', 'SPRING_',
                'SERVER_', 'CORS_', 'RATE_LIMIT_', 'E2E_')
    env = {key: value for key, value in env.items() if not key.startswith(prefixes)}
    db_port, api_port, frontend_port = ports
    url = f'jdbc:postgresql://127.0.0.1:{db_port}/students_test'
    password = secrets.token_urlsafe(24)
    env.update(
        TEST_DB_PORT=str(db_port), TEST_DB_URL=url, TEST_DB_USER='lr3_test', TEST_DB_PASSWORD=password,
        DB_URL=url, DB_USER='lr3_test', DB_PASSWORD=password,
        SERVER_PORT=str(api_port), SERVER_ADDRESS='127.0.0.1',
        JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode(),
        JWT_ISSUER='lr3-test-server', JWT_ACCESS_TTL='3s', JWT_REFRESH_TTL='10m',
        BOOTSTRAP_ADMIN_ENABLED='false', IMPORT_STUDENTS_ENABLED='false',
        RATE_LIMIT_MAX_REQUESTS='10000',
        CORS_ALLOWED_ORIGINS=f'http://127.0.0.1:{frontend_port},http://localhost:{frontend_port},http://127.0.0.1:5500,http://localhost:5500',
        E2E_API_URL=f'http://127.0.0.1:{api_port}/api', E2E_FRONTEND_PORT=str(frontend_port),
        E2E_ADMIN_USER='e2e-admin', E2E_ADMIN_PASSWORD='e2e-admin-password',
        COMPOSE_PROJECT_NAME=PROJECT,
    )
    return settings, env, ports


def compose_args(*args):
    # Fixed project and fixed configuration; never call the working project's Compose file.
    return ['docker', 'compose', '--project-name', PROJECT, '--env-file',
            str(ROOT / 'testing/.env.example'), '-f', str(CONFIG), *args]


def kill_tree(process, record):
    if process.poll() is None and project.owned(record):
        if os.name == 'nt':
            project.command(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture=True, timeout=15)
        else:
            process.terminate()
        process.wait(timeout=15)


def run_logged(args, env, filename, cwd=ROOT, timeout=900):
    print(f'Running {filename}; log: .runtime/testing/{filename}', flush=True)
    with (LOGS / filename).open('w', encoding='utf-8') as output:
        process = subprocess.Popen([str(arg) for arg in args], cwd=cwd, env=env,
                                   stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        record = project.identity(process.pid)
        deadline = time.monotonic() + timeout
        try:
            while process.poll() is None:
                try:
                    process.wait(timeout=min(15, max(.1, deadline - time.monotonic())))
                except subprocess.TimeoutExpired:
                    if time.monotonic() >= deadline:
                        raise RuntimeError(f'{filename} timed out; inspect its log.')
                    print(f'  {filename}: still running...', flush=True)
            if process.returncode:
                raise RuntimeError(f'{filename} failed (exit {process.returncode}); inspect its log.')
        finally:
            kill_tree(process, record)
    print(f'PASS: {filename}', flush=True)


@contextlib.contextmanager
def exclusive_run():
    LOGS.mkdir(parents=True, exist_ok=True)
    with (LOGS / 'run.lock').open('a+b') as lock:
        if lock.tell() == 0:
            lock.write(b'0')
            lock.flush()
        lock.seek(0)
        if os.name == 'nt':
            import msvcrt
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Check configuration/tools only; do not start Docker or tests')
    parser.add_argument('--java-only', action='store_true', help='Run Java tests without browser tests')
    args = parser.parse_args()
    _, env, ports = configuration()
    for tool in ('docker', *(() if args.java_only else ('node', 'npm'))):
        if not shutil.which(tool):
            raise RuntimeError(f'{tool} was not found on PATH.')
    print(f'Test project: {PROJECT}; database: students_test; ports: {ports}', flush=True)
    print(f'JDK: {env["JAVA_HOME"]}', flush=True)
    if args.check:
        print('Configuration OK. No services started; no tests executed.', flush=True)
        return
    with exclusive_run():
        # Port conflicts are errors: never reuse an unrelated backend, frontend or database.
        for port in ports:
            if not project.port_available(port):
                raise RuntimeError(f'Test port {port} is occupied. Set another port in testing/.env.')
        project.ensure_docker()
        backend_record = None
        report = {'status': 'failed', 'project': PROJECT, 'database': 'students_test',
                  'java': False, 'python': False, 'browser': 'not run', 'postman': 'not run'}
        try:
            # No reusable volume. Docker removes the tmpfs data when the container stops.
            run_logged(compose_args('up', '-d', '--wait', '--wait-timeout', '120', 'postgres'),
                       env, 'database.log', timeout=600)
            run_logged([sys.executable, '-m', 'unittest', 'discover', '-s', 'tests/launcher', '-v'], env, 'python.log')
            report['python'] = True
            run_logged([ROOT / 'server/mvnw.cmd', '-B', 'verify'], env, 'java.log', cwd=ROOT / 'server')
            report['java'] = True
            if not args.java_only:
                # Lockfile supplies the exact browser-testing dependency versions.
                lock_hash = hashlib.sha256((ROOT / 'package-lock.json').read_bytes()).hexdigest()
                dependency_marker = LOGS / 'npm-lock.sha256'
                if (not (ROOT / 'node_modules/@playwright/test/package.json').exists()
                        or not dependency_marker.exists()
                        or dependency_marker.read_text().strip() != lock_hash):
                    run_logged(['cmd', '/c', 'npm', 'ci'], env, 'npm.log')
                    dependency_marker.write_text(lock_hash + '\n')
                env.update(BOOTSTRAP_ADMIN_ENABLED='true', BOOTSTRAP_ADMIN_USERNAME='e2e-admin',
                           BOOTSTRAP_ADMIN_EMAIL='e2e-admin@example.org',
                           BOOTSTRAP_ADMIN_PASSWORD=env['E2E_ADMIN_PASSWORD'])
                backend_record = project.spawn([Path(env['JAVA_HOME']) / 'bin/java.exe', '-jar', project.JAR],
                                               env, 'testing/backend.log')
                project.wait_url(f'http://127.0.0.1:{ports[1]}/actuator/health/readiness', backend_record, 120)
                run_logged(['cmd', '/c', 'npm', 'run', 'test:e2e'], env, 'browser.log', timeout=300)
                report['browser'] = True
                # The 3-second TTL belongs to browser expiration scenarios only.
                # Run the Postman contract suite with normal access-token lifetime.
                project.terminate(backend_record)
                env['JWT_ACCESS_TTL'] = '15m'
                env['JWT_REFRESH_TTL'] = '7d'
                backend_record = project.spawn([Path(env['JAVA_HOME']) / 'bin/java.exe', '-jar', project.JAR],
                                               env, 'testing/postman-backend.log')
                project.wait_url(f'http://127.0.0.1:{ports[1]}/actuator/health/readiness', backend_record, 120)
                run_logged(['node', ROOT / 'node_modules/newman/bin/newman.js', 'run',
                            ROOT / 'server/docs/postman/LR3.postman_collection.json',
                            '-e', ROOT / 'server/docs/postman/local.postman_environment.example.json',
                            '--env-var', f'baseUrl=http://127.0.0.1:{ports[1]}',
                            '--env-var', 'adminUsername=e2e-admin', '--env-var', 'adminPassword=e2e-admin-password'],
                           env, 'postman.log', timeout=180)
                report['postman'] = True
            report['status'] = 'passed'
        finally:
            try:
                project.terminate(backend_record)
            finally:
                # --volumes applies only to the fixed test project; working data is not mounted.
                result = project.command(compose_args('down', '--volumes', '--remove-orphans'),
                                         env=env, capture=True, timeout=60, cwd=ROOT)
                (LOGS / 'cleanup.log').write_text((result.stdout or '') + (result.stderr or ''), encoding='utf-8')
                report['cleanup'] = result.returncode == 0
                (LOGS / 'result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
                if result.returncode:
                    raise RuntimeError('Test environment cleanup failed; see .runtime/testing/cleanup.log.')
        print('PASS. Test services and temporary database removed. See .runtime/testing/result.json.', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        print(f'ERROR: {error or "Interrupted"}', file=sys.stderr, flush=True)
        sys.exit(1)
