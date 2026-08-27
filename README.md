# Lexi

Un'app Android di flashcard per ampliare il vocabolario inglese - parole, phrasal verbs,
modi di dire ed espressioni - senza pubblicità e senza limiti sul numero di carte.
Locale, gratuita, open source.

## Il metodo di memorizzazione: FSRS

La ricerca sulla memoria a lungo termine converge su due tecniche: **retrieval practice**
(recupero attivo di un'informazione dalla memoria, non semplice rilettura) e
**spaced repetition** (ripassare un'informazione a intervalli crescenti, appena prima che
venga dimenticata). Le flashcard con autovalutazione, come quelle di Duocards, implementano
già la prima. Quello che cambia la qualità di un'app di flashcard è *quale algoritmo decide
gli intervalli*.

Lexi usa **FSRS (Free Spaced Repetition Scheduler)**, l'algoritmo di ripetizione dilazionata
più performante secondo i benchmark pubblici attuali:

- FSRS modella la probabilità di ricordare una carta con due parametri (*stability* e
  *difficulty*) stimati da un modello addestrato su centinaia di milioni di ripassi reali,
  invece delle formule fisse e uguali per tutti del classico algoritmo SM-2 (quello usato
  da Anki "vecchio stile" e da SuperMemo).
- Nei benchmark del progetto Open Spaced Repetition, FSRS raggiunge la stessa retention di
  SM-2 con circa il **20-30% di ripassi in meno**, ed è superiore a SM-2 per il 99,6% degli
  utenti testati (FSRS-6, con recency weighting).
- Da fine 2023 è l'algoritmo raccomandato di default in Anki, ed è diventato lo standard
  de facto per le app di ripetizione dilazionata.

Fonti: [FSRS vs SM-2 (antiagent.io)](https://www.antiagent.io/blog/fsrs-vs-sm-2),
[Benchmark del progetto Open Spaced Repetition](https://expertium.github.io/Benchmark.html),
[The FSRS Algorithm - wiki ufficiale](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm).

L'implementazione in `core/.../fsrs/FsrsScheduler.kt` è un porting fedele di
[`py-fsrs`](https://github.com/open-spaced-repetition/py-fsrs) (la libreria Python di
riferimento del progetto Open Spaced Repetition, licenza MIT) - stessi 21 parametri,
stesse formule. È verificato con 7 test che confrontano riga per riga i risultati con quelli
prodotti dalla libreria Python reale, a partire dagli stessi identici input (vedi
"Testing" più sotto).

## Struttura del progetto

```
core/   modulo Kotlin puro (nessuna dipendenza Android): algoritmo FSRS, parser di
        import (Duocards, Kindle), parser delle risposte delle API di dizionario/traduzione.
        Interamente testato con JUnit 5 - vedi core/src/test.
app/    app Android (Jetpack Compose + Room + Navigation), dipende da :core.
```

La logica critica (algoritmo di scheduling, parsing) vive in `:core`, senza dipendenze da
Android: puoi leggerla, testarla e fidarti del suo comportamento indipendentemente
dall'interfaccia.

## Come compilare

Questo progetto è stato sviluppato in un ambiente sandbox **senza accesso alla rete di
Google** (`dl.google.com`/`maven.google.com`, dove vivono AndroidX, Jetpack Compose e
l'Android Gradle Plugin) - solo Maven Central era raggiungibile. Per questo:

- **`:core` è stato compilato e testato in questo ambiente** (`./gradlew :core:test`,
  26 test, tutti verdi) perché usa solo Maven Central.
- **`:app` non è stato compilato qui** perché richiede Google Maven. Il codice è scritto
  seguendo le API stabili di AndroidX/Compose/Room per le versioni dichiarate in
  `app/build.gradle.kts`, ma **la prima build va fatta in un ambiente con accesso a
  internet normale** (il tuo PC/Mac, Android Studio) prima di fidarsi al 100%.

Per compilare ed eseguire l'app:

1. Apri la cartella del progetto in **Android Studio** (Koala o più recente).
2. Lascia che Gradle sincronizzi (scaricherà AndroidX, Compose, Room, ecc. da Google Maven).
3. Collega un telefono Android (o usa un emulatore) e premi Run.
4. Se Android Studio propone aggiornamenti automatici delle versioni di AGP/Compose/Room,
   accettarli è sicuro.

In alternativa da terminale, con un JDK 17+ e accesso di rete normale:

```bash
./gradlew :app:assembleDebug
# l'APK sarà in app/build/outputs/apk/debug/
```

## Testing

```bash
./gradlew :core:test
```

26 test coprono:
- **FSRS scheduler** (7 test): sequenze di ripassi (Again/Hard/Good/Easy, apprendimento,
  review, relearning, lapsus) confrontate valore per valore con l'output della libreria
  Python ufficiale `py-fsrs`, a parità di parametri e timestamp.
- **Import CSV/TSV** (5 test): delimitatore automatico, intestazione opzionale, campi tra
  virgolette, colonne mancanti.
- **Import Kindle** (7 test): accoppiamento evidenziazione+nota, formato inglese e
  italiano, evidenziazioni senza nota, segnalibri ignorati, deduplica.
- **Parsing risposte dizionario/traduzione** (7 test): risposte valide e malformate delle
  API esterne.

## Importare i tuoi vocaboli

Dalla schermata **Importa** puoi scegliere tra due formati:

### Da Duocards

Duocards non pubblica un formato di export ufficiale stabile, quindi il parser è
volutamente flessibile: rileva da solo se il file è separato da virgole, punti e virgola
o tab, se c'è una riga di intestazione (front/back, question/answer, ecc.) o no, e prende
la prima colonna come termine e la seconda come traduzione (una terza colonna opzionale
viene letta come frase d'esempio). Se il tuo export ha un formato diverso da quello
previsto, dimmelo (o incolla qui un estratto anonimizzato) e sistemo il parser sul formato
reale.

### Da Kindle

Collega il Kindle al computer via USB e copia il file `documents/My Clippings.txt`
(o mandatelo al telefono). L'app riconosce automaticamente le evidenziazioni ("Highlight")
e, se hai aggiunto una nota subito dopo aver evidenziato un termine (es. la sua traduzione),
la accoppia automaticamente creando fronte=evidenziazione, retro=nota. Le evidenziazioni
senza nota diventano carte con il retro vuoto, da completare a mano o con
l'auto-completamento. Il parser riconosce sia il formato inglese ("Highlight", "Location")
sia quello italiano ("evidenziazione", "posizione"). Segnalibri vengono ignorati, ed
evidenziare due volte la stessa parola non crea doppioni.

### Auto-completamento

Per le carte importate senza traduzione (o quando premi "Auto-completa" nel form di una
carta), l'app interroga due servizi online gratuiti (nessuna chiave richiesta):
[dictionaryapi.dev](https://dictionaryapi.dev) per definizione/esempio in inglese e
[MyMemory](https://mymemory.translated.net) per la traduzione in italiano. Puoi disattivare
questo comportamento nelle Impostazioni. Nota: MyMemory ha un limite di utilizzo gratuito
giornaliero: per import molto grandi potrebbe smettere di tradurre a metà, in quel caso
riprova il giorno dopo o completa a mano le carte rimaste.

## Limiti noti / possibili sviluppi futuri

- Nessun sync tra dispositivi (i dati vivono solo nel database locale del telefono);
  un backup/export manuale è un'estensione naturale da aggiungere.
- Nessun audio di pronuncia.
- Le note vocali/immagini non sono supportate.
- Import di grandi quantità di carte con auto-completamento attivo può richiedere qualche
  minuto (le chiamate alle API gratuite sono sequenziali per rispettarne i limiti).
