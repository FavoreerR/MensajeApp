package cl.mensajeapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private LinearLayout root;
    private final int green = Color.rgb(10, 125, 99);
    @Override public void onCreate(Bundle state) { super.onCreate(state); showHome(); }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(18), dp(14), dp(18), dp(14)); return t;
    }
    private void start(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 248, 247)); setContentView(root);
        TextView header = text(title, 24, Color.WHITE, true); header.setBackgroundColor(green);
        root.addView(header);
    }
    private void showHome() {
        start("MensajeApp");
        root.addView(text("Tus conversaciones", 21, Color.DKGRAY, true));
        root.addView(text("Bienvenido a MensajeApp. Esta es una versión inicial de prueba.", 15, Color.DKGRAY, false));
        Button sample = new Button(this); sample.setText("Abrir conversación de demostración");
        root.addView(sample); sample.setOnClickListener(v -> showChat());
        root.addView(text("Próximamente: registro por teléfono, contactos y mensajes en tiempo real con Firebase.", 14, Color.GRAY, false));
    }
    private void showChat() {
        start("←  Chat de prueba");
        TextView back = text("Volver a conversaciones", 15, green, true);
        root.addView(back); back.setOnClickListener(v -> showHome());
        ScrollView scroll = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        messages.addView(text("Esta conversación solo funciona en tu dispositivo. Los mensajes no se envían a otras personas.", 14, Color.GRAY, false));
        LinearLayout composer = new LinearLayout(this); composer.setOrientation(LinearLayout.HORIZONTAL);
        EditText entry = new EditText(this); entry.setSingleLine(false); entry.setHint("Escribe un mensaje");
        composer.addView(entry, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = new Button(this); send.setText("Enviar"); composer.addView(send);
        root.addView(composer);
        send.setOnClickListener(v -> {
            String msg = entry.getText().toString().trim();
            if (!msg.isEmpty()) { TextView bubble = text(msg, 16, Color.BLACK, false);
                bubble.setGravity(Gravity.END); messages.addView(bubble); entry.setText("");
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); }
        });
    }
}
