package com.kacerato.matte3d;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(24, 25, 27);
    private static final int TOP = Color.rgb(31, 33, 35);
    private static final int PANEL = Color.rgb(34, 36, 39);
    private static final int PANEL_2 = Color.rgb(39, 41, 44);
    private static final int CARD = Color.rgb(45, 48, 51);
    private static final int BUTTON = Color.rgb(52, 55, 59);
    private static final int BUTTON_SELECTED = Color.rgb(79, 83, 88);
    private static final int TEXT = Color.rgb(229, 231, 233);
    private static final int MUTED = Color.rgb(157, 162, 167);
    private static final int DIM = Color.rgb(105, 110, 115);

    private SceneModel scene;
    private EditorRenderer renderer;
    private EditorGLView glView;

    private LinearLayout hierarchyList;
    private TextView sceneCount;
    private EditText nameField;
    private TextView inspectorMeta;
    private TextView statusText;
    private TextView viewBadge;
    private final EditText[][] transformFields = new EditText[3][3];
    private final Button[] toolButtons = new Button[4];
    private Button undoButton;
    private Button redoButton;
    private Button snapButton;
    private Button visibilityButton;
    private Button lockButton;
    private Button resetButton;
    private boolean syncingInspector;
    private EditorGLView.ToolMode currentTool = EditorGLView.ToolMode.NAVIGATE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        scene = SceneModel.load(this);
        renderer = new EditorRenderer(scene);
        glView = new EditorGLView(this, renderer);
        glView.setListener(new EditorGLView.Listener() {
            @Override
            public void onSelectionChanged() {
                refreshHierarchy();
                refreshInspector();
                refreshStatus();
            }

            @Override
            public void onTransformChanged() {
                refreshInspector();
                refreshStatus();
            }
        });

        setContentView(buildUi());
        enterImmersive();
        setTool(EditorGLView.ToolMode.NAVIGATE);
        refreshAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scene.save(this);
        glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
        enterImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        body.addView(buildHierarchyPanel(), new LinearLayout.LayoutParams(dp(190), -1));
        body.addView(buildViewport(), new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(buildInspectorPanel(), new LinearLayout.LayoutParams(dp(292), -1));

        return root;
    }

    private View buildTopBar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(5), dp(8), dp(5));
        toolbar.setBackgroundColor(TOP);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setGravity(Gravity.CENTER_VERTICAL);
        brandBox.setPadding(dp(8), 0, dp(8), 0);

        TextView brand = new TextView(this);
        brand.setText("MATTE 3D");
        brand.setTextColor(TEXT);
        brand.setTextSize(14f);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(brand, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView version = new TextView(this);
        version.setText("EDITOR 0.2");
        version.setTextColor(DIM);
        version.setTextSize(9f);
        brandBox.addView(version, new LinearLayout.LayoutParams(-1, dp(14)));

        toolbar.addView(brandBox, new LinearLayout.LayoutParams(dp(116), -1));

        Button add = topButton("ADD", v -> showAddMenu(v));
        toolbar.addView(add, topButtonParams(62));

        undoButton = topButton("UNDO", v -> {
            if (scene.undo()) refreshAll();
        });
        toolbar.addView(undoButton, topButtonParams(64));

        redoButton = topButton("REDO", v -> {
            if (scene.redo()) refreshAll();
        });
        toolbar.addView(redoButton, topButtonParams(64));

        Button duplicate = topButton("DUP", v -> {
            if (scene.duplicateSelected() != null) refreshAll();
        });
        toolbar.addView(duplicate, topButtonParams(58));

        Button delete = topButton("DEL", v -> {
            if (scene.deleteSelected()) refreshAll();
        });
        toolbar.addView(delete, topButtonParams(58));

        Button view = topButton("VIEW", this::showViewMenu);
        toolbar.addView(view, topButtonParams(62));

        snapButton = topButton("SNAP", v -> {
            renderer.setSnapEnabled(!renderer.isSnapEnabled());
            updateSnapButton();
            refreshStatus();
        });
        toolbar.addView(snapButton, topButtonParams(64));

        Button save = topButton("SAVE", v -> {
            scene.save(this);
            Toast.makeText(this, "Scene saved", Toast.LENGTH_SHORT).show();
        });
        toolbar.addView(save, topButtonParams(62));

        TextView gestures = new TextView(this);
        gestures.setText("tap select  •  double tap focus  •  pinch zoom");
        gestures.setTextColor(DIM);
        gestures.setTextSize(10f);
        gestures.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        toolbar.addView(gestures, new LinearLayout.LayoutParams(0, -1, 1f));

        return toolbar;
    }

    private View buildViewport() {
        FrameLayout viewport = new FrameLayout(this);
        viewport.setBackgroundColor(BG);
        viewport.addView(glView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolRail = new LinearLayout(this);
        toolRail.setOrientation(LinearLayout.VERTICAL);
        toolRail.setPadding(dp(4), dp(4), dp(4), dp(4));
        toolRail.setBackground(rounded(Color.argb(235, 39, 41, 44), 8));

        String[] labels = {"NAV", "MOVE", "ROT", "SCALE"};
        EditorGLView.ToolMode[] modes = {
                EditorGLView.ToolMode.NAVIGATE,
                EditorGLView.ToolMode.MOVE,
                EditorGLView.ToolMode.ROTATE,
                EditorGLView.ToolMode.SCALE
        };

        for (int i = 0; i < labels.length; i++) {
            final EditorGLView.ToolMode mode = modes[i];
            Button b = compactButton(labels[i], v -> setTool(mode));
            b.setTextSize(9.5f);
            toolButtons[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(38));
            if (i > 0) lp.topMargin = dp(4);
            toolRail.addView(b, lp);
        }

        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(dp(62), dp(176));
        railLp.gravity = Gravity.LEFT | Gravity.TOP;
        railLp.leftMargin = dp(10);
        railLp.topMargin = dp(10);
        viewport.addView(toolRail, railLp);

        viewBadge = new TextView(this);
        viewBadge.setText("PERSPECTIVE");
        viewBadge.setTextColor(MUTED);
        viewBadge.setTextSize(9.5f);
        viewBadge.setGravity(Gravity.CENTER);
        viewBadge.setBackground(rounded(Color.argb(225, 38, 40, 43), 6));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(dp(112), dp(30));
        badgeLp.gravity = Gravity.RIGHT | Gravity.TOP;
        badgeLp.rightMargin = dp(10);
        badgeLp.topMargin = dp(10);
        viewport.addView(viewBadge, badgeLp);

        statusText = new TextView(this);
        statusText.setTextColor(MUTED);
        statusText.setTextSize(10f);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setPadding(dp(10), 0, dp(10), 0);
        statusText.setBackground(rounded(Color.argb(225, 38, 40, 43), 6));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-2, dp(30));
        statusLp.gravity = Gravity.LEFT | Gravity.BOTTOM;
        statusLp.leftMargin = dp(10);
        statusLp.bottomMargin = dp(10);
        viewport.addView(statusText, statusLp);

        return viewport;
    }

    private View buildHierarchyPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(PANEL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(sectionTitle("SCENE"), new LinearLayout.LayoutParams(0, dp(32), 1f));

        sceneCount = new TextView(this);
        sceneCount.setTextColor(DIM);
        sceneCount.setTextSize(10f);
        sceneCount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(sceneCount, new LinearLayout.LayoutParams(dp(52), dp(32)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(32)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        hierarchyList = new LinearLayout(this);
        hierarchyList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(hierarchyList, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView footer = new TextView(this);
        footer.setText("V visibility   L lock");
        footer.setTextColor(DIM);
        footer.setTextSize(9.5f);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(footer, new LinearLayout.LayoutParams(-1, dp(28)));
        return panel;
    }

    private View buildInspectorPanel() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(PANEL_2);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        panel.addView(sectionTitle("INSPECTOR"), new LinearLayout.LayoutParams(-1, dp(30)));

        nameField = new EditText(this);
        nameField.setSingleLine(true);
        nameField.setTextColor(TEXT);
        nameField.setHintTextColor(DIM);
        nameField.setTextSize(15f);
        nameField.setSelectAllOnFocus(false);
        nameField.setPadding(dp(10), 0, dp(10), 0);
        nameField.setBackground(rounded(CARD, 6));
        nameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitName();
                v.clearFocus();
                return true;
            }
            return false;
        });
        nameField.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !syncingInspector) commitName();
        });
        panel.addView(nameField, new LinearLayout.LayoutParams(-1, dp(42)));

        inspectorMeta = new TextView(this);
        inspectorMeta.setTextColor(DIM);
        inspectorMeta.setTextSize(9.5f);
        inspectorMeta.setGravity(Gravity.CENTER_VERTICAL);
        inspectorMeta.setPadding(dp(2), 0, 0, 0);
        panel.addView(inspectorMeta, new LinearLayout.LayoutParams(-1, dp(28)));

        panel.addView(sectionTitle("TRANSFORM"), new LinearLayout.LayoutParams(-1, dp(30)));

        addTransformGroup(panel, 0, "POSITION");
        addTransformGroup(panel, 1, "ROTATION");
        addTransformGroup(panel, 2, "SCALE");

        resetButton = compactButton("RESET TRANSFORM", v -> {
            if (scene.resetSelectedTransform()) refreshAll();
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, dp(38));
        resetLp.topMargin = dp(6);
        panel.addView(resetButton, resetLp);

        panel.addView(sectionTitle("OBJECT"), sectionParams());

        LinearLayout objectActions = new LinearLayout(this);
        objectActions.setOrientation(LinearLayout.HORIZONTAL);

        visibilityButton = compactButton("VISIBLE", v -> {
            int index = scene.getSelectedIndex();
            if (scene.toggleVisible(index)) refreshAll();
        });
        objectActions.addView(visibilityButton, actionCellParams());

        lockButton = compactButton("UNLOCKED", v -> {
            int index = scene.getSelectedIndex();
            if (scene.toggleLocked(index)) refreshAll();
        });
        objectActions.addView(lockButton, actionCellParams());

        Button focus = compactButton("FOCUS", v -> renderer.focusSelected());
        objectActions.addView(focus, actionCellParams());

        panel.addView(objectActions, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView note = new TextView(this);
        note.setText("Move drag = camera-ground X/Z\nRotate drag = X/Y   •   Scale drag = uniform");
        note.setTextColor(DIM);
        note.setTextSize(9.5f);
        note.setPadding(dp(2), dp(12), dp(2), dp(4));
        panel.addView(note, new LinearLayout.LayoutParams(-1, dp(54)));

        return outer;
    }

    private void addTransformGroup(LinearLayout panel, int mode, String titleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(MUTED);
        title.setTextSize(9.5f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            final int m = mode;
            final int a = axis;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            if (axis > 0) cell.setPadding(dp(4), 0, 0, 0);

            TextView axisLabel = new TextView(this);
            axisLabel.setText(axes[axis]);
            axisLabel.setTextColor(DIM);
            axisLabel.setTextSize(9f);
            axisLabel.setGravity(Gravity.CENTER_VERTICAL);
            cell.addView(axisLabel, new LinearLayout.LayoutParams(-1, dp(18)));

            EditText field = new EditText(this);
            field.setSingleLine(true);
            field.setTextColor(TEXT);
            field.setTextSize(11f);
            field.setGravity(Gravity.CENTER);
            field.setSelectAllOnFocus(true);
            field.setPadding(dp(4), 0, dp(4), 0);
            field.setInputType(InputType.TYPE_CLASS_NUMBER |
                    InputType.TYPE_NUMBER_FLAG_DECIMAL |
                    InputType.TYPE_NUMBER_FLAG_SIGNED);
            field.setImeOptions(axis == 2 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
            field.setBackground(rounded(CARD, 5));
            field.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    commitTransformField(m, a);
                    return false;
                }
                return false;
            });
            field.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !syncingInspector) commitTransformField(m, a);
            });
            transformFields[mode][axis] = field;
            cell.addView(field, new LinearLayout.LayoutParams(-1, dp(34)));

            row.addView(cell, new LinearLayout.LayoutParams(0, dp(54), 1f));
        }

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(56));
        rowLp.bottomMargin = dp(4);
        panel.addView(row, rowLp);
    }

    private void showAddMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Cube");
        menu.getMenu().add("Plane");
        menu.getMenu().add("Sphere");
        menu.getMenu().add("Cylinder");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Plane".equals(title)) scene.addObject(SceneObject.Type.PLANE, "Plane");
            else if ("Sphere".equals(title)) scene.addObject(SceneObject.Type.SPHERE, "Sphere");
            else if ("Cylinder".equals(title)) scene.addObject(SceneObject.Type.CYLINDER, "Cylinder");
            else scene.addObject(SceneObject.Type.CUBE, "Cube");
            refreshAll();
            return true;
        });
        menu.show();
    }

    private void showViewMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Perspective");
        menu.getMenu().add("Front");
        menu.getMenu().add("Right");
        menu.getMenu().add("Top");
        menu.getMenu().add("Focus Selection");
        menu.getMenu().add("Reset View");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Front".equals(title)) {
                renderer.setViewPreset(EditorRenderer.ViewPreset.FRONT);
                viewBadge.setText("FRONT");
            } else if ("Right".equals(title)) {
                renderer.setViewPreset(EditorRenderer.ViewPreset.RIGHT);
                viewBadge.setText("RIGHT");
            } else if ("Top".equals(title)) {
                renderer.setViewPreset(EditorRenderer.ViewPreset.TOP);
                viewBadge.setText("TOP");
            } else if ("Focus Selection".equals(title)) {
                renderer.focusSelected();
            } else if ("Reset View".equals(title)) {
                renderer.resetCamera();
                viewBadge.setText("PERSPECTIVE");
            } else {
                renderer.setViewPreset(EditorRenderer.ViewPreset.PERSPECTIVE);
                viewBadge.setText("PERSPECTIVE");
            }
            return true;
        });
        menu.show();
    }

    private void setTool(EditorGLView.ToolMode mode) {
        currentTool = mode;
        glView.setToolMode(mode);
        EditorGLView.ToolMode[] modes = EditorGLView.ToolMode.values();
        for (int i = 0; i < toolButtons.length; i++) {
            if (toolButtons[i] == null) continue;
            boolean selected = modes[i] == mode;
            toolButtons[i].setBackground(rounded(selected ? BUTTON_SELECTED : BUTTON, 5));
            toolButtons[i].setTextColor(selected ? TEXT : MUTED);
        }
        refreshStatus();
    }

    private void refreshAll() {
        refreshHierarchy();
        refreshInspector();
        refreshStatus();
        updateHistoryButtons();
        updateSnapButton();
    }

    private void refreshHierarchy() {
        if (hierarchyList == null) return;
        hierarchyList.removeAllViews();
        List<SceneObject> objects = scene.snapshot();
        int selected = scene.getSelectedIndex();
        sceneCount.setText(objects.size() + " OBJ");

        for (int i = 0; i < objects.size(); i++) {
            final int index = i;
            SceneObject object = objects.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(3), dp(4), dp(3));
            row.setBackground(rounded(index == selected ? BUTTON_SELECTED : CARD, 6));

            TextView type = new TextView(this);
            type.setText(typeShort(object.type));
            type.setTextColor(index == selected ? TEXT : MUTED);
            type.setTextSize(9f);
            type.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            type.setGravity(Gravity.CENTER);
            row.addView(type, new LinearLayout.LayoutParams(dp(28), -1));

            Button name = compactButton(object.name, v -> {
                scene.select(index);
                refreshHierarchy();
                refreshInspector();
                refreshStatus();
            });
            name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            name.setTextColor(object.locked ? DIM : TEXT);
            name.setBackgroundColor(Color.TRANSPARENT);
            name.setPadding(dp(4), 0, dp(2), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -1, 1f));

            Button visible = compactButton(object.visible ? "V" : "-", v -> {
                scene.toggleVisible(index);
                refreshAll();
            });
            visible.setTextColor(object.visible ? TEXT : DIM);
            visible.setBackgroundColor(Color.TRANSPARENT);
            row.addView(visible, new LinearLayout.LayoutParams(dp(30), -1));

            Button locked = compactButton(object.locked ? "L" : "-", v -> {
                scene.toggleLocked(index);
                refreshAll();
            });
            locked.setTextColor(object.locked ? TEXT : DIM);
            locked.setBackgroundColor(Color.TRANSPARENT);
            row.addView(locked, new LinearLayout.LayoutParams(dp(30), -1));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
            lp.bottomMargin = dp(5);
            hierarchyList.addView(row, lp);
        }
    }

    private void refreshInspector() {
        if (nameField == null) return;
        SceneObject object = scene.getSelected();
        syncingInspector = true;

        if (object == null) {
            nameField.setText("");
            nameField.setHint("No selection");
            nameField.setEnabled(false);
            inspectorMeta.setText("SELECT AN OBJECT IN VIEWPORT OR SCENE");
            for (EditText[] group : transformFields) {
                for (EditText field : group) {
                    if (field != null) {
                        field.setText("-");
                        field.setEnabled(false);
                    }
                }
            }
            visibilityButton.setEnabled(false);
            lockButton.setEnabled(false);
            resetButton.setEnabled(false);
        } else {
            nameField.setEnabled(true);
            nameField.setText(object.name);
            nameField.setHint("");
            inspectorMeta.setText(typeLong(object.type) + "  •  ID " + object.id + "  •  " +
                    (object.visible ? "VISIBLE" : "HIDDEN") + "  •  " +
                    (object.locked ? "LOCKED" : "EDITABLE"));

            float[][] values = {
                    {object.px, object.py, object.pz},
                    {object.rx, object.ry, object.rz},
                    {object.sx, object.sy, object.sz}
            };
            for (int mode = 0; mode < 3; mode++) {
                for (int axis = 0; axis < 3; axis++) {
                    EditText field = transformFields[mode][axis];
                    field.setEnabled(!object.locked);
                    field.setText(formatNumber(values[mode][axis]));
                }
            }
            visibilityButton.setEnabled(true);
            lockButton.setEnabled(true);
            resetButton.setEnabled(!object.locked);
            visibilityButton.setText(object.visible ? "VISIBLE" : "HIDDEN");
            lockButton.setText(object.locked ? "LOCKED" : "UNLOCKED");
        }

        syncingInspector = false;
        updateHistoryButtons();
    }

    private void refreshStatus() {
        if (statusText == null) return;
        SceneObject selected = scene.getSelected();
        String name = selected == null ? "NO SELECTION" : selected.name.toUpperCase(Locale.US);
        String snap = renderer.isSnapEnabled() ? "  •  SNAP 0.25 / 15°" : "";
        statusText.setText(toolLabel(currentTool) + "  •  " + name + "  •  " +
                scene.objectCount() + " OBJECTS" + snap);
    }

    private void commitName() {
        if (syncingInspector) return;
        if (scene.renameSelected(nameField.getText().toString())) {
            refreshHierarchy();
            refreshInspector();
            refreshStatus();
        }
    }

    private void commitTransformField(int mode, int axis) {
        if (syncingInspector) return;
        EditText field = transformFields[mode][axis];
        if (field == null) return;
        try {
            float value = Float.parseFloat(field.getText().toString().replace(',', '.'));
            if (scene.setSelectedTransform(mode, axis, value)) {
                refreshInspector();
                refreshStatus();
            }
        } catch (NumberFormatException ignored) {
            refreshInspector();
        }
    }

    private void updateHistoryButtons() {
        if (undoButton == null || redoButton == null) return;
        undoButton.setEnabled(scene.canUndo());
        redoButton.setEnabled(scene.canRedo());
        undoButton.setTextColor(scene.canUndo() ? TEXT : DIM);
        redoButton.setTextColor(scene.canRedo() ? TEXT : DIM);
    }

    private void updateSnapButton() {
        if (snapButton == null) return;
        boolean enabled = renderer.isSnapEnabled();
        snapButton.setBackground(rounded(enabled ? BUTTON_SELECTED : BUTTON, 5));
        snapButton.setText(enabled ? "SNAP ON" : "SNAP");
    }

    private Button topButton(String text, View.OnClickListener listener) {
        Button b = compactButton(text, listener);
        b.setTextSize(9.5f);
        return b;
    }

    private Button compactButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setTextSize(10.5f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(BUTTON, 5));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams topButtonParams(int widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), dp(36));
        lp.rightMargin = dp(4);
        return lp;
    }

    private LinearLayout.LayoutParams actionCellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.rightMargin = dp(4);
        return lp;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(30));
        lp.topMargin = dp(8);
        return lp;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(MUTED);
        title.setTextSize(10f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private static String typeShort(SceneObject.Type type) {
        switch (type) {
            case PLANE: return "PL";
            case SPHERE: return "SP";
            case CYLINDER: return "CY";
            default: return "CU";
        }
    }

    private static String typeLong(SceneObject.Type type) {
        switch (type) {
            case PLANE: return "PLANE";
            case SPHERE: return "SPHERE";
            case CYLINDER: return "CYLINDER";
            default: return "CUBE";
        }
    }

    private static String toolLabel(EditorGLView.ToolMode mode) {
        switch (mode) {
            case MOVE: return "MOVE";
            case ROTATE: return "ROTATE";
            case SCALE: return "SCALE";
            default: return "NAVIGATE";
        }
    }

    private static String formatNumber(float value) {
        if (Math.abs(value) < 0.00005f) value = 0f;
        return String.format(Locale.US, "%.2f", value);
    }

    private void enterImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
