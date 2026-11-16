from chiyuri_ai.research import InternetResearchService, SearchResult


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
