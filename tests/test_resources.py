from chiyuri_ai.resources import ResourceMonitor


def test_resource_monitor_returns_valid_host_memory():
    snapshot = ResourceMonitor().snapshot()

    assert snapshot.cpu_percent >= 0
    assert snapshot.ram_total_bytes > 0
    assert 0 <= snapshot.ram_available_bytes <= snapshot.ram_total_bytes
    assert 0 <= snapshot.ram_used_percent <= 100
