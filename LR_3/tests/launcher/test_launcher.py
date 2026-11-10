import contextlib
import importlib.util
import io
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[2]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


launcher = load('lr3_launcher', 'project.py')
static = load('lr3_static', 'static_server.py')


class LauncherTests(unittest.TestCase):
    def test_env_is_data_and_preserves_embedded_equals(self):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / '.env'
            file.write_text('# Comment\nJWT_SECRET=abc=def\nTEXT=$(not-a-command)\nPATH_VALUE="a b"\n', encoding='utf-8')
            self.assertEqual(launcher.read_env(file), {
                'JWT_SECRET': 'abc=def', 'TEXT': '$(not-a-command)', 'PATH_VALUE': 'a b'
            })
            file.write_text('KEY=one\nKEY=two\n', encoding='utf-8')
            with self.assertRaises(ValueError):
                launcher.read_env(file)

    def test_setup_preserves_keys_and_passwords_on_repeated_calls(self):
        with tempfile.TemporaryDirectory() as temporary, contextlib.redirect_stdout(io.StringIO()):
            root = Path(temporary)
            with patch.object(launcher, 'RUNTIME', root / '.runtime'), \
                 patch.object(launcher, 'ENV_FILE', root / '.env'), \
                 patch.object(launcher, 'find_java', return_value=root / 'jdk'), \
                 patch.object(launcher, 'free_port', side_effect=[55432, 8080, 5500]):
                first = launcher.setup()
                original = (root / '.runtime/admin-credentials.json').read_bytes()
                second = launcher.setup()
                digest = lambda value: hashlib.sha256(json.dumps(value, sort_keys=True).encode()).digest()
                self.assertEqual(digest(first), digest(second))
                self.assertEqual(original, (root / '.runtime/admin-credentials.json').read_bytes())
                self.assertGreater(len(first['JWT_SECRET']), 32)

    def test_import_correction_does_not_modify_the_original(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            runtime = root / '.runtime'
            runtime.mkdir()
            source = root / 'students.json'
            source.write_text(json.dumps([{'isu': 100097, 'fio': 'Шестakov Илья Александрович'}], ensure_ascii=False), encoding='utf-8')
            before = source.read_bytes()
            with patch.object(launcher, 'ROOT', root), patch.object(launcher, 'RUNTIME', runtime):
                prepared = launcher.prepare_import()
            self.assertEqual(source.read_bytes(), before)
            self.assertEqual(json.loads(prepared.read_text(encoding='utf-8'))[0]['fio'], 'Шестаков Илья Александрович')

    def test_stop_does_not_kill_a_pid_with_different_creation_time(self):
        flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(15)'], creationflags=flags)
        try:
            record = launcher.identity(process.pid)
            self.assertIsNotNone(record)
            self.assertTrue(launcher.owned(record))
            wrong = {**record, 'created': str(record['created']) + '-wrong'}
            launcher.terminate(wrong)
            self.assertIsNone(process.poll())
            launcher.terminate(record)
            process.wait(timeout=5)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)


class StaticServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), static.handler(18083))
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = f'http://127.0.0.1:{cls.server.server_port}'

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def test_public_files_and_generated_api_configuration_work(self):
        with urllib.request.urlopen(self.base + '/config.js') as response:
            self.assertIn('http://127.0.0.1:18083/api', response.read().decode())
            self.assertEqual(response.headers['X-LR3-Project'], static.PROJECT_ID)
        with urllib.request.urlopen(self.base + '/') as response:
            self.assertEqual(response.status, 200)

    def test_secrets_and_repository_files_are_not_served(self):
        for path in ['/server/.env', '/.runtime/admin-credentials.json', '/students.json',
                     '/scripts/project.py', '/%2e%2e/server/.env', '/server%2f.env', '/server/']:
            with self.subTest(path=path), self.assertRaises(urllib.error.HTTPError) as caught:
                urllib.request.urlopen(self.base + path)
            self.assertEqual(caught.exception.code, 404)
            caught.exception.close()


if __name__ == '__main__':
    unittest.main()
