# Onda Launcher

Launcher Android minimale ispirato al concept dei launcher "a lista" (stile Niagara), scritto da zero in Kotlin + Jetpack Compose.

## Funzioni (v0.1)

| Area | Cosa fa |
|---|---|
| **Home** | Orologio e data (tocco → sveglie / calendario), widget, lista verticale dei preferiti |
| **Scroller a onda** | Colonna A–Z sul bordo: trascinando, le lettere vicine al dito si gonfiano e scorrono verso il centro, con vibrazione a ogni lettera e apertura dell'elenco completo |
| **Notifiche in lista** | Sotto ogni preferito compare l'ultima notifica (titolo + testo, contatore "+N"); tocco = apre, × = ignora. Si possono silenziare per app |
| **Ricerca** | Swipe verso l'alto o "Cerca": app (anche nascoste, per iniziali e testo parziale), contatti, ricerca web. Invio = apre il primo risultato |
| **Gesti** | Swipe giù = tendina notifiche · swipe su = ricerca · pressione lunga su spazio vuoto = menu home |
| **Opzioni app** (pressione lunga) | Scorciatoie dell'app, preferiti, riordino, rinomina, nascondi, silenzia notifiche, widget dell'app, info, disinstalla |
| **Widget** | Widget di sistema in home, con selettore e flusso di consenso e configurazione |
| **Personalizzazione** | Tema sistema/chiaro/scuro, 6 colori d'accento, icone on/off, dimensione testo, oscuramento sfondo, modalità mancini, gestione delle app nascoste |
| **Profilo di lavoro** | Le app del profilo di lavoro sono incluse, con etichetta "lavoro" |

## Compilare l'APK

### Opzione A: Android Studio (consigliata)
1. Apri la cartella `OndaLauncher` in Android Studio (Ladybug o successivo).
2. Attendi la sincronizzazione Gradle (scarica in automatico SDK 35 e le dipendenze).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. L'APK si trova in `app/build/outputs/apk/debug/app-debug.apk`.

### Opzione B: GitHub Actions (senza installare nulla)
1. Crea un repository su GitHub e caricaci il contenuto della cartella (deve includere `.github/workflows/build-apk.yml`).
2. A ogni push parte il workflow **Build APK**.
3. Al termine scarica l'artefatto **OndaLauncher-debug** dalla pagina dell'esecuzione (tab *Actions*).

### Opzione C: riga di comando
```bash
./gradlew assembleDebug        # richiede JDK 17 e ANDROID_HOME impostato
```

## Primo avvio
1. Installa l'APK (abilita "Installa app sconosciute" per il file manager o il browser).
2. Premi Home e scegli **Onda** → *Sempre*, oppure usa il pulsante "Imposta Onda come home".
3. Tocca "Mostra le notifiche qui" e attiva l'accesso alle notifiche per Onda.
4. Le scorciatoie delle app compaiono solo quando Onda è il launcher predefinito (limite di Android).

## Struttura
```
app/src/main/java/com/gabrielepiccione/onda/
├── OndaApp.kt                 Application, crea i repository
├── MainActivity.kt            Activity HOME, permessi, flussi widget
├── LauncherViewModel.kt       Stato unificato (app + preferenze + notifiche), ricerca
├── data/
│   ├── AppRepository.kt       LauncherApps: elenco app, avvio, scorciatoie
│   ├── SettingsRepository.kt  Preferenze persistenti
│   ├── NotificationStore.kt   Notifiche attive per pacchetto
│   ├── ContactsRepository.kt  Ricerca contatti
│   ├── WidgetManager.kt       AppWidgetHost
│   └── Models.kt
├── service/OndaNotificationListener.kt
├── ui/                        Compose: Home, Scroller a onda, Ricerca, Impostazioni, Widget
└── util/SystemActions.kt      Tendina notifiche, disinstalla, ricerca web…
```

## Note
- Codice, nome, icona e grafica sono originali: dell'app di riferimento è stato ripreso solo il concept d'uso.
- La release è firmata con la chiave di debug, quindi va bene per l'uso personale. Per distribuirla, configura una tua `signingConfig`.
