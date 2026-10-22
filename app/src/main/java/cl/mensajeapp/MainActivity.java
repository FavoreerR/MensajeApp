package cl.mensajeapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.os.Build;
import android.view.WindowManager;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private LinearLayout root;
    private final int green = Color.rgb(10, 125, 99);
    private static final String KEY = "demo_messages";
    @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); getWindow().getDecorView().setFitsSystemWindows(false); getWindow().setStatusBarColor(green); getWindow().setNavigationBarColor(Color.rgb(246, 248, 247)); if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR); showHome(); }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(18), dp(14), dp(18), dp(14)); return t;
    }
    private void start(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 248, 247));
        setContentView(root);
        TextView header = text(title, 24, Color.WHITE, true); header.setBackgroundColor(green);
        root.addView(header);
    }
    private JSONArray load() {
        try { return new JSONArray(getPreferences(MODE_PRIVATE).getString(KEY, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }
    private void save(JSONArray messages) {
        getPreferences(MODE_PRIVATE).edit().putString(KEY, messages.toString()).apply();
    }
    private void showHome() {
        start("MensajeApp");
        root.addView(text("Conversaciones", 21, Color.DKGRAY, true));
        root.addView(text("Versión de prueba • mensajes guardados solo en este teléfono", 14, Color.GRAY, false));
        Button sample = new Button(this); sample.setText("Chat de demostración (" + load().length() + ")");
        root.addView(sample); sample.setOnClickListener(v -> showChat());
        root.addView(text("Próximamente: acceso con número, búsqueda de contactos y mensajes entre dispositivos.", 14, Color.GRAY, false));
    }
    private void showChat() {
        start("MensajeApp • Demo");
        TextView back = text("← Volver", 16, green, true);
        root.addView(back); back.setOnClickListener(v -> showHome());
        ScrollView scroll = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        messages.addView(text("Chat local de prueba: nada de lo que escribas se envía a otras personas.", 14, Color.GRAY, false));
        JSONArray history = load();
        for (int i = 0; i < history.length(); i++) addBubble(messages, history.optString(i));
        LinearLayout composer = new LinearLayout(this); composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(8), dp(12), dp(8));
        composer.setBackgroundColor(Color.WHITE);
        EditText entry = new EditText(this); entry.setSingleLine(false); entry.setMaxLines(4); entry.setHint("Escribe un mensaje");
        entry.setTextSize(16); entry.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable field = new GradientDrawable(); field.setColor(Color.rgb(246, 248, 247)); field.setCornerRadius(dp(24));
        entry.setBackground(field);
        LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(0, -2, 1);
        entryParams.setMargins(0, 0, dp(8), 0); composer.addView(entry, entryParams);
        Button send = new Button(this); send.setText("Enviar"); send.setTextColor(Color.WHITE);
        send.setAllCaps(false); send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));
        composer.addView(send, new LinearLayout.LayoutParams(dp(100), dp(52)));
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        send.setOnClickListener(v -> {
            String msg = entry.getText().toString().trim();
            if (!msg.isEmpty()) {
                JSONArray updated = load(); updated.put(msg); save(updated);
                addBubble(messages, msg); entry.setText("");
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
    private void addBubble(LinearLayout parent, String message) {
        TextView bubble = text(message, 16, Color.rgb(26, 47, 42), false);
        bubble.setGravity(Gravity.END);
        bubble.setBackgroundColor(Color.rgb(218, 246, 227));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(16), dp(4), dp(16), dp(4));
        parent.addView(bubble, params);
    }
    @Override public void onBackPressed() { showHome(); }
}
