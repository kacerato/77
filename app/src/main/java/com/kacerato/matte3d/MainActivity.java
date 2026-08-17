package com.kacerato.matte3d;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(29, 31, 33);
    private static final int PANEL = Color.rgb(43, 46, 49);
    private static final int PANEL_2 = Color.rgb(50, 53, 56);
    private static final int BUTTON = Color.rgb(65, 69, 73);
    private static final int BUTTON_SELECTED = Color.rgb(88, 93, 98);
    private static final int TEXT = Color.rgb(222, 224, 226);
    private static final int MUTED = Color.rgb(163, 167, 171);

    private SceneModel scene;
    private EditorRenderer renderer;
    private EditorGLView glView;
    private LinearLayout hierarchyList;
    private TextView inspectorTitle;
    private final SeekBar[] axisBars = new SeekBar[3];
    private final TextView[] axisValues = new TextView[3];
    private final Button[] modeButtons = new Button[3];
    private int transformMode = 0;
    private boolean syncingInspector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        scene = SceneModel.load(this);
        renderer = new EditorRenderer(scene);
        glView = new EditorGLView(this, renderer);
        setContentView(buildUi());
        enterImmersive();
        refreshHierarchy();
        refreshInspector();
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

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        toolbar.setBackgroundColor(Color.rgb(38, 40, 43));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView brand = new TextView(this);
        brand.setText("MATTE 3D");
        brand.setTextColor(TEXT);
        brand.setTextSize(15f);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(8), 0, dp(16), 0);
        toolbar.addView(brand, new LinearLayout.LayoutParams(dp(110), -1));

        addToolbarButton(toolbar, "+ Cube", v -> {
            scene.addObject(SceneObject.Type.CUBE, "Cube");
            refreshHierarchy();
            refreshInspector();
        });
        addToolbarButton(toolbar, "+ Plane", v -> {
            scene.addObject(SceneObject.Type.PLANE, "Plane");
            refreshHierarchy();
            refreshInspector();
        });
        addToolbarButton(toolbar, "Duplicate", v -> {
            if (scene.duplicateSelected() != null) {
                refreshHierarchy();
                refreshInspector();
            }
        });
        addToolbarButton(toolbar, "Delete", v -> {
            scene.deleteSelected();
            refreshHierarchy();
            refreshInspector();
        });
        addToolbarButton(toolbar, "Save", v -> {
            scene.save(this);
            Toast.makeText(this, "Scene saved", Toast.LENGTH_SHORT).show();
        });
        addToolbarButton(toolbar, "Reset View", v -> renderer.resetCamera());

        TextView hint = new TextView(this);
        hint.setText("drag: orbit   pinch: zoom");
        hint.setTextColor(MUTED);
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        toolbar.addView(hint, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        body.addView(buildHierarchyPanel(), new LinearLayout.LayoutParams(dp(190), -1));
        body.addView(glView, new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(buildInspectorPanel(), new LinearLayout.LayoutParams(dp(260), -1));
        return root;
    }

    private View buildHierarchyPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(PANEL);

        TextView title = sectionTitle("SCENE");
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        hierarchyList = new LinearLayout(this);
        hierarchyList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(hierarchyList, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView footer = new TextView(this);
        footer.setText("Objects are saved locally");
        footer.setTextColor(MUTED);
        footer.setTextSize(11f);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(footer, new LinearLayout.LayoutParams(-1, dp(30)));
        return panel;
    }

    private View buildInspectorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.setBackgroundColor(PANEL_2);

        TextView title = sectionTitle("INSPECTOR");
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(32)));

        inspectorTitle = new TextView(this);
        inspectorTitle.setTextColor(TEXT);
        inspectorTitle.setTextSize(17f);
        inspectorTitle.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(inspectorTitle, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Position", "Rotation", "Scale"};
        for (int i = 0; i < names.length; i++) {
            final int mode = i;
            modeButtons[i] = smallButton(names[i], v -> {
                transformMode = mode;
                refreshInspector();
            });
            modes.addView(modeButtons[i], new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        panel.addView(modes, new LinearLayout.LayoutParams(-1, dp(44)));

        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(6), 0, dp(2));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            TextView axisLabel = new TextView(this);
            axisLabel.setText(axes[i]);
            axisLabel.setTextColor(TEXT);
            axisLabel.setTextSize(13f);
            header.addView(axisLabel, new LinearLayout.LayoutParams(dp(28), dp(24)));

            axisValues[i] = new TextView(this);
            axisValues[i].setTextColor(MUTED);
            axisValues[i].setTextSize(12f);
            axisValues[i].setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            header.addView(axisValues[i], new LinearLayout.LayoutParams(0, dp(24), 1f));
            row.addView(header, new LinearLayout.LayoutParams(-1, dp(24)));

            SeekBar bar = new SeekBar(this);
            bar.setMax(200);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(160, 165, 170)));
            bar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(195, 199, 202)));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || syncingInspector) return;
                    applyAxisValue(axis, progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            axisBars[i] = bar;
            row.addView(bar, new LinearLayout.LayoutParams(-1, dp(36)));
            panel.addView(row, new LinearLayout.LayoutParams(-1, dp(68)));
        }

        TextView note = new TextView(this);
        note.setText("Basic native editor • OpenGL ES 3.0");
        note.setTextColor(MUTED);
        note.setTextSize(11f);
        note.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        panel.addView(note, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    private void refreshHierarchy() {
        if (hierarchyList == null) return;
        hierarchyList.removeAllViews();
        List<SceneObject> objects = scene.snapshot();
        int selected = scene.getSelectedIndex();
        for (int i = 0; i < objects.size(); i++) {
            final int index = i;
            SceneObject object = objects.get(i);
            Button row = new Button(this);
            row.setAllCaps(false);
            row.setText((object.type == SceneObject.Type.CUBE ? "Cube   " : "Plane  ") + object.name);
            row.setTextColor(TEXT);
            row.setTextSize(12f);
            row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(8), 0);
            row.setBackground(rounded(index == selected ? BUTTON_SELECTED : BUTTON, 6));
            row.setOnClickListener(v -> {
                scene.select(index);
                refreshHierarchy();
                refreshInspector();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
            lp.bottomMargin = dp(5);
            hierarchyList.addView(row, lp);
        }
    }

    private void refreshInspector() {
        if (inspectorTitle == null) return;
        SceneObject object = scene.getSelected();
        for (int i = 0; i < 3; i++) {
            modeButtons[i].setBackground(rounded(i == transformMode ? BUTTON_SELECTED : BUTTON, 6));
        }
        syncingInspector = true;
        if (object == null) {
            inspectorTitle.setText("No selection");
            for (int i = 0; i < 3; i++) {
                axisBars[i].setEnabled(false);
                axisValues[i].setText("-");
            }
        } else {
            inspectorTitle.setText(object.name);
            for (SeekBar bar : axisBars) bar.setEnabled(true);
            float[] values = getModeValues(object);
            for (int axis = 0; axis < 3; axis++) {
                axisBars[axis].setProgress(valueToProgress(values[axis]));
                axisValues[axis].setText(formatValue(values[axis]));
            }
        }
        syncingInspector = false;
    }

    private float[] getModeValues(SceneObject object) {
        if (transformMode == 0) return new float[]{object.px, object.py, object.pz};
        if (transformMode == 1) return new float[]{object.rx, object.ry, object.rz};
        return new float[]{object.sx, object.sy, object.sz};
    }

    private void applyAxisValue(int axis, int progress) {
        SceneObject object = scene.getSelected();
        if (object == null) return;
        float value = progressToValue(progress);
        if (transformMode == 0) {
            if (axis == 0) object.px = value;
            if (axis == 1) object.py = value;
            if (axis == 2) object.pz = value;
        } else if (transformMode == 1) {
            if (axis == 0) object.rx = value;
            if (axis == 1) object.ry = value;
            if (axis == 2) object.rz = value;
        } else {
            value = Math.max(0.1f, value);
            if (axis == 0) object.sx = value;
            if (axis == 1) object.sy = value;
            if (axis == 2) object.sz = value;
        }
        axisValues[axis].setText(formatValue(value));
    }

    private int valueToProgress(float value) {
        if (transformMode == 0) return clamp(Math.round((value + 10f) * 10f), 0, 200);
        if (transformMode == 1) return clamp(Math.round((value + 180f) / 1.8f), 0, 200);
        return clamp(Math.round((value - 0.1f) / 0.0145f), 0, 200);
    }

    private float progressToValue(int progress) {
        if (transformMode == 0) return progress / 10f - 10f;
        if (transformMode == 1) return progress * 1.8f - 180f;
        return 0.1f + progress * 0.0145f;
    }

    private String formatValue(float value) {
        if (transformMode == 1) return String.format(Locale.US, "%.0f°", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private void addToolbarButton(LinearLayout toolbar, String text, View.OnClickListener listener) {
        Button button = smallButton(text, listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(38));
        lp.rightMargin = dp(5);
        toolbar.addView(button, lp);
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setTextSize(11.5f);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground(rounded(BUTTON, 6));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(MUTED);
        title.setTextSize(11f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void enterImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
