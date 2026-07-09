package com.jcbmarqz.ankidroidbridge;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class BridgeServer extends NanoHTTPD {

    private static final String TAG = "BridgeServer";
    private static final Uri NOTES_URI = Uri.parse("content://com.ichi2.anki.flashcards/notes");
    private static final Uri CARDS_URI = Uri.parse("content://com.ichi2.anki.flashcards/cards");
    private static final Uri MODELS_URI = Uri.parse("content://com.ichi2.anki.flashcards/models");
    private static final Uri DECKS_URI = Uri.parse("content://com.ichi2.anki.flashcards/decks");
    private static final Uri SCHEDULE_URI = Uri.parse("content://com.ichi2.anki.flashcards/schedule");
    private static final Uri MEDIA_URI = Uri.parse("content://com.ichi2.anki.flashcards/media");

    private final Context context;
    private final Map<String, ActionHandler> handlers = new HashMap<>();

    public interface ActionHandler {
        Object handle(JSONObject params) throws Exception;
    }

    public BridgeServer(Context context, int port) {
        super(port);
        this.context = context;
        registerHandlers();
    }

    private ContentResolver cr() {
        return context.getContentResolver();
    }

    private void log(String message) {
        Log.i(TAG, message);
        Intent intent = new Intent(BridgeService.ACTION_LOG);
        intent.setPackage(context.getPackageName());
        intent.putExtra(BridgeService.EXTRA_MESSAGE, message);
        context.sendBroadcast(intent);
    }

    private void registerHandlers() {
        handlers.put("deckNames", this::handleDeckNames);
        handlers.put("deckNamesAndIds", this::handleDeckNamesAndIds);
        handlers.put("findCards", this::handleFindCards);
        handlers.put("findNotes", this::handleFindNotes);
        handlers.put("cardsInfo", this::handleCardsInfo);
        handlers.put("notesInfo", this::handleNotesInfo);
        handlers.put("addNote", this::handleAddNote);
        handlers.put("updateNoteFields", this::handleUpdateNoteFields);
        handlers.put("modelNames", this::handleModelNames);
        handlers.put("modelFieldNames", this::handleModelFieldNames);
        handlers.put("getTags", this::handleGetTags);
        handlers.put("createDeck", this::handleCreateDeck);
        handlers.put("answerCards", this::handleAnswerCards);
        handlers.put("forgetCards", this::handleForgetCards);
        handlers.put("storeMediaFile", this::handleStoreMediaFile);
        handlers.put("openBrowser", this::handleOpenBrowser);
    }

    // === deckNames ===
    private Object handleDeckNames(JSONObject params) throws Exception {
        JSONArray result = new JSONArray();
        try (Cursor c = cr().query(DECKS_URI, new String[]{"deck_name"}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    result.put(c.getString(0));
                }
            }
        }
        return result;
    }

    // === deckNamesAndIds ===
    private Object handleDeckNamesAndIds(JSONObject params) throws Exception {
        JSONObject result = new JSONObject();
        try (Cursor c = cr().query(DECKS_URI, new String[]{"deck_id", "deck_name"}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    result.put(c.getString(1), c.getLong(0));
                }
            }
        }
        return result;
    }

    // === findNotes ===
    private Object handleFindNotes(JSONObject params) throws Exception {
        String query = params.getString("query");
        JSONArray result = new JSONArray();
        try (Cursor c = cr().query(NOTES_URI, new String[]{"_id"}, query, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    result.put(c.getLong(0));
                }
            }
        }
        return result;
    }

    // === modelNames ===
    private Object handleModelNames(JSONObject params) throws Exception {
        JSONArray result = new JSONArray();
        try (Cursor c = cr().query(MODELS_URI, new String[]{"name"}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    result.put(c.getString(0));
                }
            }
        }
        return result;
    }

    // === modelFieldNames ===
    private Object handleModelFieldNames(JSONObject params) throws Exception {
        String modelName = params.getString("modelName");
        long modelId = findModelIdByName(modelName);
        if (modelId == -1) return new JSONArray();
        String[] names = getModelFieldNames(modelId);
        JSONArray result = new JSONArray();
        for (String name : names) result.put(name);
        return result;
    }

    // === getTags ===
    private Object handleGetTags(JSONObject params) throws Exception {
        java.util.Set<String> tags = new java.util.HashSet<>();
        try (Cursor c = cr().query(NOTES_URI, new String[]{"_id"}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long noteId = c.getLong(0);
                    Uri noteUri = Uri.withAppendedPath(NOTES_URI, String.valueOf(noteId));
                    try (Cursor nc = cr().query(noteUri, new String[]{"tags"}, null, null, null)) {
                        if (nc != null && nc.moveToFirst()) {
                            String t = nc.getString(0);
                            if (t != null && !t.isEmpty()) {
                                for (String tag : t.trim().split("\\s+")) {
                                    if (!tag.isEmpty()) tags.add(tag);
                                }
                            }
                        }
                    }
                }
            }
        }
        JSONArray result = new JSONArray();
        for (String tag : tags) result.put(tag);
        return result;
    }

    // === findCards ===
    private Object handleFindCards(JSONObject params) throws Exception {
        String query = params.getString("query");
        JSONArray result = new JSONArray();
        // Query notes matching the search, then get their card IDs
        try (Cursor c = cr().query(NOTES_URI, new String[]{"_id"}, query, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long noteId = c.getLong(0);
                    // Get cards for this note
                    Uri cardsUri = Uri.withAppendedPath(NOTES_URI, noteId + "/cards");
                    try (Cursor cc = cr().query(cardsUri, new String[]{"_id"}, null, null, null)) {
                        if (cc != null) {
                            while (cc.moveToNext()) {
                                result.put(cc.getLong(0));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    // === cardsInfo ===
    private Object handleCardsInfo(JSONObject params) throws Exception {
        JSONArray cardIds = params.getJSONArray("cards");
        JSONArray result = new JSONArray();
        for (int i = 0; i < cardIds.length(); i++) {
            long cardId = cardIds.getLong(i);
            JSONObject cardInfo = getCardInfo(cardId);
            if (cardInfo != null) {
                result.put(cardInfo);
            }
        }
        return result;
    }

    private JSONObject getCardInfo(long cardId) throws Exception {
        Uri cardUri = Uri.withAppendedPath(CARDS_URI, String.valueOf(cardId));
        try (Cursor c = cr().query(cardUri, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;

            JSONObject info = new JSONObject();
            info.put("cardId", cardId);
            info.put("note", c.getLong(c.getColumnIndexOrThrow("note_id")));
            int queueIdx = c.getColumnIndex("queue");
            info.put("queue", queueIdx >= 0 ? c.getInt(queueIdx) : 0);

            // Get fields from the note
            long noteId = c.getLong(c.getColumnIndexOrThrow("note_id"));
            info.put("fields", getNoteFields(noteId));
            return info;
        }
    }

    // === notesInfo ===
    private Object handleNotesInfo(JSONObject params) throws Exception {
        JSONArray noteIds = params.getJSONArray("notes");
        JSONArray result = new JSONArray();
        for (int i = 0; i < noteIds.length(); i++) {
            long noteId = noteIds.getLong(i);
            JSONObject noteInfo = new JSONObject();
            noteInfo.put("noteId", noteId);
            noteInfo.put("fields", getNoteFields(noteId));
            result.put(noteInfo);
        }
        return result;
    }

    private JSONObject getNoteFields(long noteId) throws Exception {
        JSONObject fields = new JSONObject();
        Uri noteUri = Uri.withAppendedPath(NOTES_URI, String.valueOf(noteId));
        try (Cursor c = cr().query(noteUri, new String[]{"mid", "flds"}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return fields;

            long mid = c.getLong(0);
            String flds = c.getString(1);
            String[] fieldValues = flds.split("\u001f", -1);

            // Get field names from model
            String[] fieldNames = getModelFieldNames(mid);
            for (int i = 0; i < fieldNames.length && i < fieldValues.length; i++) {
                JSONObject fieldObj = new JSONObject();
                fieldObj.put("value", fieldValues[i]);
                fields.put(fieldNames[i], fieldObj);
            }
        }
        return fields;
    }

    private final Map<Long, String[]> modelFieldCache = new HashMap<>();

    private String[] getModelFieldNames(long modelId) throws Exception {
        if (modelFieldCache.containsKey(modelId)) {
            return modelFieldCache.get(modelId);
        }
        Uri modelUri = Uri.withAppendedPath(MODELS_URI, String.valueOf(modelId));
        try (Cursor c = cr().query(modelUri, new String[]{"field_names"}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String[] names = c.getString(0).split("\u001f");
                modelFieldCache.put(modelId, names);
                return names;
            }
        }
        return new String[0];
    }

    // === addNote ===
    private Object handleAddNote(JSONObject params) throws Exception {
        JSONObject note = params.getJSONObject("note");
        String deckName = note.getString("deckName");
        String modelName = note.getString("modelName");
        JSONObject fields = note.getJSONObject("fields");

        // Resolve model ID and get field names
        long modelId = findModelIdByName(modelName);
        if (modelId == -1) return null;

        // Resolve deck ID
        long deckId = findDeckIdByName(deckName);
        if (deckId == -1) return null;

        // Build FLDS string in correct order
        String[] fieldNames = getModelFieldNames(modelId);
        StringBuilder flds = new StringBuilder();
        for (int i = 0; i < fieldNames.length; i++) {
            if (i > 0) flds.append("\u001f");
            flds.append(fields.optString(fieldNames[i], ""));
        }

        // Insert note using bulkInsert with deckId parameter
        ContentValues values = new ContentValues();
        values.put("mid", modelId);
        values.put("flds", flds.toString());

        Uri insertUri = NOTES_URI.buildUpon()
                .appendQueryParameter("deckId", String.valueOf(deckId))
                .build();
        int count = cr().bulkInsert(insertUri, new ContentValues[]{values});
        if (count > 0) {
            // Find the note by flds match
            Uri v2 = Uri.parse("content://com.ichi2.anki.flashcards/notes_v2");
            try (Cursor c = cr().query(v2, new String[]{"_id"},
                    "mid = " + modelId + " AND flds = ?", new String[]{flds.toString()}, null)) {
                if (c != null && c.moveToLast()) {
                    return c.getLong(0);
                }
            }
        }
        return null;
    }

    private long findModelIdByName(String name) throws Exception {
        try (Cursor c = cr().query(MODELS_URI, new String[]{"_id", "name"}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    if (name.equals(c.getString(1))) {
                        return c.getLong(0);
                    }
                }
            }
        }
        return -1;
    }

    private long findDeckIdByName(String name) throws Exception {
        try (Cursor c = cr().query(DECKS_URI, new String[]{"deck_id", "deck_name"}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    if (name.equals(c.getString(1))) {
                        return c.getLong(0);
                    }
                }
            }
        }
        return -1;
    }

    // === updateNoteFields ===
    private Object handleUpdateNoteFields(JSONObject params) throws Exception {
        JSONObject note = params.getJSONObject("note");
        long noteId = note.getLong("id");
        JSONObject fields = note.getJSONObject("fields");

        // Get current note to find model
        Uri noteUri = Uri.withAppendedPath(NOTES_URI, String.valueOf(noteId));
        long mid;
        try (Cursor c = cr().query(noteUri, new String[]{"mid", "flds"}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            mid = c.getLong(0);
        }

        // Build updated FLDS
        String[] fieldNames = getModelFieldNames(mid);
        // Get current field values
        String[] currentValues = new String[fieldNames.length];
        try (Cursor c = cr().query(noteUri, new String[]{"flds"}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String[] vals = c.getString(0).split("\u001f", -1);
                System.arraycopy(vals, 0, currentValues, 0, Math.min(vals.length, currentValues.length));
            }
        }

        // Apply updates
        for (int i = 0; i < fieldNames.length; i++) {
            if (fields.has(fieldNames[i])) {
                currentValues[i] = fields.getString(fieldNames[i]);
            }
        }

        StringBuilder flds = new StringBuilder();
        for (int i = 0; i < currentValues.length; i++) {
            if (i > 0) flds.append("\u001f");
            flds.append(currentValues[i] != null ? currentValues[i] : "");
        }

        ContentValues values = new ContentValues();
        values.put("flds", flds.toString());
        cr().update(noteUri, values, null, null);
        return null;
    }

    // === createDeck ===
    private Object handleCreateDeck(JSONObject params) throws Exception {
        String deckName = params.getString("deck");
        ContentValues values = new ContentValues();
        values.put("deck_name", deckName);
        Uri result = cr().insert(DECKS_URI, values);
        return result != null ? Long.parseLong(result.getLastPathSegment()) : null;
    }

    // === answerCards ===
    private Object handleAnswerCards(JSONObject params) throws Exception {
        // No-op: grading is done manually via openBrowser
        return null;
    }

    // === forgetCards ===
    private Object handleForgetCards(JSONObject params) throws Exception {
        // No-op: forgetting is done manually via openBrowser
        return null;
    }

    // === storeMediaFile ===
    private Object handleStoreMediaFile(JSONObject params) throws Exception {
        String filename = params.getString("filename");
        String dataBase64 = params.getString("data");
        byte[] data = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT);

        // Write to cache dir
        java.io.File tempFile = new java.io.File(context.getCacheDir(), filename);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            fos.write(data);
        }

        // Get a content URI via FileProvider
        Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", tempFile);

        // Grant read permission to AnkiDroid
        context.grantUriPermission("com.ichi2.anki", fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        ContentValues values = new ContentValues();
        values.put("file_uri", fileUri.toString());
        values.put("preferred_name", filename);

        try {
            Uri result = cr().insert(MEDIA_URI, values);
            if (result != null) {
                // AnkiDroid returns file:///mangled_name — extract just the filename
                String path = result.getLastPathSegment();
                return path != null ? path : filename;
            }
            return null;
        } finally {
            tempFile.delete();
        }
    }

    // === openBrowser ===
    private Object handleOpenBrowser(JSONObject params) throws Exception {
        JSONArray cardIds = params.getJSONArray("cards");
        StringBuilder cids = new StringBuilder();
        for (int i = 0; i < cardIds.length(); i++) {
            if (i > 0) cids.append(",");
            cids.append(cardIds.getLong(i));
        }
        String search = "deck:* cid:" + cids;
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("anki://x-callback-url/browser?search=" + Uri.encode(search)));
            intent.setPackage("com.ichi2.anki");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });
        return null;
    }

    // === HTTP handling ===

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() != Method.POST) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "POST only");
        }

        try {
            int contentLength = Integer.parseInt(session.getHeaders().getOrDefault("content-length", "0"));
            byte[] buf = new byte[contentLength];
            session.getInputStream().read(buf, 0, contentLength);
            String json = new String(buf);

            JSONObject request = new JSONObject(json);
            String action = request.optString("action", "");
            JSONObject reqParams = request.optJSONObject("params");
            if (reqParams == null) reqParams = new JSONObject();

            ActionHandler handler = handlers.get(action);
            if (handler == null) {
                log("<< " + action + " (unknown)");
                return jsonResponse(errorResult("unsupported action: " + action));
            }

            log(">> " + action);
            log("   " + reqParams.toString());
            Object result = handler.handle(reqParams);
            String resultStr = result != null ? result.toString() : "null";
            if (resultStr.length() > 500) resultStr = resultStr.substring(0, 500) + "...";
            log("<< " + action);
            log("   " + resultStr);
            return jsonResponse(successResult(result));

        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            log("<< ERROR");
            log("   " + e.getMessage());
            return jsonResponse(errorResult(e.getMessage()));
        }
    }

    private String successResult(Object result) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("result", result != null ? result : JSONObject.NULL);
            resp.put("error", JSONObject.NULL);
            return resp.toString();
        } catch (Exception e) {
            return "{\"result\":null,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String errorResult(String error) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("result", JSONObject.NULL);
            resp.put("error", error);
            return resp.toString();
        } catch (Exception e) {
            return "{\"result\":null,\"error\":\"" + error + "\"}";
        }
    }

    private Response jsonResponse(String json) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }
}
