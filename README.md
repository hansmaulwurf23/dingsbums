# Dingsbums

Wordle-Klon für Android: Gesucht wird ein deutsches Wort mit fünf Buchstaben, sechs Versuche hat man.

## Bedienung

- Auf der Bildschirmtastatur (QWERTZ, Ä/Ö/Ü in der untersten Reihe) ein Wort tippen, mit **ENTER**
  abschicken. Die Eingabezeile ist blau hinterlegt, das Feld mit hellem Rahmen ist der Cursor.
- **Feld antippen** setzt den Cursor dorthin: Tippen überschreibt ab da und rückt weiter. Löschen
  entfernt den Buchstaben unter dem Cursor, ist das Feld leer, den links davon. Mit Lücken lässt
  sich nicht abschicken. Auf einer Hardware-Tastatur gehen auch die Pfeiltasten.
- **Doppeltipp** auf die leere erste Zeile schickt AUTOS ab, auf die leere zweite REICH; ist dabei
  auch die erste noch leer, kommen beide nacheinander. Mit schon getippten Buchstaben passiert nichts.
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

Gebraucht wird ein **JDK 17** (das JBR aus aktuellen Android-Studio-Versionen ist zu neu für Gradle 8.7):

```bash
JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Download

Signierte APKs hängen an den [Releases](https://github.com/hansmaulwurf23/dingsbums/releases).

Zum Installieren die Datei auf dem Gerät öffnen. Android fragt einmalig nach der Erlaubnis, Apps aus
dieser Quelle zu installieren. Voraussetzung ist Android 8 oder neuer; auf dem Fire-Tablet im
Eltern-Profil installieren und die App dann für das Kinderprofil freigeben.

## Selbst-Update

Die App aktualisiert sich selbst (`update/AppUpdater`):

- Beim Start, höchstens alle 6 Stunden, und über *Menü → Nach Update suchen* fragt sie das neueste
  Release bei der GitHub-API ab (`/repos/hansmaulwurf23/dingsbums/releases/latest`). Jedes Release
  hat neben dem APK eine `update.json` mit versionCode und SHA-256.
- Ist das Release neuer, lädt sie das APK im Hintergrund, prüft die SHA-256 und bietet es per Dialog
  an. Installiert wird über eine PackageInstaller-Session; beim ersten Mal fragt Android nach der
  Erlaubnis „Apps aus dieser Quelle installieren“.
- **Statistik und laufende Runde bleiben erhalten**: ein Update mit demselben Signaturschlüssel ersetzt
  nur den Code, die SharedPreferences bleiben unberührt. Deshalb darf der Schlüssel nie wechseln und
  `versionCode` muss mit jedem Release steigen.
- In Profilen, die keine Apps installieren dürfen (Amazon-Kids-Profil auf dem Fire-Tablet), sucht die
  App gar nicht erst. Das Update im Eltern-Profil gilt für alle Profile des Geräts.

Die API-Adresse steht in `app/build.gradle` (`UPDATE_API`). Ein Umbenennen des Repos leitet GitHub
weiter; wird es gelöscht oder privat, finden ausgelieferte Apps keine Updates mehr.

Bis Version 1.4 lag das Projekt auf gitlab.noisebleed.de (`pub/dingsbums`, Projekt-ID 57), und die
Apps fragten dort. Version 1.5 ist deshalb zusätzlich noch dort erschienen: Wer sie installiert, sucht
ab dann auf GitHub. Das GitLab-Projekt ist archiviert, seine Releases bleiben abrufbar.

## Release

Signaturschlüssel und `keystore.properties` liegen **nicht im Repo**, sondern in
`~/Documents/Android/own/dingsbums/` (per Syncthing gespiegelt und im Backup); `storeFile` wird
relativ zur Properties-Datei aufgelöst. Ein abweichender Pfad geht über `-Pdingsbums.signing=…`;
fehlt der Schlüssel ganz, bleibt das Release-APK unsigniert statt den Build zu brechen.

Ohne diesen Schlüssel lässt sich kein Update über eine installierte Version schieben — Verlust
bedeutet, dass alle Nutzer erst deinstallieren müssten und ihre Statistik verlören.

Lokal bauen und prüfen:

```bash
JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

`.github/workflows/release.yml` testet, baut, signiert und veröffentlicht, sobald ein Tag `v<Ziffer>`
gepusht wird, und hängt `update.json` neben das APK. Der Text des annotierten Tags (ohne die erste
Zeile) wird zur Release-Beschreibung. Zwei Prüfungen brechen absichtlich ab, statt ein kaputtes
Release zu veröffentlichen: ein unsigniertes APK und ein `versionName`, der nicht zum Tag passt.

Die Repository-Secrets `ANDROID_KEYSTORE_B64` (`base64 -w0 dingsbums-release.jks`),
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`dingsbums`) und `ANDROID_KEY_PASSWORD` sind
gesetzt; damit liegt eine zweite Kopie des Schlüssels bei GitHub.

```bash
# versionCode und versionName in app/build.gradle anheben, committen
git tag -a v1.6 -m "Dingsbums 1.6" -m "- Was sich geändert hat" && git push origin main v1.6
```
