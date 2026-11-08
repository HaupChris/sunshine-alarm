"""Find Philips Somneo devices on the local network.

1. SSDP multicast search (UPnP), what the official app / Home Assistant use.
2. Fallback: probe every host of a /24 subnet for https://<ip>/upnp/description.xml.

Usage: python tools/discover_somneo.py [subnet-prefix, e.g. 192.168.178]
"""

import concurrent.futures
import socket
import ssl
import sys
import urllib.request

SSDP_ADDR = ("239.255.255.250", 1900)
SSDP_MSG = (
    "M-SEARCH * HTTP/1.1\r\n"
    "HOST: 239.255.255.250:1900\r\n"
    'MAN: "ssdp:discover"\r\n'
    "MX: 3\r\n"
    "ST: ssdp:all\r\n\r\n"
).encode()

# Somneo uses a self-signed certificate
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def fetch_description(ip: str, timeout: float = 2.0) -> str | None:
    try:
        with urllib.request.urlopen(
            f"https://{ip}/upnp/description.xml", context=CTX, timeout=timeout
        ) as resp:
            return resp.read().decode(errors="replace")
    except Exception:
        return None


def is_somneo(xml: str | None) -> bool:
    return bool(xml) and ("somneo" in xml.lower() or "HF367" in xml)


def ssdp_search(timeout: float = 5.0) -> set[str]:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
    sock.settimeout(timeout)
    sock.sendto(SSDP_MSG, SSDP_ADDR)
    hits: set[str] = set()
    try:
        while True:
            data, (ip, _) = sock.recvfrom(4096)
            text = data.decode(errors="replace").lower()
            if "somneo" in text or "philips" in text or "/upnp/description.xml" in text:
                hits.add(ip)
    except socket.timeout:
        pass
    return hits


def subnet_scan(prefix: str) -> list[str]:
    ips = [f"{prefix}.{i}" for i in range(1, 255)]
    with concurrent.futures.ThreadPoolExecutor(64) as pool:
        results = pool.map(lambda ip: (ip, fetch_description(ip, 1.5)), ips)
    return [ip for ip, xml in results if is_somneo(xml)]


def main() -> None:
    prefix = sys.argv[1] if len(sys.argv) > 1 else "192.168.178"

    print("SSDP search ...")
    found = [ip for ip in ssdp_search() if is_somneo(fetch_description(ip))]

    if not found:
        print(f"Nothing via SSDP, scanning {prefix}.0/24 ...")
        found = subnet_scan(prefix)

    if not found:
        print("No Somneo found.")
        return
    for ip in found:
        xml = fetch_description(ip) or ""
        print(f"\nSomneo at {ip}")
        for tag in ("friendlyName", "modelName", "modelNumber", "serialNumber"):
            if f"<{tag}>" in xml:
                print(f"  {tag}: {xml.split(f'<{tag}>')[1].split('<')[0]}")


if __name__ == "__main__":
    main()
