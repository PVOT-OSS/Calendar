#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates app/src/main/assets/holidays/*.json from python-holidays (MIT).

    python3 -m venv /tmp/venv && /tmp/venv/bin/pip install holidays
    /tmp/venv/bin/python scripts/generate_holidays.py

National public holidays only, English names where the library has them. Regenerate yearly
to move the year window.
"""
import json
import os
import sys

import holidays

FIRST_YEAR, LAST_YEAR = 2025, 2032
COUNTRIES = [
    "AE", "AR", "AT", "AU", "BD", "BE", "BR", "CA", "CH", "CL", "CN", "CO", "CZ", "DE", "DK",
    "EG", "ES", "FI", "FR", "GB", "GR", "HK", "ID", "IE", "IL", "IN", "IT", "JP", "KE", "KR",
    "MX", "MY", "NG", "NL", "NO", "NZ", "PE", "PH", "PK", "PL", "PT", "RU", "SA", "SE", "SG",
    "TR", "TW", "US", "ZA",
]

out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "holidays")
os.makedirs(out_dir, exist_ok=True)
index = []
for code in COUNTRIES:
    cls = holidays.country_holidays(code)
    languages = getattr(cls, "supported_languages", ())
    language = next((l for l in ("en_US", "en_GB", "en") if l in languages), None)
    entries = []
    for year in range(FIRST_YEAR, LAST_YEAR + 1):
        days = holidays.country_holidays(code, years=year, language=language)
        for date, name in sorted(days.items()):
            for part in name.split("; "):
                entries.append([date.isoformat(), part])
    with open(os.path.join(out_dir, f"{code}.json"), "w", encoding="utf-8") as f:
        json.dump({"country": code, "holidays": entries}, f, ensure_ascii=False, separators=(",", ":"))
    index.append(code)
    print(code, len(entries), language, file=sys.stderr)

with open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8") as f:
    json.dump({"source": f"python-holidays {holidays.__version__} (MIT)", "first_year": FIRST_YEAR,
               "last_year": LAST_YEAR, "countries": index}, f, separators=(",", ":"))
