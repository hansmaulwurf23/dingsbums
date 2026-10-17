#!/usr/bin/env python3
"""Erzeugt die beiden Wortlisten in app/src/main/assets/.

guesses.txt   — alle zulässigen Rateversuche: jedes Wort mit genau fünf Buchstaben aus
                enz/german-wordlist (CC0, Scrabble-Regeln: keine Namen, Abkürzungen, Toponyme).
solutions.txt — die Wörter, die gesucht werden: nur Grundformen (Substantiv im Nominativ
                Singular, Verb im Infinitiv, Adjektiv, Adverb, …) laut dem deutschen Wiktionary
                (Export von kaikki.org), und davon nur die, die in hermitdave/FrequencyWords
                (OpenSubtitles 2018, häufigste 50 000) vorkommen. Weil Untertitel und Wiktionary
                viele englische Lehnwörter enthalten, fliegen Wörter aus dem englischen Wörterbuch
                raus, außer sie stehen in keep.txt; Anstößiges und Namen stehen in block.txt.

Aufruf: tools/build_wordlists.py <german-wordlist/words> <de_50k.txt> <kaikki-Deutsch.jsonl>
                                 [/usr/share/dict/american-english]
"""
import json
import os
import sys

ALPHABET = set("ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜ")
# Wortarten-Überschriften im deutschen Wiktionary, die eine Grundform kennzeichnen. Gebeugte Formen
# stehen dort auf eigenen Seiten ("Deklinierte Form", "Konjugierte Form", "Komparativ", …) und fehlen
# hier ebenso wie Pronomen, Artikel und Namen.
LEMMA_TITLES = {
    "Substantiv", "Zahlklassifikator", "Verb", "Hilfsverb", "Adjektiv", "Numerale",
    "Adverb", "Lokaladverb", "Temporaladverb", "Modaladverb", "Konjunktionaladverb",
    "Pronominaladverb", "Interrogativadverb", "Präposition", "Postposition", "Konjunktion",
    "Subjunktion", "Partikel", "Antwortpartikel", "Gradpartikel", "Modalpartikel",
    "Negationspartikel", "Fokuspartikel", "Interjektion", "Grußformel",
}

here = os.path.dirname(os.path.abspath(__file__))
assets = os.path.join(here, "..", "app", "src", "main", "assets")


def read_set(name):
    with open(os.path.join(here, name), encoding="utf-8") as f:
        return {w.strip().upper() for w in f.read().split() if not w.startswith("#")}


def read_lemmas(wiktionary):
    """Großgeschriebene Grundformen mit fünf Buchstaben. Die Datei ist JSON Lines, eine Zeile je
    Eintrag; vorgefiltert (siehe README) ist sie statt 3,4 GB nur ein paar hundert MB groß."""
    lemmas = set()
    with open(wiktionary, encoding="utf-8") as f:
        for line in f:
            entry = json.loads(line)
            word = entry.get("word", "")
            if len(word) == 5 and entry.get("pos_title") in LEMMA_TITLES:
                lemmas.add(word.upper())
    return lemmas


def main(wordlist, frequencies, wiktionary, english="/usr/share/dict/american-english"):
    with open(wordlist, encoding="utf-8") as f:
        # ß als Großbuchstabe wäre "SS" und änderte die Länge — solche Wörter bleiben draußen.
        guesses = {w for w in (l.strip().upper() for l in f) if len(w) == 5 and set(w) <= ALPHABET}
    with open(english, encoding="utf-8") as f:
        english_words = {l.strip().upper() for l in f}
    keep, block = read_set("keep.txt"), read_set("block.txt")
    lemmas = read_lemmas(wiktionary)

    solutions = []
    with open(frequencies, encoding="utf-8") as f:
        for line in f:
            word = line.split()[0].upper()
            if word not in guesses or word not in lemmas or word in block or word in solutions:
                continue
            if word in english_words and word not in keep:
                continue
            solutions.append(word)

    for name, words in (("guesses.txt", sorted(guesses)), ("solutions.txt", sorted(solutions))):
        with open(os.path.join(assets, name), "w", encoding="utf-8") as f:
            f.write("\n".join(words) + "\n")
    print(f"{len(guesses)} zulässige Wörter, {len(solutions)} Lösungswörter")


if __name__ == "__main__":
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
