#!/usr/bin/env python3
"""Erzeugt die beiden Wortlisten in app/src/main/assets/.

guesses.txt   — alle zulässigen Rateversuche: jedes Wort mit genau fünf Buchstaben aus
                enz/german-wordlist (CC0, Scrabble-Regeln: keine Namen, Abkürzungen, Toponyme).
solutions.txt — die Wörter, die gesucht werden: die häufigsten dieser Wörter laut
                hermitdave/FrequencyWords (OpenSubtitles 2018). Weil Untertitel viel Englisch und
                Namen enthalten, fliegen Wörter aus dem englischen Wörterbuch raus, außer sie
                stehen in keep.txt; anstößige Wörter und Namen stehen in block.txt.

Aufruf: tools/build_wordlists.py <german-wordlist/words> <de_50k.txt> [/usr/share/dict/american-english]
"""
import os
import sys

ALPHABET = set("ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜ")
# So viele Lösungswörter; dahinter werden die Kandidaten zunehmend obskur.
SOLUTION_COUNT = 1800

here = os.path.dirname(os.path.abspath(__file__))
assets = os.path.join(here, "..", "app", "src", "main", "assets")


def read_set(name):
    with open(os.path.join(here, name), encoding="utf-8") as f:
        return {w.strip().upper() for w in f.read().split() if not w.startswith("#")}


def main(wordlist, frequencies, english="/usr/share/dict/american-english"):
    with open(wordlist, encoding="utf-8") as f:
        # ß als Großbuchstabe wäre "SS" und änderte die Länge — solche Wörter bleiben draußen.
        guesses = {w for w in (l.strip().upper() for l in f) if len(w) == 5 and set(w) <= ALPHABET}
    with open(english, encoding="utf-8") as f:
        english_words = {l.strip().upper() for l in f}
    keep, block = read_set("keep.txt"), read_set("block.txt")

    solutions = []
    with open(frequencies, encoding="utf-8") as f:
        for line in f:
            word = line.split()[0].upper()
            if word not in guesses or word in block or word in solutions:
                continue
            if word in english_words and word not in keep:
                continue
            solutions.append(word)
            if len(solutions) == SOLUTION_COUNT:
                break

    for name, words in (("guesses.txt", sorted(guesses)), ("solutions.txt", sorted(solutions))):
        with open(os.path.join(assets, name), "w", encoding="utf-8") as f:
            f.write("\n".join(words) + "\n")
    print(f"{len(guesses)} zulässige Wörter, {len(solutions)} Lösungswörter")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(*sys.argv[1:])
