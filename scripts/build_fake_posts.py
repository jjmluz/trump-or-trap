#!/usr/bin/env python3
"""Build the fake-post deck for Trump or Trap and merge it with the real deck.

Usage: build_fake_posts.py <data_dir>

Reads <data_dir>/fakes/*.txt, one "YEAR|text" post per line ("#" lines are comments), and
writes to
<data_dir>/out:
  fake_deck.json        the validated fake posts
  game_deck.json        real and fake cards, shuffled
  fake_report.json      overlap failures and real vs fake style per era

Run build_real_posts.py first.
"""
import argparse
import collections
import html
import json
import pathlib
import random
import re
import statistics

import pyarrow.parquet as pq

from build_real_posts import ERA_QUOTAS, SEED, clean_text, deck_reject_reason, era_of

SHINGLE_WORDS = 5
MAX_REAL_OVERLAP = 0.35
MAX_FAKE_OVERLAP = 0.5


def words(text):
    return re.findall(r"[a-z0-9']+", text.lower().replace("’", "'"))


def shingles(text):
    tokens = words(text)
    if len(tokens) < SHINGLE_WORDS:
        return {" ".join(tokens)}
    return {" ".join(tokens[i:i + SHINGLE_WORDS]) for i in range(len(tokens) - SHINGLE_WORDS + 1)}


def load_real_texts(data_dir):
    texts = [clean_text(t) for t in pq.read_table(data_dir / "raw" / "twitter" / "tweets.parquet")["text"].to_pylist()]
    rows = json.loads((data_dir / "raw" / "truth_social" / "truth_archive.json").read_text(encoding="utf-8"))
    return texts + [clean_text(row.get("content")) for row in rows]


def load_fakes(fakes_dir):
    fakes = []
    for path in sorted(fakes_dir.glob("*.txt")):
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            year, text = line.split("|", 1)
            fakes.append({"year": int(year), "text": html.unescape(text).strip(), "created_at": year})
    return fakes


TOPIC_RE = re.compile(
    r"\b(Democrats?|Dems|Republicans?|Fake News|Border|Wall|China|Biden|Obama|Hillary|Russia|Witch Hunt|Tariffs?|"
    r"Economy|Jobs|Stock Market|Iran|Trade|Military|Polls?|Media|Kamala|Election|Fed|Jeb|Cruz|Mexico|Trump)\b",
    re.I,
)
SIGN_OFF_RE = re.compile(r"(President (DJT|DONALD J\. TRUMP)|attention to this matter!?)$", re.I)


def char_limit(year):
    return 140 if year <= 2016 else 280 if year <= 2021 else 500


def is_caps_heavy(text):
    letters = [c for c in text if c.isalpha()]
    return sum(c.isupper() for c in letters) / max(len(letters), 1) > 0.5


def merge_short_posts(pool, real, rng):
    """Join same-year, same-topic posts, spending only the fakes beyond the era's quota."""
    target = statistics.median(len(p["text"]) for p in real)
    floor = len(real)
    pool = sorted(pool, key=lambda f: f["text"])
    rng.shuffle(pool)
    buckets = collections.defaultdict(list)
    for fake in pool:
        match = TOPIC_RE.search(fake["text"])
        if match and len(fake["text"]) < target and not SIGN_OFF_RE.search(fake["text"]):
            buckets[(fake["year"], match.group(0).lower(), is_caps_heavy(fake["text"]))].append(fake)
    for key, bucket in sorted(buckets.items(), key=str):
        while len(bucket) >= 2:
            if len(pool) <= floor or statistics.median(len(f["text"]) for f in pool) >= target:
                return pool
            first, second = bucket.pop(), bucket.pop()
            text = f"{first['text']} {second['text']}"
            if len(text) > char_limit(first["year"]):
                continue
            pool = [f for f in pool if f is not first and f is not second]
            pool.append({**first, "text": text})
    return pool


def match_lengths(real, pool, rng):
    """Pick, for each real post, the unused fake closest to it in length."""
    pool = sorted(pool, key=lambda f: (len(f["text"]), f["text"]))
    rng.shuffle(pool)
    selected = []
    for post in sorted(real, key=lambda p: len(p["text"]), reverse=True):
        if not pool:
            break
        best = min(range(len(pool)), key=lambda i: abs(len(pool[i]["text"]) - len(post["text"])))
        selected.append(pool.pop(best))
    return selected


def rate(texts, test):
    return sum(bool(test(t)) for t in texts) / max(len(texts), 1)


CONTRACTIONS = [
    (re.compile(r"\b(I|we|you|they|We|You|They)'ve\b"), r"\1 have"),
    (re.compile(r"\b(we|you|they|We|You|They)'re\b"), r"\1 are"),
    (re.compile(r"\b(I|we|you|they|he|she|it|We|You|They|He|She|It)'ll\b"), r"\1 will"),
    (re.compile(r"\b(It|it|That|that|He|he|She|she|There|there|What|what)'s (?=(?:been|happened|gone|done|become|had|made|taken)\b)"), r"\1 has "),
    (re.compile(r"\b(It|it|That|that|He|he|She|she|There|there|What|what)'s\b"), r"\1 is"),
    (re.compile(r"\bI'm\b"), "I am"),
]
CONTRACTION_RE = re.compile(r"\b\w+['\u2019](?:ve|re|ll|m|s)\b", re.I)
VERY_RE = re.compile(r"\b[Vv]ery (?!much|well|few|little|many|soon)")


def match_style(fakes, real_texts, rng):
    """Rewrite writing habits on a seeded subset so each era's rates match the real posts."""
    for fake in fakes:
        fake["text"] = re.sub(r"(?:'ve| have) got\b", " have", fake["text"])
        fake["text"] = re.sub(r"(?:'s| has) got\b", " has", fake["text"])
    texts = [f["text"] for f in fakes]
    fake_rate = rate(texts, CONTRACTION_RE.search)
    keep = rate(real_texts, CONTRACTION_RE.search) / max(fake_rate, 0.01)
    for fake in fakes:
        if rng.random() > keep:
            for pattern, replacement in CONTRACTIONS:
                fake["text"] = pattern.sub(replacement, fake["text"])

    fake_count = sum(len(VERY_RE.findall(f["text"])) for f in fakes) / len(fakes)
    real_count = sum(len(VERY_RE.findall(t)) for t in real_texts) / len(real_texts)
    keep = real_count / max(fake_count, 0.01)
    for fake in fakes:
        fake["text"] = VERY_RE.sub(lambda m: m.group(0) if rng.random() < keep else "", fake["text"])
        fake["text"] = re.sub(r"\b([Aa]) (?=[AEIOaeio]|U[^n]|u[^n])(?!One|one|Euro|euro)", r"\1n ", fake["text"])

    curly_share = sum(t.count("\u2019") for t in real_texts) / max(sum(t.count("\u2019") + t.count("'") for t in real_texts), 1)
    for fake in fakes:
        if rng.random() < curly_share:
            fake["text"] = fake["text"].replace("'", "\u2019")

    missing = rate(real_texts, is_caps_heavy) - rate([f["text"] for f in fakes], is_caps_heavy)
    candidates = [f for f in fakes if not is_caps_heavy(f["text"]) and "@" not in f["text"]]
    for fake in rng.sample(candidates, max(0, min(len(candidates), round(missing * len(fakes))))):
        fake["text"] = fake["text"].upper()
    for fake in fakes:
        fake["text"] = fake["text"].replace("PRESIDENT DJT", "President DJT")

    ends_bang = lambda t: t.endswith("!")
    excess = rate([f["text"] for f in fakes], ends_bang) - rate(real_texts, ends_bang)
    candidates = [f for f in fakes if ends_bang(f["text"])]
    for fake in rng.sample(candidates, max(0, min(len(candidates), round(excess * len(fakes))))):
        fake["text"] = fake["text"][:-1] + rng.choice([".", ".", ""])

    has_amp = lambda t: "&" in t
    missing = rate(real_texts, has_amp) - rate([f["text"] for f in fakes], has_amp)
    candidates = [f for f in fakes if " and " in f["text"] and not has_amp(f["text"])]
    for fake in rng.sample(candidates, max(0, min(len(candidates), round(missing * len(fakes))))):
        fake["text"] = fake["text"].replace(" and ", " & ", 1)

    scare_quote = lambda t: "“" in t
    missing = rate(real_texts, scare_quote) - rate([f["text"] for f in fakes], scare_quote)
    term = re.compile(r"\b(Experts|Polls|Fake News|Hoax|Witch Hunt|Rigged|Deal|Crime|Leaders|Lawyers|Judge|Reporters|Comeback|Winning|Prosecutors|Investigation|Case|Trial|Speech|Ratings|Candidates|Pollsters|Story|Press|Debate|Report|Interview)\b")
    candidates = [f for f in fakes if term.search(f["text"]) and not scare_quote(f["text"])]
    for fake in rng.sample(candidates, max(0, min(len(candidates), round(missing * len(fakes))))):
        fake["text"] = term.sub(lambda m: f"“{m.group(0)}”", fake["text"], count=1)

    missing = rate(real_texts, lambda t: "@" in t) - rate([f["text"] for f in fakes], lambda t: "@" in t)
    handles = {"Fox News": "@FoxNews", "Fox": "@FoxNews", "New York Times": "@nytimes", "CNN": "@CNN", "NBC": "@NBCNews",
               "ABC": "@ABC", "Wall Street Journal": "@WSJ", "Washington Post": "@washingtonpost", "CBS": "@CBSNews"}
    pattern = re.compile(r"\b(" + "|".join(handles) + r")\b")
    candidates = [f for f in fakes if pattern.search(f["text"]) and "@" not in f["text"]]
    for fake in rng.sample(candidates, max(0, min(len(candidates), round(missing * len(fakes))))):
        fake["text"] = pattern.sub(lambda m: handles[m.group(0)], fake["text"], count=1)


def style(texts):
    def share(test):
        return round(100 * sum(bool(test(t)) for t in texts) / max(len(texts), 1))

    return {
        "count": len(texts),
        "median_chars": statistics.median(len(t) for t in texts),
        "pct_ends_exclamation": share(lambda t: t.endswith("!")),
        "pct_caps_heavy": share(is_caps_heavy),
        "pct_quotes": share(lambda t: "“" in t or '"' in t),
        "pct_ampersand": share(lambda t: "&" in t),
        "pct_mentions": share(lambda t: "@" in t),
        "pct_hashtags": share(lambda t: "#" in t),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("data_dir", type=pathlib.Path)
    args = parser.parse_args()

    real_shingles = set()
    real_exact = set()
    for text in load_real_texts(args.data_dir):
        real_shingles |= shingles(text)
        real_exact.add(" ".join(words(text)))

    kept, failures = [], []
    seen = collections.defaultdict(set)
    kept_shingles = []
    for fake in load_fakes(args.data_dir / "fakes"):
        grams = shingles(fake["text"])
        overlap = len(grams & real_shingles) / len(grams)
        if " ".join(words(fake["text"])) in real_exact or overlap > MAX_REAL_OVERLAP:
            failures.append({"reason": "matches_real_post", "overlap": round(overlap, 2), **fake})
            continue
        matches = collections.Counter(i for gram in grams for i in seen[gram])
        if matches and matches.most_common(1)[0][1] / len(grams) > MAX_FAKE_OVERLAP:
            failures.append({"reason": "matches_other_fake", "overlap": 1, **fake})
            continue
        for gram in grams:
            seen[gram].add(len(kept_shingles))
        kept_shingles.append(grams)
        kept.append(fake)

    real_deck = json.loads((args.data_dir / "out" / "real_deck.json").read_text(encoding="utf-8"))
    rng = random.Random(SEED)
    candidates = json.loads((args.data_dir / "out" / "real_candidates.json").read_text(encoding="utf-8"))
    eligible = [p for p in candidates if p["engagement_rank"] >= 0.5 and not deck_reject_reason(p)]
    deck, game_real = [], []
    for era in ERA_QUOTAS:
        target = [p for p in real_deck if p["era"] == era]
        pool = merge_short_posts([f for f in kept if era_of(f) == era], target, rng)
        selected = match_lengths(target, pool, rng)
        # Lengths the fakes could not reach are closed from the real side: each fake gets
        # the eligible real post nearest its own length, so length gives nothing away.
        real = match_lengths(selected, [{**p, "era": era} for p in eligible if era_of(p) == era], rng)
        match_style(selected, [p["text"] for p in real], rng)
        deck.extend(selected)
        game_real.extend(real)
    deck = [{
        "id": f"fk-{index:04d}",
        "label": "fake",
        "source": "twitter" if fake["year"] <= 2021 else "truth_social",
        "era": era_of(fake),
        "year": fake["year"],
        "text": fake["text"],
    } for index, fake in enumerate(sorted(deck, key=lambda f: f["year"]), start=1)]

    cards = [
        {"id": p["id"], "label": "real", "source": p["source"], "era": p["era"],
         "year": int(p["created_at"][:4]), "text": p["text"], "url": p["url"]}
        for p in game_real
    ] + [{**fake, "url": None} for fake in deck]
    random.Random(SEED).shuffle(cards)

    report = {"failures": failures, "eras": {}}
    for era, quota in ERA_QUOTAS.items():
        report["eras"][era] = {
            "quota": quota,
            "real": style([p["text"] for p in game_real if p["era"] == era]),
            "fake": style([f["text"] for f in deck if f["era"] == era]),
        }

    out_dir = args.data_dir / "out"
    for name, value in (("fake_deck.json", deck), ("game_deck.json", cards), ("fake_report.json", report)):
        (out_dir / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print("kept", len(deck), "failures", collections.Counter(f["reason"] for f in failures))
    for era, row in report["eras"].items():
        print(era, "quota", row["quota"], "\n  real", row["real"], "\n  fake", row["fake"])


if __name__ == "__main__":
    main()
