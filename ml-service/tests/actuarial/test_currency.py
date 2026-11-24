from __future__ import annotations

import json
import shutil

import pandas as pd
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from actuarial import config
from actuarial.api import ActuarialService, _convert, get_service, router
from actuarial.pricing import PRICING_CONFIG_NAME, load_pricing_engine
from tests.actuarial.test_api import EXPERIENCED, YOUNG

RATE = config.DEFAULT_CURRENCY["rate_from_model"]


@pytest.fixture()
def setup(trained, tmp_path):
    """Private copy of the artifacts (administration writes to them)."""
    directory, _ = trained
    copy = tmp_path / "artifacts"
    shutil.copytree(directory, copy)
    app = FastAPI()
    app.include_router(router)
    service = ActuarialService(copy)
    app.dependency_overrides[get_service] = lambda: service
    return TestClient(app), copy


def _engine_premium(directory, profile: dict) -> float:
    """Pure premium computed directly by the engine, in the model currency (EUR)."""
    from actuarial.api import PolicyProfile

    row = PolicyProfile(**profile).to_row()
    return float(load_pricing_engine(directory).quote(pd.DataFrame([row]))["pure_premium"].iloc[0])


def test_default_currency_is_the_tunisian_dinar(setup) -> None:
    client, _ = setup
    currency = client.get("/api/actuarial/metadata").json()["currency"]
    assert currency["code"] == "TND"
    assert currency["modelCurrency"] == "EUR"
    assert currency["rateFromModel"] == pytest.approx(RATE)


def test_quote_amounts_are_converted_with_the_rate(setup) -> None:
    client, directory = setup
    body = client.post("/api/actuarial/quote", json={"profile": YOUNG}).json()

    assert body["currency"]["code"] == "TND"
    assert body["purePremium"] == pytest.approx(_engine_premium(directory, YOUNG) * RATE, abs=0.02)
    assert body["explanation"]["purePremium"] == pytest.approx(body["purePremium"], abs=0.02)
    # Unit-free quantities are not converted.
    assert 0 < body["claimProbability"] < 1
    assert body["commercialPremium"] == pytest.approx(
        body["purePremium"] * body["assumptions"]["commercialFactor"], abs=0.05
    )


def test_comparison_ratio_does_not_depend_on_the_currency(setup) -> None:
    client, _ = setup
    body = client.post("/api/actuarial/compare", json={"profileA": YOUNG, "profileB": EXPERIENCED}).json()
    assert body["currency"]["code"] == "TND"
    assert body["premiumRatio"] == pytest.approx(body["premiumA"] / body["premiumB"], rel=1e-3)


def test_stress_and_pricing_report_are_converted(setup) -> None:
    client, directory = setup
    raw = json.loads((directory / "pricing_report.json").read_text(encoding="utf-8"))
    report = client.get("/api/actuarial/reports/pricing").json()
    stress = client.post("/api/actuarial/stress", json={}).json()

    raw_premiums = raw["margin"]["portfolio"][0]["premiums"]
    assert report["margin"]["portfolio"][0]["premiums"] == pytest.approx(raw_premiums * RATE, abs=0.02)
    assert report["margin"]["portfolio"][0]["actual_loss_ratio"] == raw["margin"]["portfolio"][0]["actual_loss_ratio"]
    assert stress["portfolio"]["premiums"] == pytest.approx(raw_premiums * RATE, abs=0.02)
    assert stress["portfolio"]["actual_loss_ratio"] == pytest.approx(
        raw["margin"]["portfolio"][0]["actual_loss_ratio"], abs=1e-6
    )
    assert stress["currency"]["code"] == report["currency"]["code"] == "TND"


def test_reserving_report_keeps_its_own_currency(setup) -> None:
    client, directory = setup
    (directory / "reserving_report.json").write_text(json.dumps({"unit": "milliers de dollars US", "total": 100}))
    body = client.get("/api/actuarial/reports/reserving").json()
    assert body == {"unit": "milliers de dollars US", "total": 100}


def test_admin_can_switch_currency(setup) -> None:
    client, directory = setup
    euros = _engine_premium(directory, YOUNG)

    response = client.put(
        "/api/actuarial/admin/currency",
        json={"code": "EUR", "label": "euro", "rateFromModel": 1.0, "source": "unité des modèles"},
    )
    assert response.status_code == 200
    assert response.json()["previous"]["code"] == "TND"

    body = client.post("/api/actuarial/quote", json={"profile": YOUNG, "explain": False}).json()
    assert body["currency"]["code"] == "EUR"
    assert body["purePremium"] == pytest.approx(euros, abs=0.01)
    stored = json.loads((directory / PRICING_CONFIG_NAME).read_text(encoding="utf-8"))
    assert stored["currency"]["code"] == "EUR"


@pytest.mark.parametrize(
    "payload",
    [
        {"code": "dinar", "label": "x", "rateFromModel": 3.0},
        {"code": "TND", "label": "", "rateFromModel": 3.0},
        {"code": "TND", "label": "dinar", "rateFromModel": 0},
        {"code": "TND", "label": "dinar", "rateFromModel": 3.0, "extra": 1},
    ],
)
def test_invalid_currency_is_rejected(setup, payload) -> None:
    client, _ = setup
    assert client.put("/api/actuarial/admin/currency", json=payload).status_code == 422


def test_currency_is_known_before_training(tmp_path) -> None:
    app = FastAPI()
    app.include_router(router)
    service = ActuarialService(tmp_path)
    app.dependency_overrides[get_service] = lambda: service
    currency = TestClient(app).get("/api/actuarial/metadata").json()["currency"]
    assert currency["code"] == "TND"


def test_convert_only_touches_the_listed_keys() -> None:
    payload = {"premiums": 10, "ratio": 0.5, "rows": [{"premiums": 2, "count": 3, "flag": True}], "threshold": [1, 2]}
    converted = _convert(payload, frozenset({"premiums", "threshold"}), 3.0)
    assert converted == {"premiums": 30, "ratio": 0.5, "rows": [{"premiums": 6, "count": 3, "flag": True}], "threshold": [3, 6]}
    assert payload["premiums"] == 10  # input untouched
