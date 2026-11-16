# Connecting a local chat model

The desktop shell connects to a local llama.cpp server using its OpenAI-compatible HTTP endpoint. No cloud account is required.

1. Download a compatible llama.cpp Windows build from its official project release.
2. Select a GGUF model that fits available GPU/CPU memory. On an 8 GB GPU, begin with a small quantized model and leave VRAM headroom for Windows and the desktop.
3. Start the server from PowerShell, for example:

```powershell
.\llama-server.exe -m "D:\AI\Models\model.gguf" --host 127.0.0.1 --port 8080 -c 8192 -ngl 99
```

4. Start Chiyuri AI. The default endpoint is http://127.0.0.1:8080.
5. If the server uses another address, set the environment variable before launching:

```powershell
$env:CHIYURI_LLAMACPP_URL = "http://127.0.0.1:8081"
$env:CHIYURI_LLAMACPP_MODEL = "my-local-model"
python -m chiyuri_ai
```

The app checks /health and sends chat requests to /v1/chat/completions. It does not download models automatically. Keep the server bound to loopback unless you deliberately configure and secure remote access.

This is the first chat adapter. Vision, speech, image and video engines remain separate adapters and will be enabled only after their workflows pass hardware tests.
