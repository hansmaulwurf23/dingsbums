# Dingsbums

Wordle-Klon für Android: Gesucht wird ein deutsches Wort mit fünf Buchstaben, sechs Versuche hat man.

## Bedienung

- Auf der Bildschirmtastatur (QWERTZ mit Ä, Ö, Ü) ein Wort tippen, mit **ENTER** abschicken.
- Die Felder klappen nacheinander um und zeigen:
  **grün** — richtiger Buchstabe an der richtigen Stelle,
  **gelb** — Buchstabe kommt im Wort vor, aber woanders,
  **grau** — Buchstabe kommt nicht (oder nicht so oft) vor.
- Die Tastatur übernimmt die beste bisher bekannte Farbe jedes Buchstabens.
- Doppelte Buchstaben werden wie im Original gezählt: ein Buchstabe wird nur so oft grün/gelb, wie
  er in der Lösung vorkommt.
- Unbekannte oder zu kurze Wörter werden abgelehnt (die Zeile wackelt), sie kosten keinen Versuch.
- **Statistik** (Balken-Symbol): gespielt, Anteil gelöst, aktuelle und beste Serie, Verteilung der
  Versuche bis zur Lösung und Durchschnitt. Erscheint auch nach jeder Runde.
- **Neues Wort** (Pfeil-Symbol): Mitten in einer Runde zählt das als aufgegeben, damit sich die
  Statistik nicht durch Abbrechen schönen lässt. Ohne einen einzigen Versuch zählt es nicht.
- Laufende Runde und Statistik liegen in den SharedPreferences und überstehen einen Neustart.

## Wortlisten

Die Listen liegen fertig in `app/src/main/assets/` und werden von `tools/build_wordlists.py` erzeugt:

- **`guesses.txt`** (≈ 9 500 Wörter) — alle zulässigen Rateversuche: jedes Wort mit fünf Buchstaben
  aus [enz/german-wordlist](https://github.com/enz/german-wordlist) (CC0). Die Liste folgt den
  Scrabble-Regeln, enthält also auch gebeugte Formen, aber keine Namen, Abkürzungen oder Ortsnamen.
- **`solutions.txt`** (≈ 1 250 Wörter) — die Wörter, die gesucht werden. Nur **Grundformen**
  (Substantiv im Nominativ Singular, Verb im Infinitiv, Adjektiv, Adverb, Präposition, …), erkannt
  über die Wortart-Überschriften im deutschen Wiktionary (maschinenlesbarer Export von
  [kaikki.org](https://kaikki.org/dewiktionary/Deutsch/)). Gebeugte Formen wie HÖRST oder BÄNKE
  stehen dort als „Konjugierte/Deklinierte Form“ und fallen raus, ebenso Pronomen, Artikel und
  Namen. Davon bleiben nur Wörter, die unter den 50 000 häufigsten aus
  [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords) (OpenSubtitles 2018)
  sind. Englische Lehnwörter (DADDY, STORY …) fliegen über das englische Wörterbuch raus, außer sie
  stehen in `tools/keep.txt`. Anstößiges, Nationalitäten und seltsam wirkende Grundformen stehen in
  `tools/block.txt` — raten lassen sie sich trotzdem, nur gesucht werden sie nie.

Wörter mit ß bleiben ganz draußen (groß geschrieben würde daraus SS und das Wort zu lang).

Neu erzeugen:

```bash
curl -LO https://raw.githubusercontent.com/enz/german-wordlist/master/words
curl -LO https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/de/de_50k.txt
# Der Wiktionary-Export ist 3,4 GB groß; beim Herunterladen grob auf Fünf-Buchstaben-Einträge filtern
curl -s https://kaikki.org/dewiktionary/Deutsch/kaikki.org-dictionary-Deutsch.jsonl \
  | LC_ALL=C.UTF-8 grep -E '"word": ?"[[:alpha:]]{5}"' > dewikt5.jsonl
python3 tools/build_wordlists.py words de_50k.txt dewikt5.jsonl /usr/share/dict/american-english
```

Ein unpassendes Lösungswort fliegt am einfachsten über einen Eintrag in `tools/block.txt` raus.

## Technik

- Java, Android Views (kein Compose), Material 3, minSdk 26 / targetSdk 34.
- `Game` ist die Spiellogik ohne Android-Abhängigkeiten, getestet in `GameTest`.
- `BoardView` (Raster mit Umklapp- und Wackel-Animation), `KeyboardView` (Bildschirmtastatur) und
  `DistributionView` (Balken der Statistik) sind eigene Views.
- `GameStore` speichert die laufende Runde, verbucht ihr Ergebnis genau einmal in `Stats` und merkt
  sich die letzten 200 Lösungen, damit kein Wort gleich wiederkommt.
- Eine Hardware-Tastatur funktioniert auch (praktisch im Emulator).

## Bauen und installieren

Gebraucht wird ein **JDK 17** (siehe Focus-README; das JBR aus Android Studio ist zu neu):

```bash
JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Download

Signierte APKs hängen an den [Releases](https://gitlab.noisebleed.de/fun/dingsbums/-/releases). Das
Projekt ist privat, der Download braucht also eine Anmeldung bei GitLab. Der Direktlink einer Version:

```
https://gitlab.noisebleed.de/api/v4/projects/57/packages/generic/dingsbums/<version>/Dingsbums-<version>.apk
```

## Release

Der Signaturschlüssel liegt **nicht im Repo**, sondern in `~/Documents/Android/own/dingsbums/` — wie
bei Focus per Syncthing zwischen den eigenen Rechnern gespiegelt und im Backup. `keystore.properties`
daneben nennt Alias und Passwörter, `storeFile` wird relativ zu dieser Datei aufgelöst. Ein
abweichender Pfad geht über `-Pdingsbums.signing=…`; fehlt der Schlüssel ganz, bleibt das
Release-APK unsigniert statt den Build zu brechen.

Ohne diesen Schlüssel lässt sich kein Update über eine installierte Version schieben — Verlust
bedeutet, dass alle Nutzer erst deinstallieren müssten.

Lokal bauen und prüfen:

```bash
JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Release über CI

`.gitlab-ci.yml` erledigt alles selbst, sobald ein Tag der Form `v<Ziffer>` gepusht wird: bauen,
signieren, in die Paket-Registry laden, Release mit Asset-Link anlegen. Für Branches läuft die
Pipeline bewusst gar nicht.

```bash
# versionCode und versionName in app/build.gradle anheben, committen
git tag -a v1.1 -m "Dingsbums 1.1" && git push origin main && git push origin v1.1
```

Zwei Prüfungen brechen den Job absichtlich ab, statt ein kaputtes Release zu veröffentlichen: das
Umbenennen der APK schlägt fehl, wenn mangels Schlüssel nur `app-release-unsigned.apk` entstanden
ist, und `versionName` im APK muss zum Tag passen.

### Einrichtung (erledigt)

- **Runner:** der Docker-Runner „Focus App Runner“ (Tag `docker`) ist auch für dieses Projekt
  freigeschaltet; Job-Image `mobiledevops/android-sdk-image:34.0.0-jdk17` wie bei Focus.
- **Geschütztes Tag-Muster** `v*` (nur Maintainer) — nur dann gibt GitLab die geschützten Variablen
  heraus.
- **Geschützte CI-Variablen** (Settings → CI/CD → Variables):

| Variable | Typ | maskiert | Inhalt |
|---|---|---|---|
| `ANDROID_KEYSTORE_B64` | File | nein | `base64 -w0 dingsbums-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Variable | ja | `storePassword` |
| `ANDROID_KEY_ALIAS` | Variable | nein | `dingsbums` |
| `ANDROID_KEY_PASSWORD` | Variable | ja | `keyPassword` |

Damit existiert eine zweite Kopie des Signaturschlüssels in GitLab — der Preis dafür, dass die CI
signieren kann.
