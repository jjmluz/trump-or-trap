#!/usr/bin/env python3
"""Build the real-post data for Trump or Trap.

Usage: build_real_posts.py <data_dir> [--refresh]

Writes to <data_dir>/out:
  real_candidates.json  every post that passes the playable filters
  real_deck.json        the selected game deck
  build_report.json     counts per filter, per era and per source
"""
import argparse
import collections
import datetime
import html
import json
import pathlib
import random
import re
import statistics
import urllib.request

import pyarrow.parquet as pq

TWEETS_URL = "https://huggingface.co/api/datasets/fschlatt/trump-tweets/parquet/default/train/0.parquet"
TRUTH_URL = "https://ix.cnn.io/data/truth-social/truth_archive.json"

SEED = 20261102
DECK_MAX_CHARS = 500
MIN_WORDS = 8
NEAR_DUPLICATE_JACCARD = 0.6

# Deck size per era, weighted toward the voice most players recognize.
ERA_QUOTAS = {
    "2009-2014": 75,
    "2015-2016": 225,
    "2017-2021": 525,
    "2022-2023": 225,
    "2024-2026": 450,
}

LINK_RE = re.compile(r"https?://|www\.|pic\.twitter\.com|\b[\w-]+\.(?:com|org|net|gov|tv)\b", re.I)
QUOTE_OPEN = ('"', "“", "‘", "'")
ATTRIBUTED_QUOTE_RE = re.compile(r"^[A-Z][\w .'’-]{2,40}:\s*[\"“]")
CONTEXT_OPENERS = re.compile(
    r"^(he|she|they|them|this|that|these|those|it|it's|his|her|their|and|but|so|or|because|which)\b"
    r"|^(no|yes),|^page \d",
    re.I,
)
ROUTINE_RE = re.compile(
    r"\b(tune in|will be (on|interviewed|speaking|appearing|live)|being interviewed|i will be on|watch (me|tonight|on)|"
    r"see you (in|at|tonight|tomorrow|soon)|just (landed|arrived)|departing|join me|congratulations (to|on)|"
    r"happy (birthday|mother|father|thanksgiving|easter|new year)|merry christmas|rest in peace|"
    r"thoughts and prayers|complete and total endorsement|think like a champion|midas touch|"
    r"via @|on @\w+ at \d|at \d{1,2}(:\d\d)?\s?[ap]\.?m\b)",
    re.I,
)
# Both archives dropped most line breaks without leaving a space ("Samoa.Jared",
# "TRUMPPRESIDENT", "seasonHence"). These put the space back where the join is unambiguous.
LOST_BREAKS = [
    # "Samoa.Jared", "TIME!!!Will"; not "U.S.A.", "Ph.D", "....The" or domain names
    (re.compile(r"(?<=[A-Za-z]{2})(?<!\bPh)(\.|[!?]+)(?=[A-Z])(?!(?i:com|org|net|gov|tv)\b)"), r"\1 "),
    (re.compile(r"(?<=[A-Za-z]):(?=\d)"), ": "),  # "defenses:1. This"
    (re.compile(r"\b(DONALD J\. TRUMP|DJT)(?=[A-Z])"), r"\1 "),  # "TRUMPPRESIDENT"
    # "seasonHence"; not his "unAmerican", names like "diGenova" or @handles and #hashtags
    (re.compile(r"(?<![\w@#'’])((?!un|[dl][aeiu]|ma?c)[a-z]{2,})(?=[A-Z][a-z])"), r"\1 "),
]
# Joins that can't be split safely: a number run into a word ("2018: 0Source") or a list
# number run into the line before ("Manipulation2. VATs").
UNREPAIRABLE_BREAK_RE = re.compile(r"(?<![\w@#])(?:\d+[A-Z][a-z]|[A-Za-z]+\d+\.(?:\s|$))")
CAMEL_RE = re.compile(r"(?<![\w@#'’])[A-Z][a-z]+(?:[A-Z][a-z]+)+\b")


def download(url, path, refresh):
    if path.exists() and not refresh:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "trump-or-trap-dataset/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response:
        path.write_bytes(response.read())


def clean_text(raw):
    text = re.sub(r"<br\s*/?>|</p>", "\n", raw or "", flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text).replace(" ", " ").replace("￼", "")
    # Fake posts are one line, so real ones are too.
    text = re.sub(r"\s+", " ", text)
    for pattern, replacement in LOST_BREAKS:
        text = pattern.sub(replacement, text)
    return text.strip()


def rare_camel_words(posts):
    """CamelCase words used in only one post of the whole archive.

    His own (ObamaCare, FoxNews, DeSanctimonious) recur; a one-off like "PolinSagui" is
    usually two lines of a list run together.
    """
    counts = collections.Counter(word for post in posts for word in set(CAMEL_RE.findall(post["text"])))
    return {word for word, count in counts.items() if count == 1}


def load_tweets(path):
    posts = []
    for row in pq.read_table(path).to_pylist():
        posts.append({
            "id": f"tw-{row['id']}",
            "source": "twitter",
            # The source stores "&amp;" as "&amp," so it unescapes to "&,".
            "text": clean_text(row["text"]).replace("&,", "&"),
            "created_at": row["datetime"].replace(tzinfo=datetime.timezone.utc).isoformat(),
            "url": f"https://twitter.com/realDonaldTrump/status/{row['id']}",
            "likes": row["favorites"] or 0,
            "reposts": row["retweets"] or 0,
            "is_repost": bool(row["is_retweet"]),
            "is_deleted": bool(row["is_deleted"]),
            "is_flagged": bool(row["is_flagged"]),
            "has_media": False,
        })
    return posts


def load_truths(path):
    posts = []
    for row in json.loads(path.read_text(encoding="utf-8")):
        posts.append({
            "id": f"ts-{row['id']}",
            "source": "truth_social",
            "text": clean_text(row.get("content")),
            "created_at": row["created_at"],
            "url": row.get("url"),
            "likes": row.get("favourites_count") or 0,
            "reposts": row.get("reblogs_count") or 0,
            "is_repost": False,
            "is_deleted": False,
            "is_flagged": False,
            "has_media": bool(row.get("media")),
        })
    return posts


def reject_reason(post, rare_words):
    text = post["text"]
    if not text:
        return "empty"
    if post["is_repost"] or text.startswith("RT "):
        return "repost"
    if post["is_flagged"]:
        return "flagged"
    if post["has_media"]:
        return "media"
    if LINK_RE.search(text):
        return "link"
    if text.startswith("@") or re.match(r"^[\"“]\s*@", text):
        return "reply"
    if text.startswith(QUOTE_OPEN) or ATTRIBUTED_QUOTE_RE.match(text):
        return "quoting_someone_else"
    if len(text.split()) < MIN_WORDS:
        return "too_short"
    if UNREPAIRABLE_BREAK_RE.search(text) or any(w in rare_words for w in CAMEL_RE.findall(text)):
        return "lost_line_break"
    return None


def deck_reject_reason(post):
    text = post["text"]
    if len(text) > DECK_MAX_CHARS:
        return "too_long_for_tv"
    if re.match(r"^(\.{2,}|…)", text) or re.search(r"(\.{2,}|…\.*)$", text):
        return "thread_fragment"
    if post["is_deleted"]:
        return "deleted"
    if CONTEXT_OPENERS.match(text):
        return "needs_context"
    if ROUTINE_RE.search(text):
        return "routine"
    return None


def normalized_words(text):
    return re.findall(r"[a-z0-9']+", text.lower())


def dedupe_exact(posts):
    seen = {}
    for post in sorted(posts, key=lambda p: p["created_at"]):
        seen.setdefault(" ".join(normalized_words(post["text"])), post)
    return list(seen.values())


def era_of(post):
    year = int(post["created_at"][:4])
    if year <= 2014:
        return "2009-2014"
    if year <= 2016:
        return "2015-2016"
    if year <= 2021:
        return "2017-2021"
    if year <= 2023:
        return "2022-2023"
    return "2024-2026"


def add_engagement_rank(posts):
    groups = collections.defaultdict(list)
    for post in posts:
        groups[(post["source"], post["created_at"][:4])].append(post)
    for group in groups.values():
        group.sort(key=lambda p: p["likes"] + p["reposts"])
        for index, post in enumerate(group):
            post["engagement_rank"] = round(index / max(len(group) - 1, 1), 4)


def select_deck(candidates):
    rng = random.Random(SEED)
    deck, rejected = [], collections.Counter()
    picked_shingles = []
    token_index = collections.defaultdict(set)
    by_era = collections.defaultdict(list)
    for post in candidates:
        reason = deck_reject_reason(post)
        if reason:
            rejected[reason] += 1
        elif post["engagement_rank"] >= 0.5:
            by_era[era_of(post)].append(post)
        else:
            rejected["low_engagement"] += 1

    for era, quota in ERA_QUOTAS.items():
        pool = sorted(by_era[era], key=lambda p: p["id"])
        rng.shuffle(pool)
        taken = 0
        for post in pool:
            if taken == quota:
                break
            words = normalized_words(post["text"])
            shingles = {" ".join(words[i:i + 3]) for i in range(len(words) - 2)}
            overlapping = set().union(*(token_index[s] for s in shingles)) if shingles else set()
            if any(len(shingles & picked_shingles[i]) / len(shingles | picked_shingles[i]) >= NEAR_DUPLICATE_JACCARD
                   for i in overlapping):
                rejected["near_duplicate"] += 1
                continue
            for shingle in shingles:
                token_index[shingle].add(len(picked_shingles))
            picked_shingles.append(shingles)
            deck.append({**post, "era": era, "label": "real"})
            taken += 1
    return sorted(deck, key=lambda p: p["created_at"]), rejected


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("data_dir", type=pathlib.Path)
    parser.add_argument("--refresh", action="store_true", help="download the sources again")
    args = parser.parse_args()

    tweets_path = args.data_dir / "raw" / "twitter" / "tweets.parquet"
    truths_path = args.data_dir / "raw" / "truth_social" / "truth_archive.json"
    download(TWEETS_URL, tweets_path, args.refresh)
    download(TRUTH_URL, truths_path, args.refresh)

    posts = load_tweets(tweets_path) + load_truths(truths_path)
    rare_words = rare_camel_words(posts)
    rejected = collections.Counter()
    kept = []
    for post in posts:
        reason = reject_reason(post, rare_words)
        if reason:
            rejected[reason] += 1
        else:
            kept.append(post)
    candidates = dedupe_exact(kept)
    add_engagement_rank(candidates)
    deck, deck_rejected = select_deck(candidates)

    out_dir = args.data_dir / "out"
    out_dir.mkdir(parents=True, exist_ok=True)
    write_json(out_dir / "real_candidates.json", candidates)
    write_json(out_dir / "real_deck.json", deck)
    lengths = [len(p["text"]) for p in deck]
    write_json(out_dir / "build_report.json", {
        "source_posts": collections.Counter(p["source"] for p in posts),
        "rejected": rejected,
        "exact_duplicates_removed": len(kept) - len(candidates),
        "candidates": collections.Counter(p["source"] for p in candidates),
        "deck_rejected": deck_rejected,
        "deck_size": len(deck),
        "deck_by_source": collections.Counter(p["source"] for p in deck),
        "deck_by_era": collections.Counter(p["era"] for p in deck),
        "deck_quota": ERA_QUOTAS,
        "deck_length_chars": {
            "min": min(lengths),
            "median": statistics.median(lengths),
            "max": max(lengths),
        },
    })


if __name__ == "__main__":
    main()
