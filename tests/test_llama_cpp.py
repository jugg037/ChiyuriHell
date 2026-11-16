import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

from chiyuri_ai.llama_cpp import LlamaCppServerAdapter
from chiyuri_ai.models import Capability, GenerationRequest


class FakeLlamaHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')

    def do_POST(self):
        size = int(self.headers["Content-Length"])
        json.loads(self.rfile.read(size))
        response = json.dumps({
            "choices": [{"message": {"content": "Локальный ответ"}}],
            "usage": {"total_tokens": 4},
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format, *args):
        return


def test_llama_cpp_adapter_health_and_chat():
    server = ThreadingHTTPServer(("127.0.0.1", 0), FakeLlamaHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        adapter = LlamaCppServerAdapter(
            base_url=f"http://127.0.0.1:{server.server_port}",
            model_id="test-model",
        )
        assert adapter.is_available()
        result = adapter.generate(GenerationRequest(Capability.CHAT, "Привет"))
        assert result.model_id == "test-model"
        assert result.output == "Локальный ответ"
    finally:
        server.shutdown()
        thread.join(timeout=2)
        server.server_close()
