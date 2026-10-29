# H4XTOR OLED Remote v3.0 — Samsung Galaxy S24 Ultra

Android-app til **Philips 65OLED865/12**, JointSpace v6 (observeret API 6.4.0).

## Installer APK
1. Åbn [Android CI](https://github.com/h4xtor/Universal-Philips-OLED-TV-Remote/actions/workflows/android.yml).
2. Vælg **seneste grønne kørsel på main**.
3. Download `h4xtor-philips-debug-apk` under **Artifacts** og udpak ZIP.
4. Installér `app-debug.apk` på Galaxy S24 Ultra. Hvis du får advarsel, skal du tillade installation fra filhåndteringen. APK'en er debug-signeret (ikke Play Store-signeret).
5. Telefon og TV skal være på samme LAN. Start med TV tændt, gå til **TOOLS**, kontrollér `192.168.0.110` og tryk **PAR TV**; indtast PIN fra TV.
6. For Wake-on-LAN indtastes **TV'ets korrekte MAC-adresse** under TOOLS. Aktivér TV'ets netværksstandby/remote start hvis tilgængelig. Test først efter normal standby, ikke afbrudt strøm.

## Funktioner

| Funktion | Implementering |
|---|---|
| Premium mørk fjernbetjening, touch/haptics, D-pad, OK, Back, Home | Implementeret |
| Standby, Smart Wake med WOL/UDP9 og online-probe | Implementeret; TV/firmware-afhængig |
| Volumen, mute, direkte volumenskyder, kanal +/- og medietaster | JointSpace API v6 |
| Tre redigerbare M1–M3-makroer med validerede tastetryk | Lokal lagring og sekventiel afsendelse |
| Kanaloversigt, søgning, direkte valg, lokale favoritter | TV's egen kanaldatabase |
| Installerede apps, søgning, start fra menu, hurtiggenveje | TV's egne intents |
| Ambilight power, profiler, læs aktiv konfiguration, live supportedstyles | JointSpace API v6 |
| Direkte input Live TV / HDMI 1–4 | Eksperimentelt på tværs af firmware |
| PIN-parring og HTTP Digest over HTTPS | Credentials lagret i Android Keystore AES-GCM |
| Certifikat TOFU-pinning med SHA-256 og eksplicit reset | Implementeret (første forbindelse kræver betroet LAN) |
| Specialtaster og numerisk tastatur | Implementeret |
| Tekstindtastning til TV apps | Ikke understøttet på denne firmware; appen påstår ikke andet |

**TV-funktioner kan ikke garanteres uden fysisk test på den konkrete firmware.** Mislykkede API-kald rapporteres med fejl. WOL kan ikke vække et TV, hvis den aktuelle netværksgrænseflade mister strøm i standby.

## Sikkerhed
Appen bruger kun lokal netværkstrafik og ingen cloud/analytics. TV-systemopslag går til HTTP port 1925 (uden credentials); øvrige kald bruger HTTPS port 1926 med Digest Authentication. Philips anvender ofte selvsigneret certifikat; appen gemmer SHA-256 fingeraftrykket ved første forbindelse (TOFU) og afviser senere ændringer, indtil brugeren eksplicit nulstiller pinnen. Første parring bør kun udføres på betroet Wi-Fi. Ingen MAC-adresse eller login er indlejret i kildekoden.

## Android Studio
Åbn `android/` i Android Studio (Gradle 8.11.1, JDK 17, Android SDK 35), eller byg via workflow i `.github/workflows/android.yml`.

App-ID: `dk.h4xtor.philipsremote`, minSdk 26, targetSdk 35.

## Afgrænsninger
Ingen test mod fysisk TV eller S24 Ultra er udført af GitHub Actions. Grøn CI bekræfter kompilering og lint, ikke funktionalitet på enhederne. Android TV-tekstinput er deaktiveret, fordi det tidligere TV API oplyste `textentry:not_available`. En kanal kan kun vælges, hvis Philips' kanaldatabase returnerer den.

## Fejlfinding
- **TV offline:** Tjek samme Wi-Fi, TV IP, og at client isolation/guest network er slået fra.
- **PAR TV:** TV skal være tændt, PIN-flow skal afsluttes inden timeout.
- **Certifikat ændret:** Kontrollér TV-IP før du nulstiller certifikatpin.
- **Ingen kanaler/apps:** Tryk SYNC efter parring. Apps og kanaloplysninger afhænger af Philips API.
- **Wake mislykkes:** Kontrollér MAC for det aktive interface, TV'ets standbyindstillinger, multicast/broadcast-filtrering, og om TV'et er på samme subnet.

## Projektets oprindelse
Funktionerne er baseret på den tidligere H4XTOR PhilipsTVRemote2026 Windows/Android-reference og migreret til en dedikeret S24 Ultra-app.
