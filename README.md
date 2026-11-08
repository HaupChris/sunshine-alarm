# Sunshine

Eine eigene Android-App für Lichtwecker der Serie Philips Somneo HF367x. Sie spricht direkt
mit der lokalen HTTPS-Schnittstelle des Weckers im eigenen WLAN, ohne Philips-Cloud und
ohne Konto. Alle Daten (Sensor-Verlauf, Schlafzeiten, Wecker) bleiben auf dem Handy.

> **Kein offizielles Produkt.** Dieses Projekt ist nicht mit Philips verbunden und wird von
> Philips weder unterstützt noch geprüft. Philips, Somneo und SleepMapper sind Marken der
> Koninklijke Philips N.V. und werden hier nur beschreibend verwendet. Die Schnittstelle des
> Weckers ist nicht dokumentiert. Nutzung auf eigene Gefahr.

## Funktionen

- **Wecker:** alle 16 Weckerplätze des Geräts anlegen, bearbeiten, benennen, ein- und
  ausschalten, löschen (mit Rückgängig)
- **Sonnenaufgang und Sonnenuntergang:** Dauer, Helligkeit, Farbschema und Ton, mit Vorschau
  direkt am Wecker; ein verschiebbarer Balken zeigt die Helligkeit zu jedem Zeitpunkt
- **Nap:** Kurzschlaf mit Countdown, Sonnenaufgang in 10 Sekunden und Stopp am Handy
- **Steuerung:** Hauptlicht, Nachtlicht, Radio mit eigenen Sendernamen und Suchlauf, AUX
- **Raumklima:** Temperatur, Luftfeuchte, Licht und Geräusch alle 15 Minuten gespeichert,
  mit Zielwerten für die Nacht (16–19 °C, 40–60 %, bis 30 dB, unter 1 lx)
- **Schlafzeiten:** aus dem Lichtsensor erkannt und von Hand korrigierbar

Getestet mit einem HF367x auf einem OnePlus 9 Pro (Android 14).

## Aufbau

| Pfad | Inhalt |
|---|---|
| `app/src/main/java/.../api` | Client für die Wecker-Schnittstelle, Datenmodelle, Zertifikats-Pinning, Gerätesuche |
| `app/src/main/java/.../data` | Room-Datenbank, Einstellungen, Repository, Schlaf-Auswertung |
| `app/src/main/java/.../nap` | Nap: exakter Android-Wecker, Vordergrund-Dienst, Vollbild-Meldung |
| `app/src/main/java/.../ui` | Oberfläche (Jetpack Compose), Bedienregeln in `Components.kt` |
| `app/src/main/java/.../work` | Hintergrund-Job: Sensoren alle 15 min speichern |
| `app/schemas/` | Exportierte DB-Schemas, Grundlage für Migrationen |
| `samples/` | Echte Antworten eines HF367x, als Testdaten |
| `tools/` | Python: Wecker im Netz finden, Schnittstelle auslesen |

## Bauen und installieren

Voraussetzungen: JDK 17 oder neuer, Android SDK (z. B. über Android Studio).

1. Einmalig einen eigenen Signaturschlüssel anlegen: `scripts/create-keystore.sh`.
   Er liegt danach in `~/.android-keys/` und `keystore.properties` (beides nicht im
   Repository). **Sichern**, sonst lässt sich später kein Update mehr über die App installieren.
2. `scripts/release.sh` erhöht die Version, führt die Tests aus und baut die APK nach `dist/`.
3. Installieren:
   - **Per Datei:** APK aufs Handy kopieren, öffnen, „Aus dieser Quelle installieren“ erlauben.
   - **Per adb:** USB-Debugging einschalten, Handy anschließen, `scripts/release.sh --install`.

Beim ersten Start sucht die App den Wecker im WLAN, alternativ lässt sich die IP eingeben.

## Updates ohne Datenverlust

`scripts/release.sh [patch|minor|major] [--install]` ersetzt die installierte App, alle Daten
bleiben erhalten, weil:

- die `applicationId` sich nie ändert,
- jeder Build mit demselben Schlüssel signiert ist,
- `versionCode` bei jedem Release steigt,
- Datenbankänderungen per Room-Migration erfolgen, nie durch Löschen.

**Bei Änderungen an der Datenbank:** `version` in `AppDatabase` erhöhen, eine
`AutoMigration(from = n, to = n + 1)` eintragen und die Schemas in `app/schemas/` committen.

## Eigenheiten des Weckers

- Die Schnittstelle verlangt HTTPS mit selbstsigniertem Zertifikat. Die App speichert es beim
  ersten Kontakt und akzeptiert danach nur noch genau dieses (Trust on first use).
- Der Wecker verträgt nur eine Verbindung gleichzeitig und braucht etwa 0,5 s pro Anfrage.
- Werden beim Speichern eines Weckers `ayear`/`amnth`/`alday` mit 0 gesendet, verschiebt das
  Gerät die Weckzeit um +6 h 28 min. Die App sendet diese Felder deshalb nie mit 0.
- Ein Sonnenaufgang des Geräts dauert mindestens 5 Minuten. Der 10-Sekunden-Aufgang des Naps
  wird deshalb von der App gesteuert.

## OnePlus-Hinweis

OxygenOS beendet Hintergrund-Jobs aggressiv. Damit Verlauf und Schlafzeiten lückenlos sind:
Einstellungen → Akku → Akkuoptimierung → Sunshine → „Nicht optimieren“.

## Lizenz

GPL-3.0, siehe [LICENSE](LICENSE). Schriften, Icons und Design-Grundlagen anderer Autoren
stehen unter eigenen Lizenzen, siehe [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Das Design folgt [unslop](https://github.com/heydamir/unslop) von Damir Sokolovsky.
