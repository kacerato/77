package com.kacerato.matte3d;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class SceneModel {
    private static final String PREFS = "matte3d_scene";
    private static final String KEY = "scene_json";
    private static final int MAX_HISTORY = 40;

    private final ArrayList<SceneObject> objects = new ArrayList<>();
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private volatile int selectedIndex = -1;
    private long nextId = 1L;

    public static SceneModel load(Context context) {
        SceneModel scene = new SceneModel();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, null);
        if (raw != null) {
            try {
                scene.restoreJsonInternal(raw);
            } catch (Exception ignored) {
                scene.objects.clear();
                scene.selectedIndex = -1;
                scene.nextId = 1L;
            }
        }

        if (scene.objects.isEmpty()) {
            SceneObject cube = scene.addObjectInternal(SceneObject.Type.CUBE, "Cube");
            cube.py = 0.5f;
            SceneObject plane = scene.addObjectInternal(SceneObject.Type.PLANE, "Ground");
            plane.sx = 6f;
            plane.sz = 6f;
            plane.locked = true;
            scene.selectedIndex = 0;
        }
        scene.undoStack.clear();
        scene.redoStack.clear();
        return scene;
    }

    public synchronized void save(Context context) {
        String raw = captureJsonInternal();
        if (raw == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, raw)
                .apply();
    }

    public synchronized void recordUndoPoint() {
        String state = captureJsonInternal();
        if (state == null) return;
        if (!undoStack.isEmpty() && undoStack.peek().equals(state)) return;
        undoStack.push(state);
        while (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        redoStack.clear();
    }

    public synchronized boolean undo() {
        if (undoStack.isEmpty()) return false;
        String current = captureJsonInternal();
        if (current != null) redoStack.push(current);
        String previous = undoStack.pop();
        try {
            restoreJsonInternal(previous);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized boolean redo() {
        if (redoStack.isEmpty()) return false;
        String current = captureJsonInternal();
        if (current != null) {
            undoStack.push(current);
            while (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        }
        String next = redoStack.pop();
        try {
            restoreJsonInternal(next);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public synchronized SceneObject addObject(SceneObject.Type type, String baseName) {
        recordUndoPoint();
        return addObjectInternal(type, baseName);
    }

    private SceneObject addObjectInternal(SceneObject.Type type, String baseName) {
        String name = uniqueName(baseName);
        SceneObject object = new SceneObject(nextId++, name, type);
        if (type != SceneObject.Type.PLANE) object.py = 0.5f;
        objects.add(object);
        selectedIndex = objects.size() - 1;
        return object;
    }

    public synchronized SceneObject duplicateSelected() {
        SceneObject selected = getSelected();
        if (selected == null) return null;
        recordUndoPoint();
        SceneObject copy = selected.copy(nextId++, uniqueName(selected.name + " Copy"));
        objects.add(copy);
        selectedIndex = objects.size() - 1;
        return copy;
    }

    public synchronized boolean deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= objects.size()) return false;
        recordUndoPoint();
        objects.remove(selectedIndex);
        if (objects.isEmpty()) selectedIndex = -1;
        else selectedIndex = Math.min(selectedIndex, objects.size() - 1);
        return true;
    }

    public synchronized boolean clearAll() {
        if (objects.isEmpty()) return false;
        recordUndoPoint();
        objects.clear();
        selectedIndex = -1;
        return true;
    }

    public synchronized List<SceneObject> snapshot() {
        return new ArrayList<>(objects);
    }

    public synchronized SceneObject getSelected() {
        if (selectedIndex < 0 || selectedIndex >= objects.size()) return null;
        return objects.get(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public synchronized int objectCount() {
        return objects.size();
    }

    public synchronized void select(int index) {
        if (index >= 0 && index < objects.size()) selectedIndex = index;
    }

    public synchronized void clearSelection() {
        selectedIndex = -1;
    }

    public synchronized boolean renameSelected(String newName) {
        SceneObject selected = getSelected();
        if (selected == null) return false;
        String clean = newName == null ? "" : newName.trim();
        if (clean.isEmpty() || clean.equals(selected.name)) return false;
        recordUndoPoint();
        selected.name = clean;
        return true;
    }

    public synchronized boolean toggleVisible(int index) {
        if (index < 0 || index >= objects.size()) return false;
        recordUndoPoint();
        objects.get(index).visible = !objects.get(index).visible;
        return true;
    }

    public synchronized boolean toggleLocked(int index) {
        if (index < 0 || index >= objects.size()) return false;
        recordUndoPoint();
        objects.get(index).locked = !objects.get(index).locked;
        return true;
    }

    public synchronized boolean setSelectedTransform(int mode, int axis, float value) {
        SceneObject o = getSelected();
        if (o == null || o.locked || axis < 0 || axis > 2) return false;
        if (mode == 2) value = Math.max(0.05f, value);
        float current = getTransformValue(o, mode, axis);
        if (Math.abs(current - value) < 0.0001f) return false;
        recordUndoPoint();
        setTransformValue(o, mode, axis, value);
        return true;
    }

    public synchronized boolean resetSelectedTransform() {
        SceneObject o = getSelected();
        if (o == null || o.locked) return false;
        recordUndoPoint();
        o.px = o.py = o.pz = 0f;
        if (o.type != SceneObject.Type.PLANE) o.py = 0.5f;
        o.rx = o.ry = o.rz = 0f;
        o.sx = o.sy = o.sz = 1f;
        return true;
    }

    private static float getTransformValue(SceneObject o, int mode, int axis) {
        if (mode == 0) return axis == 0 ? o.px : axis == 1 ? o.py : o.pz;
        if (mode == 1) return axis == 0 ? o.rx : axis == 1 ? o.ry : o.rz;
        return axis == 0 ? o.sx : axis == 1 ? o.sy : o.sz;
    }

    private static void setTransformValue(SceneObject o, int mode, int axis, float value) {
        if (mode == 0) {
            if (axis == 0) o.px = value;
            else if (axis == 1) o.py = value;
            else o.pz = value;
        } else if (mode == 1) {
            if (axis == 0) o.rx = value;
            else if (axis == 1) o.ry = value;
            else o.rz = value;
        } else {
            if (axis == 0) o.sx = value;
            else if (axis == 1) o.sy = value;
            else o.sz = value;
        }
    }

    private String captureJsonInternal() {
        try {
            JSONObject root = new JSONObject();
            JSONArray array = new JSONArray();
            for (SceneObject object : objects) array.put(object.toJson());
            root.put("objects", array);
            root.put("selected", selectedIndex);
            root.put("nextId", nextId);
            return root.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void restoreJsonInternal(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        JSONArray array = root.getJSONArray("objects");
        objects.clear();
        nextId = 1L;
        for (int i = 0; i < array.length(); i++) {
            SceneObject object = SceneObject.fromJson(array.getJSONObject(i));
            objects.add(object);
            nextId = Math.max(nextId, object.id + 1L);
        }
        nextId = Math.max(nextId, root.optLong("nextId", nextId));
        int requested = root.optInt("selected", -1);
        selectedIndex = requested >= 0 && requested < objects.size() ? requested : -1;
    }

    private String uniqueName(String baseName) {
        String candidate = baseName;
        int suffix = 2;
        while (containsName(candidate)) candidate = baseName + " " + suffix++;
        return candidate;
    }

    private boolean containsName(String name) {
        for (SceneObject object : objects) {
            if (object.name.equals(name)) return true;
        }
        return false;
    }
}
