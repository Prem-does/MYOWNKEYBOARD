#!/usr/bin/env python3
import argparse
import csv
import json
import sys
from pathlib import Path

# Add the cloned wordfreq repository to Python path
sys.path.insert(0, str(Path(__file__).resolve().parent / "wordfreq-repo"))

from wordfreq import get_frequency_dict


def export_csv(freq_dict, output_path, limit=None):
    with open(output_path, "w", encoding="utf-8", newline="") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["word", "frequency"])
        for idx, (word, freq) in enumerate(sorted(freq_dict.items(), key=lambda x: -x[1])):
            if limit is not None and idx >= limit:
                break
            writer.writerow([word, f"{freq:.12f}"])


def export_json(freq_dict, output_path, limit=None):
    items = [
        {"word": word, "frequency": float(freq)}
        for word, freq in sorted(freq_dict.items(), key=lambda x: -x[1])
        if limit is None or len(items) < limit
    ]
    with open(output_path, "w", encoding="utf-8") as jsonfile:
        json.dump(items, jsonfile, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(description="Export wordfreq English wordlist to CSV or JSON.")
    parser.add_argument("--wordlist", default="large", choices=["large", "small"], help="wordfreq wordlist size")
    parser.add_argument("--lang", default="en", help="language code")
    parser.add_argument("--format", default="csv", choices=["csv", "json"], help="output file format")
    parser.add_argument("--output", default=None, help="output file path")
    parser.add_argument("--limit", type=int, default=None, help="maximum number of words to export")
    args = parser.parse_args()

    freq_dict = get_frequency_dict(args.lang, args.wordlist)
    out_path = Path(args.output) if args.output else Path(f"wordfreq_{args.wordlist}_{args.lang}.{args.format}")

    if args.format == "csv":
        export_csv(freq_dict, out_path, args.limit)
    else:
        export_json(freq_dict, out_path, args.limit)

    print(f"Exported {len(freq_dict) if args.limit is None else min(args.limit, len(freq_dict))} words to {out_path}")


if __name__ == "__main__":
    main()
