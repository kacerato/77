package com.kacerato.matte3d;

import org.json.JSONException;
import org.json.JSONObject;

public final class SceneObject {
    public enum Type { CUBE, PLANE }

    public final long id;
    public String name;
    public final Type type;

    public volatile float px;
    public volatile float py;
    public volatile float pz;
    public volatile float rx;
    public volatile float ry;
    public volatile float rz;
    public volatile float sx = 1f;
    public volatile float sy = 1f;
    public volatile float sz = 1f;

    public SceneObject(long id, String name, Type type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public SceneObject copy(long newId, String newName) {
        SceneObject out = new SceneObject(newId, newName, type);
        out.px = px + 0.5f;
        out.py = py;
        out.pz = pz + 0.5f;
        out.rx = rx;
        out.ry = ry;
        out.rz = rz;
        out.sx = sx;
        out.sy = sy;
        out.sz = sz;
        return out;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("type", type.name());
        json.put("px", px);
        json.put("py", py);
        json.put("pz", pz);
        json.put("rx", rx);
        json.put("ry", ry);
        json.put("rz", rz);
        json.put("sx", sx);
        json.put("sy", sy);
        json.put("sz", sz);
        return json;
    }

    public static SceneObject fromJson(JSONObject json) throws JSONException {
        SceneObject out = new SceneObject(
                json.getLong("id"),
                json.getString("name"),
                Type.valueOf(json.getString("type"))
        );
        out.px = (float) json.optDouble("px", 0.0);
        out.py = (float) json.optDouble("py", 0.0);
        out.pz = (float) json.optDouble("pz", 0.0);
        out.rx = (float) json.optDouble("rx", 0.0);
        out.ry = (float) json.optDouble("ry", 0.0);
        out.rz = (float) json.optDouble("rz", 0.0);
        out.sx = (float) json.optDouble("sx", 1.0);
        out.sy = (float) json.optDouble("sy", 1.0);
        out.sz = (float) json.optDouble("sz", 1.0);
        return out;
    }
}
