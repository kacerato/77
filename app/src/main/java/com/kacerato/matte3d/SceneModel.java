package com.kacerato.matte3d;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SceneModel {
    private static final String PREFS = "matte3d_scene";
    private static final String KEY = "scene_json";

    private final ArrayList<SceneObject> objects = new ArrayList<>();
    private volatile int selectedIndex = -1;
    private long nextId = 1L;

    public static SceneModel load(Context context) {
        SceneModel scene = new SceneModel();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, null);
        if (raw != null) {
            try {
                JSONObject root = new JSONObject(raw);
                JSONArray array = root.getJSONArray("objects");
                for (int i = 0; i < array.length(); i++) {
                    SceneObject object = SceneObject.fromJson(array.getJSONObject(i));
                    scene.objects.add(object);
                    scene.nextId = Math.max(scene.nextId, object.id + 1L);
                }
                scene.selectedIndex = Math.min(root.optInt("selected", -1), scene.objects.size() - 1);
            } catch (Exception ignored) {
                scene.objects.clear();
                scene.selectedIndex = -1;
            }
        }

        if (scene.objects.isEmpty()) {
            SceneObject cube = scene.addObject(SceneObject.Type.CUBE, "Cube");
            cube.py = 0.55f;
            SceneObject plane = scene.addObject(SceneObject.Type.PLANE, "Ground");
            plane.sx = 5f;
            plane.sz = 5f;
            scene.select(0);
        }
        return scene;
    }

    public synchronized void save(Context context) {
        try {
            JSONObject root = new JSONObject();
            JSONArray array = new JSONArray();
            for (SceneObject object : objects) {
                array.put(object.toJson());
            }
            root.put("objects", array);
            root.put("selected", selectedIndex);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, root.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public synchronized SceneObject addObject(SceneObject.Type type, String baseName) {
        String name = uniqueName(baseName);
        SceneObject object = new SceneObject(nextId++, name, type);
        if (type == SceneObject.Type.CUBE) object.py = 0.5f;
        objects.add(object);
        selectedIndex = objects.size() - 1;
        return object;
    }

    public synchronized SceneObject duplicateSelected() {
        SceneObject selected = getSelected();
        if (selected == null) return null;
        SceneObject copy = selected.copy(nextId++, uniqueName(selected.name + " Copy"));
        objects.add(copy);
        selectedIndex = objects.size() - 1;
        return copy;
    }

    public synchronized void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= objects.size()) return;
        objects.remove(selectedIndex);
        if (objects.isEmpty()) selectedIndex = -1;
        else selectedIndex = Math.min(selectedIndex, objects.size() - 1);
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

    public synchronized void select(int index) {
        if (index >= 0 && index < objects.size()) selectedIndex = index;
    }

    private String uniqueName(String baseName) {
        String candidate = baseName;
        int suffix = 2;
        while (containsName(candidate)) {
            candidate = baseName + " " + suffix++;
        }
        return candidate;
    }

    private boolean containsName(String name) {
        for (SceneObject object : objects) {
            if (object.name.equals(name)) return true;
        }
        return false;
    }
}
