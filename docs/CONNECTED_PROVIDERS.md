# Connected AI providers

Chiyuri is the stable application identity; model providers are replaceable engines.
Conversation history, personality profiles and the avatar runtime belong to the application,
not to an individual provider.

## Cloud chat providers

- Every adapter advertises its supported capabilities and a stable model identifier.
- The existing llama.cpp adapter remains the local chat option.
- `OpenAICompatibleAdapter` connects to explicitly configured OpenAI-compatible HTTPS chat
  endpoints. It can be used with compatible hosted APIs; it does not assume every vendor has
  identical model features.
- Provider settings are stored in the private user data directory. API-key values are not
  stored in provider JSON; only an environment-variable name is stored.
- A provider is unavailable until enabled, its configured key exists (if required), and the
  user has explicitly approved sending conversation data to that provider.
- Cloud endpoints must use HTTPS on port 443. DNS results are validated and pinned to public
  IP addresses for each request; redirects are rejected.
- The current cloud adapter supports text chat only. Vision, audio, image and video require
  capability-specific adapters and must not be advertised until implemented and tested.

## Hugging Face model library

- Hugging Face is a model discovery and download source, not a mandatory cloud inference
  provider. Public catalog search does not require an account.
- The in-app catalog defaults to the `huihui-ai` author namespace, while allowing users to
  search all authors.
- The first downloader supports explicitly selected GGUF files only. GGUF is the format used
  by the current llama.cpp integration; other formats must not be presented as ready-to-run.
- Model files are stored under the private `CHIYURI_HOME/Models` directory, outside Git.
- Downloads are user-confirmed and never run model code or automatically start a downloaded
  artifact. Model cards, licences, file sizes and runtime compatibility should be reviewed.
- A downloaded file is not yet an active model: the next integration step is an in-app
  llama.cpp server lifecycle manager that loads a selected GGUF and connects it to the router.

## Configuration and privacy

Create a cloud provider record through `ProviderStore` with a name, HTTPS base URL (including
the provider's API prefix such as `/v1`), model identifier and optional environment-variable name.
Set the API key in the named environment variable outside the application. Set
`data_sharing_approved=True` only after deciding that conversation content may be sent to
that provider.
