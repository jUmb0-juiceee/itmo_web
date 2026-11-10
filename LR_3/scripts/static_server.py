"""Loopback-only static server. Never exposes the repository or local secrets."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit
import argparse
import hashlib
import json
import mimetypes

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = frozenset({
    'index.html', 'login.html', 'form.html', 'dossier.html', 'style.css',
    'api.js', 'main.js', 'auth.js', 'login.js', 'config.js',
    'cat.png', 'forest.svg', 'stars.svg',
})
PROJECT_ID = hashlib.sha256(str(ROOT).encode()).hexdigest()[:16]


def handler(api_port):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.serve(True)

        def do_HEAD(self):
            self.serve(False)

        def serve(self, body):
            name = unquote(urlsplit(self.path).path).removeprefix('/') or 'index.html'
            if name == 'favicon.ico':
                name = 'cat.png'
            if name not in PUBLIC:
                self.send_error(404)
                return
            if name == 'config.js':
                data = ('window.APP_CONFIG = Object.freeze(' + json.dumps({
                    'apiBaseUrl': f'http://127.0.0.1:{api_port}/api'
                }) + ');\n').encode()
            else:
                data = (ROOT / name).read_bytes()
            content_type = 'application/javascript' if name.endswith('.js') else mimetypes.guess_type(name)[0] or 'application/octet-stream'
            self.send_response(200)
            self.send_header('Content-Type', content_type + ('; charset=utf-8' if name.endswith(('.html', '.css', '.js', '.svg')) else ''))
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('X-LR3-Project', PROJECT_ID)
            self.end_headers()
            if body:
                self.wfile.write(data)

    return Handler


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--api-port', type=int, required=True)
    args = parser.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', args.port), handler(args.api_port))
    print(f'LR_3 frontend: http://127.0.0.1:{args.port}', flush=True)
    server.serve_forever()
