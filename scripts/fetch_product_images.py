#!/usr/bin/env python3
"""Fetch freely-licensed food photos from Wikimedia Commons for seed products."""
import json, os, re, sys, urllib.parse, urllib.request

OUT = os.path.expanduser(
    "~/Development/KMP/POSMultiplatform/composeApp/src/commonMain/composeResources/drawable"
)
UA = {"User-Agent": "POSMultiplatform-demo/1.0 (contact: local-dev)"}

# key -> search terms (tried in order)
PRODUCTS = {
    "nasi_goreng": ["nasi goreng", "fried rice Indonesia"],
    "mie_ayam": ["mie ayam", "chicken noodle bowl"],
    "ayam_geprek": ["ayam geprek", "fried chicken plate"],
    "nasi_uduk": ["nasi uduk", "coconut rice"],
    "es_teh_manis": ["es teh", "iced tea glass"],
    "es_jeruk": ["orange juice glass"],
    "air_mineral": ["bottled water", "mineral water bottle"],
    "teh_botol": ["bottled tea", "iced tea bottle"],
    "kopi_susu": ["kopi susu", "iced coffee milk"],
    "americano": ["caffè americano", "americano coffee cup"],
    "cappuccino": ["cappuccino cup"],
    "keripik_singkong": ["cassava chips", "potato chips bowl"],
    "pisang_goreng": ["pisang goreng", "fried banana"],
    "donat_gula": ["donut glazed", "doughnut"],
}

OK_LICENSE = re.compile(r"(cc0|public domain|cc-by|cc by)", re.I)


def api(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def search(term):
    q = urllib.parse.quote(f"filetype:bitmap {term}")
    url = (
        "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
        f"&gsrsearch={q}&gsrnamespace=6&gsrlimit=12&prop=imageinfo"
        "&iiprop=url|extmetadata|mime&iiurlwidth=900&format=json"
    )
    data = api(url)
    pages = data.get("query", {}).get("pages", {})
    out = []
    for p in pages.values():
        ii = (p.get("imageinfo") or [{}])[0]
        if ii.get("mime") not in ("image/jpeg", "image/png"):
            continue
        meta = ii.get("extmetadata", {})
        lic = meta.get("LicenseShortName", {}).get("value", "")
        out.append((lic, ii.get("thumburl") or ii.get("url"), p.get("title")))
    return out


def pick(cands):
    def rank(c):
        lic = c[0].lower()
        if "cc0" in lic or "public domain" in lic:
            return 0
        if "cc" in lic and "by" in lic:
            return 1
        return 2

    cands = [c for c in cands if OK_LICENSE.search(c[0])]
    if not cands:
        return None
    cands.sort(key=rank)
    return cands[0]


def main():
    os.makedirs(OUT, exist_ok=True)
    credits = []
    for key, terms in PRODUCTS.items():
        got = False
        for term in terms:
            try:
                cands = search(term)
            except Exception as e:  # noqa
                print(f"{key}: search error {e}")
                continue
            best = pick(cands)
            if not best:
                continue
            lic, url, title = best
            try:
                req = urllib.request.Request(url, headers=UA)
                with urllib.request.urlopen(req, timeout=30) as r:
                    raw = r.read()
            except Exception as e:  # noqa
                print(f"{key}: download error {e}")
                continue
            ext = ".png" if url.lower().endswith(".png") else ".jpg"
            path = os.path.join(OUT, key + ext)
            with open(path, "wb") as f:
                f.write(raw)
            credits.append((key, title, lic, url))
            print(f"{key}: OK [{lic}] {title} ({len(raw)//1024}KB)")
            got = True
            break
        if not got:
            print(f"{key}: NOT FOUND")
    with open(os.path.join(OUT, "CREDITS.txt"), "w") as f:
        for c in credits:
            f.write("\t".join(c) + "\n")
    print(f"done: {len(credits)}/{len(PRODUCTS)}")


if __name__ == "__main__":
    main()
