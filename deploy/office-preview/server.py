from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import subprocess
import tempfile

MAX_INPUT = 20 * 1024 * 1024
MAX_OUTPUT = 100 * 1024 * 1024
SAFE_NAME = re.compile(r"^[A-Za-z0-9._-]{1,120}$")
ALLOWED = {".docx", ".xlsx", ".pptx"}


class Handler(BaseHTTPRequestHandler):
    server_version = "cgc-office-preview/1"

    def do_GET(self):
        if self.path != "/health":
            self.send_error(404)
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def do_POST(self):
        if self.path != "/convert":
            self.send_error(404)
            return
        name = self.headers.get("X-File-Name", "")
        try:
            size = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            size = -1
        suffix = Path(name).suffix.lower()
        if not SAFE_NAME.fullmatch(name) or suffix not in ALLOWED or not 0 < size <= MAX_INPUT:
            self.send_error(400, "invalid input")
            return
        content = self.rfile.read(size)
        if len(content) != size:
            self.send_error(400, "incomplete input")
            return
        try:
            with tempfile.TemporaryDirectory(dir="/tmp") as tmp:
                root = Path(tmp)
                source = root / ("source" + suffix)
                output = root / "output"
                output.mkdir()
                source.write_bytes(content)
                subprocess.run(
                    [
                        "soffice", "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                        "--nolockcheck", "--norestore", "--convert-to", "pdf", "--outdir", str(output),
                        str(source),
                    ],
                    check=True,
                    timeout=55,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    env={"HOME": tmp, "TMPDIR": tmp, "PATH": "/usr/bin:/bin"},
                )
                pdf = output / "source.pdf"
                result = pdf.read_bytes()
                if not result.startswith(b"%PDF-") or len(result) > MAX_OUTPUT:
                    raise ValueError("invalid output")
                self.send_response(200)
                self.send_header("Content-Type", "application/pdf")
                self.send_header("Content-Length", str(len(result)))
                self.end_headers()
                self.wfile.write(result)
        except subprocess.TimeoutExpired:
            self.send_error(504, "conversion timeout")
        except Exception:
            self.send_error(422, "conversion failed")

    def log_message(self, format, *args):
        return


ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
