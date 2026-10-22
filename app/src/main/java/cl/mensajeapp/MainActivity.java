package cl.mensajeapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
    private final int green = Color.rgb(8, 128, 117);
    private static final String KEY = "demo_messages";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration liveMessages;
    private ListenerRegistration liveChats;
    private String verificationId;
    private static final int PICK_AVATAR = 81;
    private ImageView profilePreview;
    private String pendingAvatar = "";
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
        if (liveChats != null) { liveChats.remove(); liveChats = null; }
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(230, 249, 249), Color.rgb(247, 250, 255), Color.rgb(224, 236, 251)}));
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) {
                if (child instanceof Button) styleButton((Button) child);
                if (child instanceof EditText) styleField((EditText) child);
            }
            @Override public void onChildViewRemoved(View parent, View child) { }
        });
        setContentView(root);
        TextView header = text("✦  " + title, 24, Color.WHITE, true);
        GradientDrawable headerBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(10, 35, 65), Color.rgb(5, 111, 115)});
        headerBg.setCornerRadius(dp(20));
        header.setBackground(headerBg);
        header.setElevation(dp(5));
        root.addView(header);
    }
    private void styleButton(Button button) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(8, 122, 133), Color.rgb(8, 154, 128)});
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setElevation(dp(3));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(dp(10), dp(6), dp(10), dp(6));
        button.setLayoutParams(params);
    }
    private void styleField(EditText field) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(197, 218, 227));
        field.setBackground(bg);
        field.setTextColor(Color.rgb(21, 43, 57));
        field.setHintTextColor(Color.rgb(105, 125, 137));
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
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
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            root.addView(text("Tus conversaciones, en un solo lugar", 21, Color.DKGRAY, true));
            root.addView(text("Inicia sesión para comunicarte gratis por Internet.", 15, Color.GRAY, false));
            Button login = new Button(this); login.setText("Entrar o crear cuenta"); root.addView(login);
            login.setOnClickListener(v -> showEmailLogin());
            return;
        }
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Chats", 28, Color.rgb(16, 44, 51), true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button profile = new Button(this); profile.setText("Mi perfil");
        top.addView(profile, new LinearLayout.LayoutParams(dp(110), dp(50)));
        root.addView(top);
        profile.setOnClickListener(v -> showProfile());

        Button newChat = new Button(this); newChat.setText("＋  Nuevo mensaje"); root.addView(newChat);
        newChat.setOnClickListener(v -> showNewChat());

        TextView subtitle = text("Conversaciones recientes", 17, Color.DKGRAY, true);
        root.addView(subtitle);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        list.addView(text("Buscando tus conversaciones...", 14, Color.GRAY, false));
        liveChats = db.collection("chats")
            .whereArrayContains("memberIds", me.getUid())
            .addSnapshotListener((snapshot, error) -> {
                if (root == null || list.getParent() != scroll) return;
                list.removeAllViews();
                if (error != null) {
                    list.addView(text("No se pudieron cargar los chats: " + error.getLocalizedMessage(), 14, Color.GRAY, false));
                    return;
                }
                if (snapshot == null || snapshot.isEmpty()) {
                    list.addView(text("Todavía no tienes conversaciones. Pulsa Nuevo mensaje para empezar.", 15, Color.GRAY, false));
                    return;
                }
                for (DocumentSnapshot chatDoc : snapshot.getDocuments()) {
                    java.util.List<String> members = (java.util.List<String>) chatDoc.get("memberIds");
                    if (members == null || members.size() != 2) continue;
                    String other = members.get(0).equals(me.getUid()) ? members.get(1) : members.get(0);
                    String chatId = chatDoc.getId();
                    rememberChat(chatId, other);
                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(LinearLayout.HORIZONTAL); item.setGravity(Gravity.CENTER_VERTICAL);
                    item.setPadding(dp(12), dp(12), dp(12), dp(12));
                    GradientDrawable background = new GradientDrawable();
                    background.setColor(Color.WHITE); background.setCornerRadius(dp(16));
                    item.setBackground(background);
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, dp(82));
                    itemParams.setMargins(dp(6), dp(4), dp(6), dp(4));
                    list.addView(item, itemParams);
                    TextView avatar = text("●", 26, green, true);
                    item.addView(avatar);
                    LinearLayout detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
                    item.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView name = text("Contacto", 17, Color.DKGRAY, true); detail.addView(name);
                    TextView preview = text("Toca para abrir la conversación", 12, Color.GRAY, false); detail.addView(preview);
                    db.collection("users").document(other).get().addOnSuccessListener(userDoc -> {
                        String displayName = userDoc.getString("displayName");
                        String username = userDoc.getString("username");
                        if (displayName != null && !displayName.isEmpty()) name.setText(displayName);
                        else if (username != null) name.setText("@" + username);
                    });
                    db.collection("chats").document(chatId).collection("messages")
                        .orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get()
                        .addOnSuccessListener(recent -> {
                            if (!recent.isEmpty()) {
                                String last = recent.getDocuments().get(0).getString("text");
                                if (last != null) preview.setText(last.length() > 48 ? last.substring(0, 48) + "…" : last);
                            }
                        });
                    item.setOnClickListener(v -> showRemoteChat(chatId, other));
                }
            });
        Button logout = new Button(this); logout.setText("Cerrar sesión"); root.addView(logout);
        logout.setOnClickListener(v -> { auth.signOut(); showHome(); });
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
        start("Buscar contacto");
        root.addView(text("Busca por número de teléfono con código de país (+56...) o por @usuario. Solo aparecerán teléfonos que sus dueños hayan decidido compartir.", 15, Color.DKGRAY, false));
        EditText peer = new EditText(this); peer.setHint("+56912345678 o @usuario"); peer.setSingleLine(true); root.addView(peer);
        TextView status = text("", 13, Color.GRAY, false); root.addView(status);
        Button go = new Button(this); go.setText("Buscar e iniciar chat"); root.addView(go);
        go.setOnClickListener(v -> {
            String query = peer.getText().toString().trim().replace(" ", "").replace("-", "");
            boolean byPhone = query.startsWith("+");
            String handle = query.toLowerCase(java.util.Locale.ROOT).replaceFirst("^@", "");
            if (byPhone ? !query.matches("^\\+[1-9][0-9]{7,14}$") : !handle.matches("[a-z0-9_]{3,20}")) { peer.setError("Ingresa +56912345678 o @usuario"); return; }
            go.setEnabled(false); status.setText("Buscando contacto...");
            db.collection(byPhone ? "phoneDirectory" : "usernames").document(byPhone ? query : handle).get().addOnCompleteListener(found -> {
                if (!found.isSuccessful()) {
                    go.setEnabled(true); status.setText("Error de Firebase: " + (found.getException() == null ? "comprueba las reglas de Firestore" : found.getException().getLocalizedMessage())); return;
                }
                if (!found.getResult().exists()) {
                    go.setEnabled(true); status.setText("No encontramos ese contacto. Debe guardar su teléfono en el perfil y permitir que lo encuentren."); return;
                }
                String other = found.getResult().getString("uid");
                if (other == null || other.equals(me.getUid())) {
                    go.setEnabled(true); status.setText("Selecciona otra persona."); return;
                }
                String id = chatId(me.getUid(), other);
                ArrayList<String> ids = new ArrayList<>(Arrays.asList(me.getUid(), other));
                Collections.sort(ids);
                java.util.Map<String,Object> data = new java.util.HashMap<>();
                data.put("memberIds", ids);
                data.put("createdAt", FieldValue.serverTimestamp());
                db.collection("chats").document(id).set(data)
                    .addOnSuccessListener(ok -> {
                        rememberChat(id, other);
                        showRemoteChat(id, other);
                    })
                    .addOnFailureListener(error -> {
                        go.setEnabled(true);
                        db.collection("chats").document(id).get().addOnCompleteListener(existing -> {
                            if (existing.isSuccessful() && existing.getResult().exists()) {
                                rememberChat(id, other); showRemoteChat(id, other);
                            } else status.setText("No se pudo abrir el chat: " + error.getLocalizedMessage());
                        });
                    });
            });
        });
        Button back = new Button(this); back.setText("Volver"); root.addView(back); back.setOnClickListener(v -> showHome());
    }
    private ImageView avatarView(String encoded) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(207, 226, 235));
        if (encoded != null && !encoded.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) image.setImageBitmap(bitmap);
            } catch (Exception ignored) { }
        }
        return image;
    }
    private void showProfile() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { showEmailLogin(); return; }
        start("Mi perfil");
        root.addView(text("Agrega tu nombre, correo y teléfono. Puedes elegir si otros usuarios podrán encontrarte por número.", 14, Color.DKGRAY, false));
        profilePreview = avatarView(pendingAvatar);
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        photoParams.setMargins(dp(22), dp(10), dp(22), dp(10)); root.addView(profilePreview, photoParams);
        Button photo = new Button(this); photo.setText("Elegir foto de galería"); root.addView(photo);
        photo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType("image/*");
            startActivityForResult(intent, PICK_AVATAR);
        });
        EditText display = new EditText(this); display.setHint("Tu nombre visible"); root.addView(display);
        EditText handle = new EditText(this); handle.setHint("Nombre de usuario (sin @)"); root.addView(handle);
        root.addView(text("Correo de acceso: " + (me.getEmail() == null ? "Sin correo" : me.getEmail()), 14, Color.DKGRAY, false));
        EditText phone = new EditText(this); phone.setHint("Teléfono, ejemplo +56912345678"); phone.setInputType(InputType.TYPE_CLASS_PHONE); root.addView(phone);
        android.widget.CheckBox discoverable = new android.widget.CheckBox(this); discoverable.setText("Permitir que me encuentren por teléfono (no verificado)"); root.addView(discoverable);
        TextView status = text("El nombre de usuario se elige una vez y debe ser único.", 13, Color.GRAY, false); root.addView(status);
        Button save = new Button(this); save.setText("Guardar perfil"); root.addView(save);
        db.collection("users").document(me.getUid()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                display.setText(doc.getString("displayName"));
                phone.setText(doc.getString("phone"));
                discoverable.setChecked(Boolean.TRUE.equals(doc.getBoolean("phoneDiscoverable")));
                String savedHandle = doc.getString("username");
                if (savedHandle != null) { handle.setText(savedHandle); handle.setEnabled(false); }
                String encoded = doc.getString("avatar");
                if (encoded != null && pendingAvatar.isEmpty()) {
                    pendingAvatar = encoded; profilePreview.setImageDrawable(avatarView(encoded).getDrawable());
                }
            }
        });
        save.setOnClickListener(v -> {
            String name = display.getText().toString().trim();
            String username = handle.getText().toString().trim().toLowerCase(java.util.Locale.ROOT).replaceFirst("^@", "");
            if (name.length() < 2 || name.length() > 40) { display.setError("Entre 2 y 40 caracteres"); return; }
            if (!username.matches("[a-z0-9_]{3,20}")) { handle.setError("Entre 3 y 20 letras, números o _"); return; }
            String number = phone.getText().toString().replace(" ", "").replace("-", "").trim();
            if (!number.isEmpty() && !number.matches("^\\+[1-9][0-9]{7,14}$")) { phone.setError("Usa el formato internacional, por ejemplo +56912345678"); return; }
            boolean searchable = discoverable.isChecked() && !number.isEmpty();
            save.setEnabled(false);
            db.runTransaction(transaction -> {
                com.google.firebase.firestore.DocumentReference profile = db.collection("users").document(me.getUid());
                com.google.firebase.firestore.DocumentReference alias = db.collection("usernames").document(username);
                com.google.firebase.firestore.DocumentSnapshot existing = transaction.get(profile);
                String old = existing.getString("username");
                if (old != null && !old.equals(username)) throw new IllegalStateException("Tu @usuario no se puede cambiar todavía");
                com.google.firebase.firestore.DocumentReference phoneRef = searchable ? db.collection("phoneDirectory").document(number) : null;
                com.google.firebase.firestore.DocumentSnapshot claimedPhone = phoneRef == null ? null : transaction.get(phoneRef);
                com.google.firebase.firestore.DocumentSnapshot claimed = transaction.get(alias);
                if (claimed.exists() && !me.getUid().equals(claimed.getString("uid"))) throw new IllegalStateException("Ese @usuario ya está ocupado");
                if (claimedPhone != null && claimedPhone.exists() && !me.getUid().equals(claimedPhone.getString("uid"))) throw new IllegalStateException("Ese número ya está registrado para búsquedas");
                String oldPhone = existing.getString("phone");
                if (oldPhone != null && !oldPhone.isEmpty() && !oldPhone.equals(number)) throw new IllegalStateException("El número registrado no se puede cambiar todavía");
                java.util.Map<String,Object> data = new java.util.HashMap<>();
                data.put("displayName", name); data.put("username", username);
                data.put("avatar", pendingAvatar);
                data.put("phone", number); data.put("phoneDiscoverable", searchable);
                if (!existing.exists()) data.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(profile, data, com.google.firebase.firestore.SetOptions.merge());
                if (!claimed.exists()) transaction.set(alias, java.util.Collections.singletonMap("uid", me.getUid()));
                if (phoneRef != null && (claimedPhone == null || !claimedPhone.exists())) transaction.set(phoneRef, java.util.Collections.singletonMap("uid", me.getUid()));
                return null;
            }).addOnSuccessListener(ok -> { pendingAvatar = ""; Toast.makeText(this, "Perfil guardado", Toast.LENGTH_SHORT).show(); showHome(); })
              .addOnFailureListener(e -> { save.setEnabled(true); status.setText("No se pudo guardar: " + e.getLocalizedMessage()); });
        });
        Button back = new Button(this); back.setText("Volver"); root.addView(back); back.setOnClickListener(v -> showHome());
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AVATAR || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream input = getContentResolver().openInputStream(data.getData())) {
            Bitmap original = BitmapFactory.decodeStream(input);
            if (original == null) throw new IllegalArgumentException("Imagen no válida");
            int width = original.getWidth(), height = original.getHeight();
            float scale = Math.min(1f, 192f / Math.max(width, height));
            Bitmap small = Bitmap.createScaledBitmap(original, Math.max(1, (int)(width * scale)), Math.max(1, (int)(height * scale)), true);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 65, output);
            pendingAvatar = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            if (profilePreview != null) profilePreview.setImageBitmap(small);
        } catch (Exception e) { Toast.makeText(this, "No se pudo abrir la imagen", Toast.LENGTH_LONG).show(); }
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
