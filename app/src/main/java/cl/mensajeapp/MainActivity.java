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
import android.widget.Toast;
import android.text.InputType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.FirebaseException;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.DateFormat;
import java.util.Date;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private LinearLayout root;
    private final int green = Color.rgb(10, 125, 99);
    private static final String KEY = "demo_messages";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration liveMessages;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); getWindow().getDecorView().setFitsSystemWindows(false); getWindow().setStatusBarColor(green); getWindow().setNavigationBarColor(Color.rgb(246, 248, 247)); if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR); auth = FirebaseAuth.getInstance(); db = FirebaseFirestore.getInstance(); showHome(); }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(18), dp(14), dp(18), dp(14)); return t;
    }
    private void start(String title) {
        if (liveMessages != null) { liveMessages.remove(); liveMessages = null; }
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
        FirebaseUser signedUser = auth.getCurrentUser();
        if (signedUser == null) {
            Button login = new Button(this); login.setText("Acceder gratis con correo electrónico");
            root.addView(login); login.setOnClickListener(v -> showEmailLogin());
        } else {
            root.addView(text("Sesión: " + (signedUser.getEmail() != null ? signedUser.getEmail() : "Usuario registrado"), 14, green, true));
            root.addView(text("Tu código de usuario: " + signedUser.getUid(), 12, Color.DKGRAY, false));
            Button newChat = new Button(this); newChat.setText("Nuevo chat entre teléfonos"); root.addView(newChat);
            newChat.setOnClickListener(v -> showNewChat());
            Button myChats = new Button(this); myChats.setText("Mis conversaciones"); root.addView(myChats);
            myChats.setOnClickListener(v -> showSavedChats());
            Button logout = new Button(this); logout.setText("Cerrar sesión"); root.addView(logout);
            logout.setOnClickListener(v -> { auth.signOut(); showHome(); });
        }
        root.addView(text("Los chats de demostración siguen siendo locales. Los chats nuevos usan Firestore.", 14, Color.GRAY, false));
        Button sample = new Button(this); sample.setText("Chat de demostración (" + load().length() + ")");
        root.addView(sample); sample.setOnClickListener(v -> showChat());
        root.addView(text("Para conversar, comparte tu código de usuario con alguien de confianza que también tenga MensajeApp.", 14, Color.GRAY, false));
    }

    private void showEmailLogin() {
        start("Acceso gratis");
        root.addView(text("Regístrate o inicia sesión con tu correo y contraseña. No se envían SMS.", 16, Color.DKGRAY, false));
        EditText email = new EditText(this); email.setHint("Correo electrónico");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setSingleLine(true); root.addView(email);
        EditText password = new EditText(this); password.setHint("Contraseña (mínimo 6 caracteres)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true); root.addView(password);
        TextView status = text("", 14, Color.GRAY, false); root.addView(status);
        Button signIn = new Button(this); signIn.setText("Iniciar sesión"); root.addView(signIn);
        Button register = new Button(this); register.setText("Crear cuenta gratis"); root.addView(register);
        View.OnClickListener action = v -> {
            String address = email.getText().toString().trim();
            String secret = password.getText().toString();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.setError("Correo no válido"); return; }
            if (secret.length() < 6) { password.setError("Mínimo 6 caracteres"); return; }
            signIn.setEnabled(false); register.setEnabled(false); status.setText("Conectando...");
            com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> task =
                v == register ? auth.createUserWithEmailAndPassword(address, secret)
                              : auth.signInWithEmailAndPassword(address, secret);
            task.addOnCompleteListener(this, result -> {
                if (result.isSuccessful()) showHome();
                else { signIn.setEnabled(true); register.setEnabled(true);
                    status.setText("No se pudo acceder: " + (result.getException() == null ? "Error desconocido" : result.getException().getLocalizedMessage())); }
            });
        };
        signIn.setOnClickListener(action); register.setOnClickListener(action);
        Button back = new Button(this); back.setText("Volver"); root.addView(back); back.setOnClickListener(v -> showHome());
    }
    private void showPhoneLogin() {
        start("Acceso con teléfono");
        root.addView(text("Ingresa tu número con código de país. Ejemplo: +56912345678", 16, Color.DKGRAY, false));
        EditText phone = new EditText(this); phone.setHint("+56912345678");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setSingleLine(true); phone.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.addView(phone);
        Button request = new Button(this); request.setText("Enviar código SMS"); root.addView(request);
        TextView status = text("Firebase puede requerir verificación de la aplicación y habilitación de SMS.", 13, Color.GRAY, false);
        root.addView(status);
        Button back = new Button(this); back.setText("Volver"); root.addView(back);
        back.setOnClickListener(v -> showHome());
        request.setOnClickListener(v -> {
            String number = phone.getText().toString().replace(" ", "").trim();
            if (!number.matches("^\\+[1-9][0-9]{7,14}$")) {
                phone.setError("Escribe el número internacional, por ejemplo +56912345678"); return;
            }
            request.setEnabled(false);
            status.setText("Solicitando código de verificación...");
            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(number)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override public void onVerificationCompleted(PhoneAuthCredential credential) {
                        signInWithCredential(credential);
                    }
                    @Override public void onVerificationFailed(FirebaseException error) {
                        request.setEnabled(true);
                        status.setText("No se pudo verificar: " + error.getLocalizedMessage());
                    }
                    @Override public void onCodeSent(String id, PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id; resendToken = token; showCodeEntry(number);
                    }
                }).build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });
    }
    private void showCodeEntry(String number) {
        start("Verificar teléfono");
        root.addView(text("Introduce el código recibido por SMS en " + number, 16, Color.DKGRAY, false));
        EditText code = new EditText(this); code.setHint("Código de 6 dígitos");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setSingleLine(true); code.setPadding(dp(18), dp(12), dp(18), dp(12)); root.addView(code);
        TextView status = text("", 14, Color.GRAY, false); root.addView(status);
        Button confirm = new Button(this); confirm.setText("Verificar código"); root.addView(confirm);
        confirm.setOnClickListener(v -> {
            String entered = code.getText().toString().trim();
            if (!entered.matches("[0-9]{6}") || verificationId == null) {
                code.setError("Introduce los seis dígitos"); return;
            }
            confirm.setEnabled(false);
            status.setText("Verificando...");
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, entered);
            auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) showHome();
                else { confirm.setEnabled(true); status.setText("Código no válido o vencido: " +
                    (task.getException() == null ? "inténtalo otra vez" : task.getException().getLocalizedMessage())); }
            });
        });
        Button back = new Button(this); back.setText("Cambiar número"); root.addView(back);
        back.setOnClickListener(v -> showPhoneLogin());
    }
    private void signInWithCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) showHome();
            else Toast.makeText(this, "No se pudo iniciar sesión: " +
                (task.getException() == null ? "error desconocido" : task.getException().getLocalizedMessage()), Toast.LENGTH_LONG).show();
        });
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

    private String chatId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
    private void showNewChat() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { showEmailLogin(); return; }
        start("Nuevo chat");
        root.addView(text("Pide a la otra persona su código de usuario de MensajeApp. No necesitas compartir tu teléfono.", 15, Color.DKGRAY, false));
        EditText peer = new EditText(this); peer.setHint("Código de usuario del contacto");
        peer.setSingleLine(true); root.addView(peer);
        TextView status = text("", 13, Color.GRAY, false); root.addView(status);
        Button go = new Button(this); go.setText("Iniciar conversación"); root.addView(go);
        go.setOnClickListener(v -> {
            String other = peer.getText().toString().trim();
            if (!other.matches("[A-Za-z0-9]{10,128}") || other.equals(me.getUid())) {
                peer.setError("Ingresa un código válido de otra persona"); return;
            }
            String id = chatId(me.getUid(), other);
            ArrayList<String> ids = new ArrayList<>(Arrays.asList(me.getUid(), other));
            Collections.sort(ids);
            go.setEnabled(false);
            db.collection("chats").document(id).get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) { go.setEnabled(true); status.setText("No se pudo consultar: " + task.getException().getLocalizedMessage()); return; }
                if (task.getResult().exists()) { rememberChat(id, other); showRemoteChat(id, other); return; }
                java.util.Map<String,Object> data = new java.util.HashMap<>();
                data.put("memberIds", ids); data.put("createdAt", FieldValue.serverTimestamp());
                db.collection("chats").document(id).set(data).addOnSuccessListener(v2 -> {
                    rememberChat(id, other); showRemoteChat(id, other);
                }).addOnFailureListener(e -> { go.setEnabled(true); status.setText("No se pudo crear el chat: " + e.getLocalizedMessage()); });
            });
        });
        Button back = new Button(this); back.setText("Volver"); root.addView(back); back.setOnClickListener(v -> showHome());
    }
    private void rememberChat(String id, String other) {
        getPreferences(MODE_PRIVATE).edit().putString("peer_" + id, other).apply();
        java.util.Set<String> saved = new java.util.HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("known_chats", Collections.emptySet()));
        saved.add(id);
        getPreferences(MODE_PRIVATE).edit().putStringSet("known_chats", saved).apply();
    }
    private void showSavedChats() {
        start("Mis conversaciones");
        root.addView(text("Chats abiertos desde este teléfono", 16, Color.DKGRAY, true));
        for (String id : getPreferences(MODE_PRIVATE).getStringSet("known_chats", Collections.emptySet())) {
            String peer = getPreferences(MODE_PRIVATE).getString("peer_" + id, "");
            Button chat = new Button(this); chat.setText("Chat: " + peer);
            root.addView(chat); chat.setOnClickListener(v -> showRemoteChat(id, peer));
        }
        Button back = new Button(this); back.setText("Volver"); root.addView(back); back.setOnClickListener(v -> showHome());
    }
    private void showRemoteChat(String id, String other) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { showHome(); return; }
        start("Chat en tiempo real");
        root.addView(text("Contacto: " + other, 12, Color.GRAY, false));
        ScrollView scroll = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout composer = new LinearLayout(this); composer.setPadding(dp(8), dp(8), dp(8), dp(8));
        EditText entry = new EditText(this); entry.setHint("Mensaje"); entry.setMaxLines(4);
        composer.addView(entry, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = new Button(this); send.setText("Enviar"); composer.addView(send);
        root.addView(composer);
        send.setOnClickListener(v -> {
            String msg = entry.getText().toString().trim();
            if (msg.isEmpty()) return;
            if (msg.length() > 4000) { entry.setError("Máximo 4000 caracteres"); return; }
            send.setEnabled(false);
            java.util.Map<String,Object> data = new java.util.HashMap<>();
            data.put("senderId", me.getUid()); data.put("text", msg);
            data.put("createdAt", FieldValue.serverTimestamp());
            db.collection("chats").document(id).collection("messages").add(data)
                .addOnSuccessListener(ref -> { entry.setText(""); send.setEnabled(true); })
                .addOnFailureListener(e -> { send.setEnabled(true); Toast.makeText(this, "Error al enviar: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show(); });
        });
        liveMessages = db.collection("chats").document(id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) { Toast.makeText(this, "No se pudo cargar el chat: " + error.getLocalizedMessage(), Toast.LENGTH_LONG).show(); return; }
                if (snapshot == null) return;
                messages.removeAllViews();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    String msg = doc.getString("text");
                    boolean mine = me.getUid().equals(doc.getString("senderId"));
                    if (msg == null) continue;
                    TextView bubble = text(msg, 16, Color.DKGRAY, false);
                    bubble.setBackgroundColor(mine ? Color.rgb(218,246,227) : Color.WHITE);
                    bubble.setGravity(mine ? Gravity.END : Gravity.START);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
                    lp.setMargins(dp(12),dp(4),dp(12),dp(4));
                    messages.addView(bubble,lp);
                }
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            });
        Button back = new Button(this); back.setText("Volver"); root.addView(back); back.setOnClickListener(v -> showHome());
    }

    @Override public void onBackPressed() { showHome(); }
}
