from __future__ import annotations

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from actuarial import schema as s
from actuarial.api import ActuarialService, get_service, router

YOUNG = {
    "driverAge": 20, "bonusMalus": 100, "vehicleAge": 2, "vehiclePower": 7,
    "density": 5000, "area": "E", "vehicleBrand": "B12", "vehicleGas": "Regular", "region": "R11",
}
EXPERIENCED = {
    "driverAge": 50, "bonusMalus": 50, "vehicleAge": 8, "vehiclePower": 6,
    "density": 50, "area": "A", "vehicleBrand": "B1", "vehicleGas": "Diesel", "region": "R24",
}


def _client(directory) -> TestClient:
    app = FastAPI()
    app.include_router(router)
    service = ActuarialService(directory)
    app.dependency_overrides[get_service] = lambda: service
    return TestClient(app)


@pytest.fixture(scope="module")
def client(trained) -> TestClient:
    directory, _ = trained
    return _client(directory)


# --- nominal ------------------------------------------------------------------
def test_status(client: TestClient) -> None:
    body = client.get("/api/actuarial/status").json()
    assert body["status"] == "ok"
    assert set(body["modelVersions"]) == {"frequency", "severity"}


def test_metadata_describes_every_field(client: TestClient) -> None:
    body = client.get("/api/actuarial/metadata").json()
    fields = {f["field"]: f for f in body["fields"]}
    assert len(fields) == len(s.FEATURES)
    assert fields["area"]["values"] == list(s.CATEGORY_DOMAINS[s.AREA])
    assert (fields["driverAge"]["min"], fields["driverAge"]["max"]) == s.NUMERIC_DOMAINS[s.DRIVER_AGE]


def test_quote_is_consistent(client: TestClient) -> None:
    response = client.post("/api/actuarial/quote", json={"profile": YOUNG})
    assert response.status_code == 200
    body = response.json()

    factor = body["assumptions"]["commercialFactor"]
    assert body["commercialPremium"] == pytest.approx(body["purePremium"] * factor, abs=0.02)
    assert body["premiumMargin"] == pytest.approx(
        body["commercialPremium"] - body["purePremium"], abs=0.02
    )
    assert 0 < body["claimProbability"] < 1

    explanation = body["explanation"]
    assert explanation["purePremium"] == pytest.approx(body["purePremium"], abs=0.01)
    assert len(explanation["contributions"]) == len(s.FEATURES)
    phis = [abs(c["phi"]) for c in explanation["contributions"]]
    assert phis == sorted(phis, reverse=True)
    assert {c["field"] for c in explanation["contributions"]} == set(YOUNG)


def test_quote_without_explanation_and_top(client: TestClient) -> None:
    plain = client.post("/api/actuarial/quote", json={"profile": YOUNG, "explain": False}).json()
    assert plain["explanation"] is None

    top = client.post("/api/actuarial/quote", json={"profile": YOUNG, "top": 3}).json()
    assert len(top["explanation"]["contributions"]) == 3


def test_custom_assumptions_change_only_the_commercial_premium(client: TestClient) -> None:
    default = client.post("/api/actuarial/quote", json={"profile": YOUNG, "explain": False}).json()
    custom = client.post(
        "/api/actuarial/quote",
        json={
            "profile": YOUNG,
            "explain": False,
            "assumptions": {"expenseRatio": 0.1, "profitRatio": 0.0, "safetyLoading": 0.0},
        },
    ).json()
    assert custom["purePremium"] == default["purePremium"]
    assert custom["commercialPremium"] == pytest.approx(custom["purePremium"] / 0.9, abs=0.02)


def test_longer_horizon_raises_claim_probability(client: TestClient) -> None:
    one = client.post("/api/actuarial/quote", json={"profile": YOUNG, "explain": False}).json()
    three = client.post(
        "/api/actuarial/quote", json={"profile": YOUNG, "explain": False, "years": 3}
    ).json()
    assert three["claimProbability"] > one["claimProbability"]
    assert three["purePremium"] == one["purePremium"]  # premiums stay annual


def test_values_above_training_caps_are_clipped_like_in_training(client: TestClient) -> None:
    capped = client.post(
        "/api/actuarial/quote", json={"profile": {**YOUNG, "vehicleAge": 20}, "explain": False}
    ).json()
    older = client.post(
        "/api/actuarial/quote", json={"profile": {**YOUNG, "vehicleAge": 35}, "explain": False}
    ).json()
    assert older["purePremium"] == capped["purePremium"]


def test_compare(client: TestClient) -> None:
    response = client.post(
        "/api/actuarial/compare", json={"profileA": YOUNG, "profileB": EXPERIENCED}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["premiumRatio"] == pytest.approx(body["premiumA"] / body["premiumB"], rel=1e-3)
    assert body["premiumRatio"] > 1


@pytest.mark.parametrize("name", ["frequency", "severity", "pricing", "explainability"])
def test_reports(client: TestClient, name: str) -> None:
    response = client.get(f"/api/actuarial/reports/{name}")
    assert response.status_code == 200
    assert response.json()


def test_model_cards(client: TestClient) -> None:
    cards = client.get("/api/actuarial/model-cards").json()
    assert set(cards) == {"frequency_model", "severity_model"}
    assert cards["frequency_model"]["data_fingerprint"]


# --- validation -----------------------------------------------------------------
@pytest.mark.parametrize(
    "payload",
    [
        {"profile": {**YOUNG, "area": "Z"}},
        {"profile": {**YOUNG, "region": "R99"}},
        {"profile": {**YOUNG, "driverAge": 16}},
        {"profile": {**YOUNG, "bonusMalus": 300}},
        {"profile": {k: v for k, v in YOUNG.items() if k != "density"}},
        {"profile": {**YOUNG, "unexpected": 1}},
        {"profile": YOUNG, "years": 0},
        {"profile": YOUNG, "top": 0},
        {"profile": YOUNG, "assumptions": {"expenseRatio": 0.7, "profitRatio": 0.3, "safetyLoading": 0}},
    ],
)
def test_invalid_requests_are_rejected(client: TestClient, payload) -> None:
    assert client.post("/api/actuarial/quote", json=payload).status_code == 422


def test_unknown_category_message_lists_allowed_values(client: TestClient) -> None:
    detail = client.post("/api/actuarial/quote", json={"profile": {**YOUNG, "area": "Z"}}).json()
    assert "A, B, C, D, E, F" in str(detail)


def test_unknown_report_is_rejected(client: TestClient) -> None:
    assert client.get("/api/actuarial/reports/unknown").status_code == 422


# --- availability ---------------------------------------------------------------
def test_missing_models_return_503(tmp_path) -> None:
    client = _client(tmp_path)
    for response in (
        client.get("/api/actuarial/status"),
        client.post("/api/actuarial/quote", json={"profile": YOUNG}),
        client.get("/api/actuarial/reports/pricing"),
        client.get("/api/actuarial/model-cards"),
    ):
        assert response.status_code == 503
        assert "actuarial.train" in response.json()["detail"]


def test_reload_picks_up_new_artifacts(trained) -> None:
    directory, _ = trained
    service = ActuarialService(directory)
    before = service.versions
    service.reload()
    assert service.versions == before


def test_main_application_exposes_the_actuarial_routes() -> None:
    from app import app

    paths = {route.path for route in app.routes}
    assert {"/api/actuarial/quote", "/api/actuarial/compare", "/api/actuarial/metadata"} <= paths


# --- reserving (Chain-Ladder on a user triangle) ----------------------------------
def _taylor_ashe_cumulative() -> list[list[float]]:
    from itertools import accumulate

    from tests.actuarial.test_reserving import TAYLOR_ASHE

    return [list(accumulate(row)) for row in TAYLOR_ASHE]


def test_chain_ladder_on_a_user_triangle(client: TestClient) -> None:
    response = client.post(
        "/api/actuarial/reserving/chain-ladder",
        json={"triangle": _taylor_ashe_cumulative(), "origins": list(range(1988, 1998))},
    )
    assert response.status_code == 200
    body = response.json()
    assert round(body["total"]["reserve"]) == 18_680_856
    assert round(body["total"]["mack_se"]) == 2_447_095
    assert body["origins"][0]["origin"] == 1988


def test_chain_ladder_accepts_rows_padded_with_nulls(client: TestClient) -> None:
    rows = _taylor_ashe_cumulative()
    padded = [row + [None] * (10 - len(row)) for row in rows]
    short = client.post("/api/actuarial/reserving/chain-ladder", json={"triangle": rows}).json()
    full = client.post("/api/actuarial/reserving/chain-ladder", json={"triangle": padded}).json()
    assert short["total"] == full["total"]


def test_chain_ladder_confidence_level_widens_the_interval(client: TestClient) -> None:
    rows = _taylor_ashe_cumulative()
    narrow = client.post("/api/actuarial/reserving/chain-ladder", json={"triangle": rows, "confidenceLevel": 0.8}).json()
    wide = client.post("/api/actuarial/reserving/chain-ladder", json={"triangle": rows, "confidenceLevel": 0.99}).json()
    assert wide["total"]["ci_low"] < narrow["total"]["ci_low"]
    assert wide["total"]["ci_high"] > narrow["total"]["ci_high"]


@pytest.mark.parametrize(
    ("payload", "message"),
    [
        ({"triangle": [[1.0, 2.0], [1.0]]}, None),  # fewer than 3 rows
        ({"triangle": [[1, 2, 3], [1, 2, 3], [1]]}, "après la diagonale"),
        ({"triangle": [[1, 2, 3, 4], [1, 2], [1]]}, "Ligne 1"),
        ({"triangle": [[1, 2, 3], [0, 2], [1]]}, "strictement positifs"),
        ({"triangle": [[1, 2, 3], [1, 2], [1]], "origins": [2001, 2002]}, "origins"),
        ({"triangle": [[1, 2, 3], [1, 2], [1]], "confidenceLevel": 1.0}, None),
    ],
)
def test_invalid_triangles_are_rejected_with_a_reason(client: TestClient, payload, message) -> None:
    response = client.post("/api/actuarial/reserving/chain-ladder", json=payload)
    assert response.status_code == 422
    if message:
        assert message in str(response.json()["detail"])


def test_chain_ladder_works_without_trained_models(tmp_path) -> None:
    response = _client(tmp_path).post(
        "/api/actuarial/reserving/chain-ladder", json={"triangle": _taylor_ashe_cumulative()}
    )
    assert response.status_code == 200


def test_missing_reserving_report_returns_503(client: TestClient) -> None:
    response = client.get("/api/actuarial/reports/reserving")
    assert response.status_code == 503
    assert "actuarial.reserving_report" in response.json()["detail"]
