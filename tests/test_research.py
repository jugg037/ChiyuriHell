import pytest

from chiyuri_ai.research import (
    InternetResearchService,
    SearchResult,
    SearxNGSearchProvider,
)


class FakeSearchProvider:
    provider_id = "test-search"

    def search(self, query, limit=5):
        return [SearchResult("Result", "https://example.com", query, self.provider_id)]


def test_research_service_labels_sources_and_untrusted_content():
    service = InternetResearchService([FakeSearchProvider()])
    results = service.search("latest local AI", limit=3)
    context = service.to_context(results)

    assert results[0].provider == "test-search"
    assert "https://example.com" in context
    assert "недоверенные данные" in context


def test_research_service_validates_query_and_limit():
    import pytest

    service = InternetResearchService([])
    with pytest.raises(ValueError):
        service.search(" ")
    with pytest.raises(ValueError):
        service.search("query", limit=50)


def test_searxng_parses_sources_and_discards_unsafe_links():
    from chiyuri_ai.research import SearxNGSearchProvider

    results = SearxNGSearchProvider._parse_results(
        {
            "results": [
                {"title": "Good", "url": "https://example.com/a", "content": "Snippet"},
                {"title": "Bad", "url": "javascript:alert(1)", "content": "No"},
                {"title": "No URL", "content": "No"},
            ]
        },
        5,
    )

    assert len(results) == 1
    assert results[0].title == "Good"
    assert results[0].provider == "searxng"


def test_searxng_requires_valid_endpoint():
    with pytest.raises(ValueError):
        SearxNGSearchProvider("file:///etc/passwd")
    with pytest.raises(ValueError):
        SearxNGSearchProvider("https://user:password@example.com")
    with pytest.raises(ValueError):
        SearxNGSearchProvider("http://example.com")


def test_searxng_query_validation():
    provider = SearxNGSearchProvider("https://search.example")
    with pytest.raises(ValueError):
        provider.search(" ")
    with pytest.raises(ValueError):
        provider.search("query", limit=99)
