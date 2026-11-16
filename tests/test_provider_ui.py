from chiyuri_ai.providers import ProviderConfig, ProviderStore


def test_provider_store_is_created_under_private_storage(tmp_path):
    store = ProviderStore(tmp_path / "Library" / "Providers")
    config = ProviderConfig(
        provider_id="example",
        name="Example",
        base_url="https://api.example.com/v1",
        model="example-chat",
    )
    store.save(config)
    assert store.load("example") == config
