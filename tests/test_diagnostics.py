import json
import zipfile

from chiyuri_ai.diagnostics import DiagnosticCenter, redact_text


def test_redact_text_removes_common_credentials_and_home_path(monkeypatch, tmp_path):
    monkeypatch.setattr("pathlib.Path.home", lambda: tmp_path)
    text = (
        "api_key=sk-testsecretvalue123 "
        "Authorization: Bearer abcdefghijklmnop "
        "password=hunter2"
    )
    redacted = redact_text(text)
    assert "sk-testsecretvalue123" not in redacted
    assert "abcdefghijklmnop" not in redacted
    assert "hunter2" not in redacted


def test_diagnostic_bundle_contains_only_technical_metadata_and_logs(tmp_path):
    center = DiagnosticCenter(tmp_path / "Diagnostics")
    center.logger.error("provider failed api_token=very-secret-token-value")
    destination = center.export_bundle(tmp_path / "support.zip")

    with zipfile.ZipFile(destination) as archive:
        names = set(archive.namelist())
        report = json.loads(archive.read("diagnostics.json"))
        combined_logs = b"".join(
            archive.read(name) for name in names if name.startswith("logs/")
        ).decode("utf-8")
        assert "diagnostics.json" in names
        assert "README.txt" in names
        assert report["privacy"]["conversation_history_included"] is False
        assert report["privacy"]["provider_credentials_included"] is False
        assert "very-secret-token-value" not in combined_logs
        assert not any("conversation" in name.lower() for name in names)
        assert not any("models/" in name.lower() for name in names)


def test_diagnostic_report_has_bounded_package_inventory(tmp_path):
    center = DiagnosticCenter(tmp_path / "Diagnostics")
    report = center.build_report()
    assert report["schema_version"] == 1
    assert report["application"]["name"] == "Chiyuri AI"
    assert len(report["packages"]) <= 8
