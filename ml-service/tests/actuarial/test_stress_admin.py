from __future__ import annotations

import json
import shutil

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from actuarial.api import ActuarialService, get_service, router
from actuarial.pricing import PRICING_CONFIG_NAME
from actuarial.stress import Shock, stress_margins
from tests.actuarial.test_api import YOUNG

SEGMENTS = [
    {"segment": "A", "policies": 10, "premiums": 1000.0, "expected_claims": 680.0, "actual_claims": 500.0},
    {"segment": "B", "policies": 5, "premiums": 500.0, "expected_claims": 340.0, "actual_claims": 450.0},
]
PORTFOLIO = {"segment": "portfolio", "policies": 15, "premiums": 1500.0, "expected_claims": 1020.0, "actual_claims": 950.0}


# --- stress arithmetic -------------------------------------------------------------
def test_no_shock_keeps_the_realised_margins() -> None:
    result = stress_margins(SEGMENTS, PORTFOLIO, Shock())
    assert result["portfolio"]["stressed_claims"] == 950.0
    assert result["portfolio"]["stressed_margin"] == result["portfolio"]["actual_margin"] == 550.0
    assert result["segments_in_loss"] == 0


def test_stress_starts_from_realised_claims_so_segments_differ() -> None:
    """Same expected S/P (68 %) in both segments, different realised S/P."""
    result = stress_margins(SEGMENTS, PORTFOLIO, Shock())
    a, b = result["segments"]
    assert a["expected_loss_ratio"] == b["expected_loss_ratio"] == pytest.approx(0.68)
    assert a["break_even_shock"] == pytest.approx(1.0)  # 1000 / 500 - 1
    assert b["break_even_shock"] == pytest.approx(500 / 450 - 1, abs=1e-6)  # rounded to 6 decimals


def test_frequency_and_severity_shocks_compound() -> None:
    result = stress_margins(SEGMENTS, PORTFOLIO, Shock(frequency=0.2, severity=0.1))
    assert result["shock"]["claims_multiplier"] == pytest.approx(1.32)
    a, b = result["segments"]
    assert a["stressed_claims"] == pytest.approx(660.0)
    assert a["stressed_loss_ratio"] == pytest.approx(0.66)
    assert b["stressed_margin"] < 0  # 450 x 1.32 = 594 > 500
    assert result["segments_in_loss"] == 1


def test_break_even_shock_brings_the_loss_ratio_to_one() -> None:
    row = stress_margins(SEGMENTS, PORTFOLIO, Shock())["segments"][1]
    at_break_even = stress_margins(SEGMENTS, PORTFOLIO, Shock(frequency=row["break_even_shock"]))
    assert at_break_even["segments"][1]["stressed_loss_ratio"] == pytest.approx(1.0)
    assert at_break_even["segments"][1]["stressed_margin"] == pytest.approx(0.0, abs=1e-6)


@pytest.mark.parametrize("kwargs", [{"frequency": -0.6}, {"severity": 2.5}])
def test_unrealistic_shocks_are_rejected(kwargs) -> None:
    with pytest.raises(ValueError, match="choc"):
        Shock(**kwargs)


# --- API: stress and administration ---------------------------------------------------
@pytest.fixture()
def admin_client(trained, tmp_path):
    """Administration writes files: work on a private copy of the artifacts."""
    directory, _ = trained
    copy = tmp_path / "artifacts"
    shutil.copytree(directory, copy)
    app = FastAPI()
    app.include_router(router)
    service = ActuarialService(copy)
    app.dependency_overrides[get_service] = lambda: service
    return TestClient(app), copy


def test_stress_endpoint(admin_client) -> None:
    client, _ = admin_client
    body = client.post(
        "/api/actuarial/stress", json={"frequencyShock": 0.25, "severityShock": 0.0, "segmentation": "by_area"}
    ).json()
    assert body["shock"]["claims_multiplier"] == pytest.approx(1.25)
    assert {row["segment"] for row in body["segments"]} == set("ABCDEF")
    portfolio = body["portfolio"]
    assert portfolio["stressed_claims"] == pytest.approx(portfolio["actual_claims"] * 1.25, rel=1e-6)


@pytest.mark.parametrize(
    "payload",
    [{"frequencyShock": 3.0}, {"severityShock": -0.9}, {"segmentation": "by_color"}, {"unknown": 1}],
)
def test_stress_validation(admin_client, payload) -> None:
    client, _ = admin_client
    assert client.post("/api/actuarial/stress", json=payload).status_code == 422


def test_admin_updates_assumptions_persistently(admin_client) -> None:
    client, directory = admin_client
    before = client.post("/api/actuarial/quote", json={"profile": YOUNG, "explain": False}).json()

    response = client.put(
        "/api/actuarial/admin/assumptions",
        json={"expenseRatio": 0.2, "profitRatio": 0.0, "safetyLoading": 0.0},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["previous"]["expense_ratio"] == pytest.approx(0.25)
    assert body["current"]["commercial_factor"] == pytest.approx(1.25)

    after = client.post("/api/actuarial/quote", json={"profile": YOUNG, "explain": False}).json()
    assert after["purePremium"] == before["purePremium"]  # models untouched
    assert after["commercialPremium"] == pytest.approx(after["purePremium"] * 1.25, abs=0.02)

    stored = json.loads((directory / PRICING_CONFIG_NAME).read_text(encoding="utf-8"))
    assert stored["assumptions"]["expense_ratio"] == pytest.approx(0.2)
    assert not (directory / (PRICING_CONFIG_NAME + ".tmp")).exists()


def test_admin_rejects_invalid_assumptions(admin_client) -> None:
    client, directory = admin_client
    original = (directory / PRICING_CONFIG_NAME).read_text(encoding="utf-8")
    response = client.put(
        "/api/actuarial/admin/assumptions",
        json={"expenseRatio": 0.7, "profitRatio": 0.3, "safetyLoading": 0.0},
    )
    assert response.status_code == 422
    assert (directory / PRICING_CONFIG_NAME).read_text(encoding="utf-8") == original


def test_admin_reload(admin_client) -> None:
    client, _ = admin_client
    response = client.post("/api/actuarial/admin/reload")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_admin_and_stress_without_models_return_503(tmp_path) -> None:
    app = FastAPI()
    app.include_router(router)
    service = ActuarialService(tmp_path)
    app.dependency_overrides[get_service] = lambda: service
    client = TestClient(app)

    assert client.post("/api/actuarial/stress", json={}).status_code == 503
    assert client.post("/api/actuarial/admin/reload").status_code == 503
    assert client.put(
        "/api/actuarial/admin/assumptions",
        json={"expenseRatio": 0.2, "profitRatio": 0.0, "safetyLoading": 0.0},
    ).status_code == 503
