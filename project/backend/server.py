#!/usr/bin/python3
"""Dependency-free loopback backend for the Carzonrent local QA environment."""

from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import socket


ROOT = Path(__file__).resolve().parent
TEMPLATE = (ROOT / "templates" / "index.html").read_text(encoding="utf-8")
STYLESHEET = (ROOT / "static" / "style.css").read_bytes()
BUILD_VERSION = os.getenv("BUILD_VERSION", "dev")


class QaRequestHandler(BaseHTTPRequestHandler):
    server_version = "Carzonrent-QA-Backend"
    sys_version = ""

    def _send(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        dynamic = content_type.startswith(("text/html", "application/json"))
        self.send_header("Cache-Control", "no-store" if dynamic else "public, max-age=300")
        self.send_header("X-Carzonrent-Backend", "qa-dashboard:8085")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self) -> None:
        self.do_GET()

    def do_GET(self) -> None:
        path = self.path.split("?", 1)[0]
        if path == "/health":
            payload = json.dumps({
                "status": "UP",
                "service": "carzonrent-qa-backend",
                "port": 8085,
                "build": BUILD_VERSION,
            }).encode("utf-8")
            self._send(200, "application/json; charset=utf-8", payload)
            return

        if path == "/style.css":
            self._send(200, "text/css; charset=utf-8", STYLESHEET)
            return

        if path == "/":
            rendered = (TEMPLATE
                .replace("{{HOSTNAME}}", socket.gethostname())
                .replace("{{CURRENT_TIME}}", datetime.now().astimezone().strftime("%d %b %Y, %H:%M:%S %Z"))
                .replace("{{BUILD_VERSION}}", BUILD_VERSION))
            self._send(200, "text/html; charset=utf-8", rendered.encode("utf-8"))
            return

        body = b'{"status":"NOT_FOUND","message":"The requested QA resource does not exist."}'
        self._send(404, "application/json; charset=utf-8", body)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"backend client={self.client_address[0]} {fmt % args}", flush=True)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8085), QaRequestHandler)
    server.daemon_threads = True
    print("Carzonrent QA backend listening on 127.0.0.1:8085", flush=True)
    server.serve_forever()
