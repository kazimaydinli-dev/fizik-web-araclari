#!/usr/bin/env python3
"""Refresh only public film-page metadata, keeping existing records and watched IDs.
Usage: python film-import.py --max-pages 10
Never downloads or embeds videos.
"""
import argparse
import hashlib
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import urljoin, urlsplit
import requests
from bs4 import BeautifulSoup

HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "films-catalog.js"
UA = {"User-Agent": "Mozilla/5.0 (compatible; PersonalFilmCatalogue/1.0; public-page-metadata)",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.7"}
SOURCES = {
    "FullHDFilmizlesene": {
        "root": "https://www.fullhdfilmizlesene.now",
        "categories": {
            "Aksiyon": "/filmizle/aksiyon-filmleri",
            "Bilim Kurgu": "/filmizle/bilim-kurgu-filmleri",
            "Animasyon": "/filmizle/animasyon-filmleri-izle",
            "Fantastik": "/filmizle/fantastik-filmler",
            "Macera": "/filmizle/macera-filmleri-izle-1",
            "Gerilim": "/filmizle/gerilim-filmleri",
            "Aile": "/filmizle/aile-filmleri"
        },
    },
    "Film Cehennemi": {
        "root": "https://www.hdfilmcehennemi.nl",
        "categories": {
            "Aksiyon": "/tur/aksiyon-filmleri-izleyin-9/",
            "Bilim Kurgu": "/tur/bilim-kurgu-filmlerini-izleyin-9/",
            "Animasyon": "/tur/animasyon-filmlerini-izleyin-5/",
            "Fantastik": "/tur/fantastik-filmlerini-izleyin-4/",
            "Macera": "/tur/macera-filmlerini-izleyin-5/",
            "Gerilim": "/tur/gerilim-filmlerini-izle-5/",
            "Aile": "/tur/aile-filmleri-izleyin-11/"
        },
    }
}
BLOCKED_PATHS = ("tur", "category", "yil", "film-arsivi", "filmler", "diziler",
                "page", "etiket", "tag", "oyuncu", "yonetmen", "filmizle",
                "belgesel", "belgeseldizi", "serifilmlerim", "iletisim",
                "author", "wp-content", "feed", "arama", "blog")

def load_old():
    if not OUTPUT.exists():
        return []
    text = OUTPUT.read_text(encoding="utf-8")
    m = re.search(r"window\.EXTRA_FILMS\s*=\s*(\[.*?\])\s*;", text, re.S)
    if not m:
        raise RuntimeError("Eski katalog JSON biçiminde değil; korunuyor.")
    return json.loads(m.group(1))

def normalized(url):
    p = urlsplit(url)
    return (p.netloc.lower().removeprefix("www.") + p.path.rstrip("/")).lower()

def plausible_link(source, link):
    parts = urlsplit(link)
    host = parts.netloc.lower().removeprefix("www.")
    expected = SOURCES[source]["root"].split("//", 1)[1].removeprefix("www.")
    if host != expected:
        return False
    path = parts.path.strip("/")
    segments = path.split("/")
    if len(path) < 5 or len(path) > 180 or any(x in path for x in ("?", "#")):
        return False
    if source == "FullHDFilmizlesene":
        return len(segments) == 2 and segments[0] == "film" and len(segments[1]) > 2
    return len(segments) == 1 and not segments[0].lower().startswith(BLOCKED_PATHS) and "." not in segments[0]

def title_from(anchor, card):
    for val in (anchor.get("title"), anchor.get("aria-label"),
                (anchor.find("img") or {}).get("alt") if anchor.find("img") else "",
                anchor.get_text(" ", strip=True),
                card.find(["h2", "h3", "h4"]).get_text(" ", strip=True)
                if card and card.find(["h2", "h3", "h4"]) else ""):
        cleaned = re.sub(r"\s+", " ", str(val or "")).strip()
        cleaned = re.sub(r"\s+(?:izle|film izle)$", "", cleaned, flags=re.I)
        if 3 <= len(cleaned) <= 160 and cleaned.lower() not in ("film izle", "izle", "hd izle", "play"):
            return cleaned
    return ""

def parse_listing(source, genre, url, text):
    soup = BeautifulSoup(text, "html.parser")
    body = soup.select_one("main") or soup.select_one("#main") or soup.select_one(".content") or soup
    found = {}
    for a in body.find_all("a", href=True):
        dest = urljoin(url, a["href"]).split("#", 1)[0].split("?", 1)[0]
        if not plausible_link(source, dest):
            continue
        card = a
        for _ in range(4):
            parent = card.parent
            if not parent or parent.name in ("main", "body", "html"):
                break
            card = parent
            if re.search(r"(movie|film|poster|item|card|box|post|video|entry)", " ".join(card.get("class", [])), re.I):
                break
        title = title_from(a, card)
        if not title:
            continue
        snippet = card.get_text(" ", strip=True)[:700] if card else ""
        year_match = re.search(r"\b(19\d{2}|20[0-3]\d)\b", snippet)
        year = int(year_match.group(1)) if year_match else None
        img = (card.find("img") if card else None)
        cover = ""
        if img:
            cover = img.get("data-src") or img.get("data-lazy-src") or img.get("src") or ""
            cover = urljoin(url, cover) if cover and not cover.startswith("data:") else ""
        slug = urlsplit(dest).path.strip("/").split("/")[-1]
        film = {
            "id": "film-" + hashlib.sha1((source + ":" + slug).encode()).hexdigest()[:15],
            "title": title,
            "source": source,
            "channel": source,
            "category": genre,
            "genres": [genre],
            "type": "Film",
            "url": dest,
            "cover": cover,
        }
        if year:
            film["year"] = year
        key = normalized(dest)
        if key in found:
            continue
        found[key] = film
    return list(found.values())

def refresh(max_pages):
    old = load_old()
    old_by_url = {normalized(x["url"]): x for x in old if x.get("url")}
    merged = dict(old_by_url)
    session = requests.Session()
    session.headers.update(UA)
    total_new, total_pages = 0, 0
    for source, conf in SOURCES.items():
        for genre, path in conf["categories"].items():
            previous = None
            for page in range(1, max_pages + 1):
                base = conf["root"] + path
                if source == "FullHDFilmizlesene":
                    url = base if page == 1 else base.rstrip("/") + "/" + str(page)
                else:
                    url = base if page == 1 else base.rstrip("/") + "/page/" + str(page) + "/"
                try:
                    response = session.get(url, timeout=18)
                    response.raise_for_status()
                    films = parse_listing(source, genre, url, response.text)
                except (requests.RequestException, ValueError) as exc:
                    print(f"[Uyarı] {source} / {genre} / sayfa {page}: {exc}", flush=True)
                    break
                if not films:
                    print(f"[Uyarı] Film bulunamadı: {source} / {genre} / {page}")
                    break
                fingerprint = tuple(normalized(x["url"]) for x in films[:8])
                if fingerprint == previous:
                    break
                previous = fingerprint
                total_pages += 1
                for film in films:
                    key = normalized(film["url"])
                    if key in merged:
                        old_film = merged[key]
                        genres = list(dict.fromkeys(old_film.get("genres", [old_film.get("category", genre)]) + [genre]))
                        old_film["genres"] = genres
                        # Do not change the ID: watch marks use it.
                        if not old_film.get("year") and film.get("year"):
                            old_film["year"] = film["year"]
                        if not old_film.get("cover") and film.get("cover"):
                            old_film["cover"] = film["cover"]
                    else:
                        merged[key] = film
                        total_new += 1
                print(f"{source} / {genre} / sayfa {page}: {len(films)} kayıt", flush=True)
                time.sleep(0.8)
    if not total_pages:
        print("Hiçbir kaynağa erişilemedi. Önceki katalog değiştirilmiyor.", file=sys.stderr)
        return 1
    items = sorted(merged.values(), key=lambda x: (x.get("source", ""), x.get("title", "")))
    if len(items) > 20000:
        print("Katalog güvenlik sınırını aştı; koruma gereği yazılmadı", file=sys.stderr)
        return 1
    OUTPUT.write_text("/* Public film-page metadata; generated by film-import.py; no video files. */\n"
                      "window.EXTRA_FILMS=" + json.dumps(items, ensure_ascii=False, separators=(",", ":")) + ";\n",
                      encoding="utf-8")
    print(f"OK: {total_pages} sayfa, {total_new} yeni film, toplam {len(items)} içerik.")
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-pages", type=int, default=10, help="Each source genre pagination cap 1–40")
    args = parser.parse_args()
    sys.exit(refresh(max(1, min(40, args.max_pages))))
