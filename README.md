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

## Usage

1. Install the APK
2. Grant notification permission when prompted
3. The server starts automatically on port 18765
4. From Termux or any local client:

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

## Note

This is a personal project built for my own Japanese study workflow on a Samsung Galaxy Fold running Android 16. Some decisions (min SDK 36, single-device testing, specific deck structures) reflect that. It works well for my setup but isn't designed as a general-purpose tool. Feel free to fork and adapt.

## License

MIT
