# Connected AI providers

Chiyuri is the stable application identity; model providers are replaceable engines.
Conversation history, personality profiles and the avatar runtime belong to the application,
not to an individual provider.

## Current provider contract

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
- The current adapter supports text chat only. Vision, audio, image and video require
  capability-specific adapters and must not be advertised until implemented and tested.

## Configuration example

Create a provider record through `ProviderStore` with a name, HTTPS base URL (including the
provider's API prefix such as `/v1`), model identifier and optional environment-variable name.
Set the API key in the named environment variable outside the application. Set
`data_sharing_approved=True` only after deciding that conversation content may be sent to
that provider.

## Next integration work

1. Add a provider connection test and live health status to the Provider Manager.
2. Add vision, speech, image and video adapters with explicit payload and consent handling.
3. Route all provider traffic through the single application network broker when that process
   boundary is implemented.
4. Inject the same active personality and relevant memory context into every compatible mode.
