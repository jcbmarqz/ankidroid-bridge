# AnkiDroid Bridge

An Android app that exposes an HTTP server on `localhost:18765`, translating [AnkiConnect](https://foosoft.net/projects/anki-connect/)-compatible JSON requests to AnkiDroid's ContentProvider API.

This lets scripts that normally talk to Anki desktop (via AnkiConnect) work on Android with minimal changes.

## Why

I study Japanese using custom Python scripts that create cards, look up words across multiple decks, and generate audio. On desktop they talk to AnkiConnect. This app makes the same scripts work on my phone via Termux.

## Supported Actions

| Action | Status |
|--------|--------|
| `deckNames` | ✅ |
| `deckNamesAndIds` | ✅ |
| `createDeck` | ✅ |
| `findCards` | ✅ |
| `findNotes` | ✅ |
| `cardsInfo` | ✅ |
| `notesInfo` | ✅ |
| `addNote` | ✅ |
| `updateNoteFields` | ✅ |
| `modelNames` | ✅ |
| `modelFieldNames` | ✅ |
| `getTags` | ✅ |
| `storeMediaFile` | ✅ (filename gets mangled) |
| `openBrowser` | ✅ (only works when app is visible) |
| `answerCards` | ⚠️ No-op |
| `forgetCards` | ⚠️ No-op |

## Limitations

- **answerCards/forgetCards** are no-ops — the ContentProvider can't grade cards or reset scheduling
- **storeMediaFile** filenames get a random suffix (e.g., `word.mp3` → `word.mp3_1234567890.mp3`)
- **openBrowser** only works when the app is in the foreground (Android blocks background activity launches)
- **is:new** filter via ContentProvider is unreliable for distinguishing new vs suspended-new cards
- No model/note type creation
- No tag editing

## Building

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android 16 (API 36)
- AnkiDroid installed with the ContentProvider enabled
- "Display over other apps" permission granted (for headless start via intents)

## Usage

1. Install the APK
2. Launch the app once (required by Android to exit "stopped state")
3. Grant notification permission and "Display over other apps" when prompted
4. The server starts automatically on port 18765
5. From Termux or any local client:

```python
import requests

def anki(action, **params):
    r = requests.post('http://localhost:18765',
                      json={'action': action, 'version': 6, 'params': params})
    return r.json()['result']

# List decks
print(anki('deckNames'))

# Find cards
cards = anki('findCards', query='"deck:My Deck" is:new -is:suspended')

# Add a note
anki('addNote', note={
    'deckName': 'My Deck',
    'modelName': 'Basic',
    'fields': {'Front': 'hello', 'Back': 'こんにちは'}
})

# Store media (base64-encoded)
import base64
data = base64.b64encode(open('audio.mp3', 'rb').read()).decode()
actual_name = anki('storeMediaFile', filename='audio.mp3', data=data)
```

## Headless Control (Intents)

The server can be started and stopped without opening the app UI, useful for automation with Termux or Tasker.

There are two approaches:
- **Broadcast receiver** — works from foreground apps (like Termux where you're actively in a terminal)
- **Direct service intent** — works from background contexts (like Tasker tasks triggered by events)

Tasker must use the direct service approach because it sends intents from the background, where Android blocks broadcast receivers from starting foreground services.

### Termux

```bash
# Start
am broadcast -a com.jcbmarqz.ankidroidbridge.START_SERVER -n com.jcbmarqz.ankidroidbridge/.StartServerReceiver

# Stop
am broadcast -a com.jcbmarqz.ankidroidbridge.STOP_SERVER -n com.jcbmarqz.ankidroidbridge/.StopServerReceiver
```

### Tasker

Use the **Send Intent** action with Target: **Service**:

| Field | Start | Stop |
|-------|-------|------|
| Action | `com.jcbmarqz.ankidroidbridge.START_SERVER` | `com.jcbmarqz.ankidroidbridge.STOP_SERVER` |
| Package | `com.jcbmarqz.ankidroidbridge` | `com.jcbmarqz.ankidroidbridge` |
| Class | `com.jcbmarqz.ankidroidbridge.BridgeService` | `com.jcbmarqz.ankidroidbridge.BridgeService` |
| Target | Service | Service |

### Notes

- Both intents are idempotent (start when running = no-op, stop when stopped = no-op)
- The app must be launched manually once after install (Android restriction)
- "Display over other apps" permission is required for background starts

## Note

This is a personal project built for my own Japanese study workflow on a Samsung Galaxy Fold running Android 16. Some decisions (min SDK 36, single-device testing, specific deck structures) reflect that. It works well for my setup but isn't designed as a general-purpose tool. Feel free to fork and adapt.

## License

MIT
