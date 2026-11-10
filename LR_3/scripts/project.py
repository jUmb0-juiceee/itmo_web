"""Local Windows launcher; uses the Python already needed for the previous lab."""
from pathlib import Path
import argparse
import base64
import ctypes
import hashlib
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import webbrowser

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / 'server'
RUNTIME = ROOT / '.runtime'
ENV_FILE = SERVER / '.env'
STATE_FILE = RUNTIME / 'processes.json'
IMPORT_MARKER = RUNTIME / 'import-completed.json'
JAR = SERVER / 'target/students-server-0.1.0-SNAPSHOT.jar'
PROJECT = 'itmo-lr3'


def read_env(path=None):
    path = path or ENV_FILE
    settings = {}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        if not separator or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key.strip()):
            raise ValueError('Invalid entry in the local .env file')
        key, value = key.strip(), value.strip()
        if key in settings:
            raise ValueError(f'Duplicate .env key: {key}')
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        settings[key] = value
    return settings


def update_env(key, value):
    lines = ENV_FILE.read_text(encoding='utf-8').splitlines()
    result = []
    found = False
    for line in lines:
        if line.partition('=')[0].strip() == key:
            result.append(f'{key}={value}')
            found = True
        else:
            result.append(line)
    if not found:
        result.append(f'{key}={value}')
    ENV_FILE.write_text('\n'.join(result) + '\n', encoding='utf-8')


def command(args, *, env=None, timeout=None, capture=False, cwd=SERVER):
    args = [str(arg) for arg in args]
    process = subprocess.Popen(args, cwd=cwd, env=env, stdout=subprocess.PIPE if capture else None,
                               stderr=subprocess.PIPE if capture else None, text=True,
                               encoding='utf-8', errors='replace',
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    record = identity(process.pid)
    try:
        output, error = process.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        if os.name == 'nt' and owned(record):
            # Kill the timed-out CLI and its plugin children, not Docker Desktop/the engine.
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True, timeout=10,
                           creationflags=subprocess.CREATE_NO_WINDOW)
        else:
            process.kill()
        process.communicate(timeout=10)
        raise
    return subprocess.CompletedProcess(args, process.returncode, output, error)


def find_java(settings=None):
    candidates = []
    for value in ((settings or {}).get('JAVA_HOME'), os.environ.get('JAVA_HOME')):
        if value:
            candidates.append(Path(value))
    binary = shutil.which('javac')
    if binary:
        candidates.append(Path(binary).resolve().parent.parent)
    for base, pattern in [('C:/Program Files/JetBrains', '*/jbr'),
                          ('C:/Program Files/Java', 'jdk*'),
                          ('C:/Program Files/Eclipse Adoptium', 'jdk*')]:
        if Path(base).exists():
            candidates.extend(Path(base).glob(pattern))
    for candidate in candidates:
        compiler = candidate / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
        if compiler.is_file():
            result = command([compiler, '-version'], timeout=10, capture=True)
            version = re.search(r'javac (\d+)', result.stdout + result.stderr)
            if result.returncode == 0 and version and int(version.group(1)) >= 17:
                return candidate.resolve()
    raise RuntimeError('JDK 17+ was not found. Install a JDK and set JAVA_HOME in server/.env.')


def free_port(preferred):
    for port in range(preferred, preferred + 30):
        if port_available(port):
            return port
    raise RuntimeError(f'No free local port near {preferred}')


def port_available(port):
    try:
        with socket.socket() as sock:
            if os.name == 'nt':
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            sock.bind(('127.0.0.1', port))
        return True
    except OSError:
        return False


def setup():
    RUNTIME.mkdir(exist_ok=True)
    if ENV_FILE.exists():
        settings = read_env()
        find_java(settings)
        print('Local configuration exists; passwords and keys were preserved.', flush=True)
        return settings
    java = find_java()
    db_port, api_port, web_port = free_port(55432), free_port(8080), free_port(5500)
    password = secrets.token_urlsafe(24)
    settings = {
        'COMPOSE_PROJECT_NAME': PROJECT, 'JAVA_HOME': java.as_posix(),
        'DB_NAME': 'students', 'DB_USER': 'students', 'DB_PASSWORD': secrets.token_urlsafe(24),
        'DB_PORT': str(db_port), 'DB_URL': f'jdbc:postgresql://127.0.0.1:{db_port}/students',
        'SERVER_PORT': str(api_port), 'FRONTEND_PORT': str(web_port),
        'JWT_SECRET': base64.b64encode(secrets.token_bytes(32)).decode(),
        'JWT_ISSUER': 'students-server', 'JWT_ACCESS_TTL': '15m', 'JWT_REFRESH_TTL': '7d',
        'CORS_ALLOWED_ORIGINS': f'http://127.0.0.1:{web_port},http://localhost:{web_port}',
        'BOOTSTRAP_ADMIN_ENABLED': 'true', 'BOOTSTRAP_ADMIN_USERNAME': 'admin',
        'BOOTSTRAP_ADMIN_EMAIL': 'admin@local.test', 'BOOTSTRAP_ADMIN_PASSWORD': password,
        'RATE_LIMIT_MAX_REQUESTS': '100', 'JPA_LOG_LEVEL': 'WARNING', 'APP_LOG_LEVEL': 'INFO',
    }
    ENV_FILE.write_text('# Local configuration. Do not commit this file.\n' +
                        '\n'.join(f'{key}={value}' for key, value in settings.items()) + '\n', encoding='utf-8')
    (RUNTIME / 'admin-credentials.json').write_text(json.dumps({
        'username': 'admin', 'password': password, 'email': 'admin@local.test',
        'loginUrl': f'http://127.0.0.1:{web_port}/login.html'
    }, indent=2) + '\n', encoding='utf-8')
    print(f'Configuration created. JDK: {java}', flush=True)
    print('Admin credentials: LR_3/.runtime/admin-credentials.json (not printed to the console).', flush=True)
    return settings


def child_environment(settings):
    env = os.environ.copy()
    env.update(settings)
    java = find_java(settings)
    env['JAVA_HOME'] = str(java)
    env['PATH'] = str(java / 'bin') + os.pathsep + env.get('PATH', '')
    env['PYTHONUTF8'] = '1'
    env['DEBUG'] = 'false'
    env['SERVER_ADDRESS'] = '127.0.0.1'
    return env


def compose(settings, *args, capture=False, timeout=180):
    if settings.get('COMPOSE_PROJECT_NAME', PROJECT) != PROJECT:
        raise RuntimeError('This launcher only manages the itmo-lr3 Compose project.')
    return command(['docker', 'compose', '--project-name', PROJECT, '--env-file', ENV_FILE,
                    '-f', SERVER / 'compose.yaml', *args], env={**os.environ, **settings},
                   capture=capture, timeout=timeout)


def ensure_docker():
    if not shutil.which('docker'):
        raise RuntimeError('Docker Desktop is not installed or docker is not on PATH.')
    deadline = time.monotonic() + 150
    attempted = False
    while time.monotonic() < deadline:
        try:
            result = command(['docker', 'info', '--format', '{{.ServerVersion}}'], capture=True, timeout=8)
            if result.returncode == 0:
                print('Docker engine is ready.', flush=True)
                return
        except subprocess.TimeoutExpired:
            pass
        if not attempted:
            print('Starting Docker Desktop; waiting for the engine...', flush=True)
            command(['docker', 'desktop', 'start', '--detach'], capture=True, timeout=25)
            attempted = True
        time.sleep(2)
    raise RuntimeError('Docker did not become ready. Open Docker Desktop and retry start.cmd.')


def identity(pid):
    if os.name == 'nt':
        from ctypes import wintypes
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
        kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            return None
        try:
            times = [wintypes.FILETIME() for _ in range(4)]
            size = wintypes.DWORD(32768)
            image = ctypes.create_unicode_buffer(size.value)
            if not kernel.GetProcessTimes(handle, *(ctypes.byref(value) for value in times)):
                return None
            if not kernel.QueryFullProcessImageNameW(handle, 0, image, ctypes.byref(size)):
                return None
            created = times[0].dwHighDateTime << 32 | times[0].dwLowDateTime
            return {'pid': pid, 'created': created, 'executable': image.value.casefold()}
        finally:
            kernel.CloseHandle(handle)
    try:
        return {'pid': pid, 'created': Path(f'/proc/{pid}/stat').read_text().split()[21],
                'executable': str(Path(f'/proc/{pid}/exe').resolve())}
    except OSError:
        return None


def owned(record):
    return bool(record and identity(record['pid']) == record)


def terminate(record):
    if owned(record):
        # The PID and creation time must match our stored process, never just its name.
        if os.name == 'nt':
            result = command(['taskkill', '/PID', str(record['pid']), '/F'], capture=True, timeout=10)
            if result.returncode != 0 and owned(record):
                raise RuntimeError(f'Could not stop owned process {record["pid"]}')
        else:
            import signal
            os.kill(record['pid'], signal.SIGTERM)


def read_state():
    return json.loads(STATE_FILE.read_text(encoding='utf-8')) if STATE_FILE.exists() else {}


def write_state(state):
    temporary = STATE_FILE.with_suffix('.tmp')
    temporary.write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')
    temporary.replace(STATE_FILE)


def spawn(args, env, log_name):
    with (RUNTIME / log_name).open('ab', buffering=0) as log:
        flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        process = subprocess.Popen([str(arg) for arg in args], cwd=ROOT, env=env,
                                   stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                   creationflags=flags, start_new_session=os.name != 'nt')
    record = identity(process.pid)
    if not record:
        raise RuntimeError(f'Could not record the new process; see {log_name}')
    return record


def wait_url(url, record, timeout, project_id=None):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not owned(record):
            raise RuntimeError('A service exited during startup; inspect LR_3/.runtime logs.')
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status == 200 and (project_id is None or response.headers.get('X-LR3-Project') == project_id):
                    return
        except urllib.error.HTTPError as error:
            error.close()
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(.5)
    raise RuntimeError(f'Startup timeout: {url}; inspect LR_3/.runtime logs.')


def prepare_import():
    source = ROOT / 'students.json'
    rows = json.loads(source.read_text(encoding='utf-8'))
    corrected = []
    for row in rows:
        if row.get('isu') == 100097 and row.get('fio') == '\u0428\u0435\u0441\u0442akov \u0418\u043b\u044c\u044f \u0410\u043b\u0435\u043a\u0441\u0430\u043d\u0434\u0440\u043e\u0432\u0438\u0447':
            row['fio'] = '\u0428\u0435\u0441\u0442\u0430\u043a\u043e\u0432 \u0418\u043b\u044c\u044f \u0410\u043b\u0435\u043a\u0441\u0430\u043d\u0434\u0440\u043e\u0432\u0438\u0447'
            corrected.append(100097)
    destination = RUNTIME / 'students.initial.json'
    destination.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (RUNTIME / 'import-source.json').write_text(json.dumps({
        'source': str(source), 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'rows': len(rows), 'correctedIsu': corrected,
        'note': 'The FIO correction was explicitly approved by the user. Original JSON is unchanged.'
    }, indent=2) + '\n', encoding='utf-8')
    return destination


def needs_build():
    if not JAR.exists():
        return True
    inputs = [SERVER / 'pom.xml', *[path for path in (SERVER / 'src/main').rglob('*') if path.is_file()]]
    return any(path.stat().st_mtime > JAR.stat().st_mtime for path in inputs)


def start(no_browser=False):
    settings = setup()
    env = child_environment(settings)
    state = read_state()
    if owned(state.get('backend')) and owned(state.get('frontend')):
        ensure_docker()
        if compose(settings, 'up', '-d', '--wait', '--wait-timeout', '120', 'postgres').returncode:
            raise RuntimeError('PostgreSQL is unavailable.')
        wait_url(f'http://127.0.0.1:{settings["SERVER_PORT"]}/actuator/health', state['backend'], 30)
        print(f'Already running: http://127.0.0.1:{settings["FRONTEND_PORT"]}/', flush=True)
        if not no_browser:
            webbrowser.open(f'http://127.0.0.1:{settings["FRONTEND_PORT"]}/')
        return
    for key in ('frontend', 'backend'):
        terminate(state.get(key))
    write_state({})
    for key in ('SERVER_PORT', 'FRONTEND_PORT'):
        if not port_available(int(settings[key])):
            raise RuntimeError(f'{key}={settings[key]} is occupied by another process; nothing was stopped.')
    ensure_docker()
    image = command(['docker', 'image', 'inspect', 'postgres:17'], capture=True, timeout=10)
    if image.returncode != 0:
        print('Downloading PostgreSQL 17 once; the first download can take several minutes...', flush=True)
        if command(['docker', 'pull', 'postgres:17'], timeout=600).returncode:
            raise RuntimeError('Could not download postgres:17. Check Docker Desktop network access.')
    print(f'Starting PostgreSQL: project {PROJECT}, localhost:{settings["DB_PORT"]}...', flush=True)
    if compose(settings, 'up', '-d', '--wait', '--wait-timeout', '120', 'postgres').returncode:
        raise RuntimeError('PostgreSQL did not start. No other Compose projects were changed.')
    if needs_build():
        print('Building Java server; build log: .runtime/build.log', flush=True)
        with (RUNTIME / 'build.log').open('w', encoding='utf-8') as log:
            result = subprocess.run([str(SERVER / 'mvnw.cmd'), '-B', '-DskipTests', 'package'], cwd=SERVER,
                                    env=env, stdout=log, stderr=subprocess.STDOUT,
                                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        if result.returncode:
            raise RuntimeError('Java build failed. See LR_3/.runtime/build.log.')
    importing = not IMPORT_MARKER.exists()
    if importing:
        env['IMPORT_STUDENTS_ENABLED'] = 'true'
        env['IMPORT_STUDENTS_PATH'] = str(prepare_import())
        env['IMPORT_STUDENTS_REPORT'] = str(RUNTIME / 'import-report.json')
    else:
        env['IMPORT_STUDENTS_ENABLED'] = 'false'
    env['DB_URL'] = f'jdbc:postgresql://127.0.0.1:{settings["DB_PORT"]}/{settings["DB_NAME"]}'
    web_port = int(settings['FRONTEND_PORT'])
    origins = settings.get('CORS_ALLOWED_ORIGINS', '').split(',')
    env['CORS_ALLOWED_ORIGINS'] = ','.join(dict.fromkeys(filter(None, [*origins,
        f'http://127.0.0.1:{web_port}', f'http://localhost:{web_port}'])))
    try:
        print('Starting Spring Boot; waiting for migrations, ADMIN and initial import...', flush=True)
        state['backend'] = spawn([Path(env['JAVA_HOME']) / 'bin/java.exe', '-jar', JAR], env, 'backend.log')
        write_state(state)
        wait_url(f'http://127.0.0.1:{settings["SERVER_PORT"]}/actuator/health/readiness', state['backend'], 120)
        if importing:
            report = json.loads((RUNTIME / 'import-report.json').read_text(encoding='utf-8'))
            IMPORT_MARKER.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
            print(f'Students: {report["validated"]} validated, {report["imported"]} imported, {report["existing"]} already present.', flush=True)
        if settings.get('BOOTSTRAP_ADMIN_ENABLED') == 'true':
            update_env('BOOTSTRAP_ADMIN_ENABLED', 'false')
            update_env('BOOTSTRAP_ADMIN_PASSWORD', '')
        print('Starting the local frontend...', flush=True)
        state['frontend'] = spawn([sys.executable, ROOT / 'scripts/static_server.py', '--port', web_port,
                                   '--api-port', settings['SERVER_PORT']], env, 'frontend.log')
        write_state(state)
        project_id = hashlib.sha256(str(ROOT).encode()).hexdigest()[:16]
        wait_url(f'http://127.0.0.1:{web_port}/login.html', state['frontend'], 15, project_id)
    except Exception:
        for key in ('frontend', 'backend'):
            terminate(state.get(key))
        write_state({})
        raise
    print(f'READY: http://127.0.0.1:{web_port}/', flush=True)
    print(f'Swagger: http://127.0.0.1:{settings["SERVER_PORT"]}/swagger-ui.html', flush=True)
    print('Login: admin; password is in .runtime/admin-credentials.json', flush=True)
    print('Stop this project with stop.cmd. Its database volume will be preserved.', flush=True)
    if not no_browser:
        webbrowser.open(f'http://127.0.0.1:{web_port}/')


def stop():
    if not RUNTIME.exists():
        print('This project has not been started.')
        return
    state = read_state()
    for key in ('frontend', 'backend'):
        terminate(state.get(key))
    write_state({})
    if ENV_FILE.exists():
        result = compose(read_env(), 'stop', '-t', '15', 'postgres', capture=True, timeout=30)
        if result.returncode:
            print('Java/frontend stopped. Docker is unavailable or PostgreSQL was already stopped.')
    print('Stopped LR_3. Database data and other projects were preserved.', flush=True)


def status():
    state = read_state() if RUNTIME.exists() else {}
    print('Backend:', 'running' if owned(state.get('backend')) else 'stopped')
    print('Frontend:', 'running' if owned(state.get('frontend')) else 'stopped')
    if ENV_FILE.exists():
        print('Compose project:', PROJECT)
        result = compose(read_env(), 'ps', 'postgres', capture=True, timeout=15)
        print(result.stdout.strip() or 'PostgreSQL is stopped or Docker is unavailable.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['setup', 'start', 'stop', 'status'])
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    try:
        RUNTIME.mkdir(exist_ok=True)
        lock = None
        if args.action != 'status':
            lock = (RUNTIME / 'launcher.lock').open('a+b')
            if lock.tell() == 0:
                lock.write(b'0')
                lock.flush()
            lock.seek(0)
            if os.name == 'nt':
                import msvcrt
                try:
                    msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                except OSError:
                    raise RuntimeError('Another LR_3 launcher is already working. Wait for it to finish.')
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.action == 'setup': setup()
        elif args.action == 'start': start(args.no_browser)
        elif args.action == 'stop': stop()
        else: status()
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        print(f'ERROR: {error}', file=sys.stderr, flush=True)
        sys.exit(1)
