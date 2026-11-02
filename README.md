# AmpyDeck ⌨

Tastiera Android cyberpunk della famiglia Ampy: comodità stile **SwiftKey**, look **AmpyLink / AmpyRocket**,
entrata **glitch + genie** e **sfondi animati** presi direttamente dal motore di AmpyRocket. Android 8 → Android 17.

## Scarica
**Releases → latest** e scarica `AmpyDeck.apk` dal telefono (oppure Actions → ultimo build → artifact `AmpyDeck-APK`).
Apri **AmpyDeck** → `1 · Abilita` → `2 · Scegli AmpyDeck`, poi prova nel campo di test.

## Stile SwiftKey
- Barra a **3 suggerimenti**: al centro ciò che inserisce lo spazio, a sinistra la parola “così com'è”, a destra l'alternativa
- **Predizione della parola successiva** che impara le tue coppie di parole
- **Autocorrezione** sullo spazio (vicinanza dei tasti, accenti: `perche → perché`, `citta → città`) e **⌫ subito dopo per annullare**
- **Flow**: scrivi scorrendo il dito sulle lettere, con scia neon; le alternative compaiono nella barra
- **Riga dei numeri**, **simboli sui tasti** (pressione lunga) e popup accenti `à è é ì ò ù ç ñ…`
- **Barra spaziatrice cursore** (scorri), **swipe a sinistra su ⌫** per cancellare la parola, ⌫ tenuto premuto che accelera
- Shift singolo / **doppio tocco = blocco maiuscole**, maiuscola automatica, **doppio spazio = punto**, punteggiatura intelligente
- **Mouse a schermo**: tocca 🖱 MOUSE sotto la tastiera (Android 16+) o ➶ nella toolbar: la tastiera diventa un touchpad e un puntatore a scelta (freccia neon, classica, cyber, mirino, anello, punto, manina) compare sopra ogni app; tocca = click, LUNGO, SCORRI, indietro/home/recenti (servizio di accessibilità "AmpyDeck Mouse", non legge lo schermo)
- **Trackpad**: tieni premuta la barra spaziatrice
- **Emoji** con scorrimento inerziale
- **Toolbar ⧉**: impostazioni, emoji, appunti, cambio tema e sfondo al volo, replay dell'animazione
- **Emoji** a categorie con recenti, **cronologia appunti** (solo in memoria, ignora i contenuti segnati come sensibili)
- Tieni premuto un suggerimento per **rimuoverlo**; dizionari **Italiano**, **English** o entrambi (wordfreq, ~45.000 parole)
- Niente apprendimento nei campi password / incognito, nessun permesso di rete

## Stile Ampy
- **Entrata Glitch + Genie**: la tastiera esce dalla “lampada” in basso con la mesh Neon Genie di AmpyRocket, poi split RGB,
  strappi orizzontali, flash di scansione e i tasti che si **decodificano lettera per lettera** (anche solo Genie o solo Glitch)
- **Sfondi animati AmpyRocket** dietro i tasti: griglia synthwave, pioggia digitale, rete di particelle, alveare esagonale, warp starfield + scanlines CRT
- Tasti a **pannello neon smussato** AmpyLink (oppure vetro, contorno, piatto), angoli accentati, glitch casuali sui tasti
- 6 temi condivisi con AmpyRocket / AmpyRAW: Cyberpunk, Vista Aero, Futuristic Glass, Retro, Minimal, Blood Moon

## Build
GitHub Actions compila a ogni push (`.github/workflows/android-apk.yml`). Firma stabile opzionale con i secrets
`AMPYDECK_KEYSTORE_B64`, `AMPYDECK_STORE_PASSWORD`, `AMPYDECK_KEY_ALIAS`, `AMPYDECK_KEY_PASSWORD`.
In locale: Android Studio, JDK 17, Gradle 8.13, SDK 36.
