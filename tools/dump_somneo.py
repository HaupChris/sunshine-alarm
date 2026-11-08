"""Read-only dump of all known Somneo API endpoints (GET only, nothing is changed).

Saves each response as JSON to samples/<endpoint>.json for use as test fixtures.

Usage: python tools/dump_somneo.py <ip>
"""

import json
import ssl
import sys
import time
import urllib.request
from pathlib import Path

ENDPOINTS = [
    "wusrd",          # sensors
    "wusts",          # device status
    "wulgt",          # light / nightlight
    "wudsk",          # sunset
    "wuply",          # player (radio/aux)
    "wualm",          # snooze etc.
    "wualm/aenvs",    # enabled alarms
    "wualm/aalms",    # alarm times
    "wualm/prfwu",    # wake-up profile of current alarm
    "wualm/alctr",    # running alarm control
    "files/lightthemes",
    "files/dusklightthemes",
    "files/wakeup",
    "files/winddowndusk",
    # not in pysomneo, probed for completeness
    "wusys",
    "wudev",
    "wurlx",          # relax / breathing (openHAB)
    "wutim",
    "wuwfi",
]

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def get(ip: str, path: str):
    url = f"https://{ip}/di/v1/products/1/{path}"
    with urllib.request.urlopen(url, context=CTX, timeout=5) as resp:
        return json.loads(resp.read())


def main() -> None:
    ip = sys.argv[1]
    out = Path(__file__).resolve().parent.parent / "samples"
    out.mkdir(exist_ok=True)
    for ep in ENDPOINTS:
        try:
            data = get(ip, ep)
        except Exception as exc:
            print(f"{ep:22s} FAIL  {exc}")
        else:
            (out / f"{ep.replace('/', '_')}.json").write_text(json.dumps(data, indent=2))
            print(f"{ep:22s} OK    {json.dumps(data)[:110]}")
        time.sleep(0.3)  # device has a tiny web server, be gentle


if __name__ == "__main__":
    main()
