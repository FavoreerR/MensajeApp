import SwiftUI
import FirebaseAuth
import FirebaseFirestore
import FirebaseMessaging

struct Chat: Identifiable {
    let id: String
    let otherId: String
    var name: String = "Contacto"
}
struct ChatMessage: Identifiable {
    let id: String
    let senderId: String
    let text: String
}
@MainActor final class ChatStore: ObservableObject {
    @Published var user: User?
    @Published var chats: [Chat] = []
    @Published var messages: [ChatMessage] = []
    @Published var error = ""
    private var chatsListener: ListenerRegistration?
    private var messagesListener: ListenerRegistration?
    private var authHandle: AuthStateDidChangeListenerHandle?
    private let db = Firestore.firestore()
    init() {
        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                self?.user = user
                self?.chatsListener?.remove()
                self?.messagesListener?.remove()
                self?.chats = []
                self?.messages = []
                if let user { self?.registerToken(user.uid); self?.listenChats(user.uid) }
            }
        }
    }
    private func registerToken(_ uid: String) {
        Messaging.messaging().token { token, _ in
            guard Auth.auth().currentUser?.uid == uid, let token else { return }
            self.db.collection("notificationTokens").document(uid)
                .setData(["token": token, "updatedAt": FieldValue.serverTimestamp()])
        }
    }
    func signIn(_ email: String, _ password: String, create: Bool) {
        let done: (AuthDataResult?, Error?) -> Void = { [weak self] _, error in
            Task { @MainActor in self?.error = error?.localizedDescription ?? "" }
        }
        if create { Auth.auth().createUser(withEmail: email, password: password, completion: done) }
        else { Auth.auth().signIn(withEmail: email, password: password, completion: done) }
    }
    func signOut() { try? Auth.auth().signOut() }
    private func listenChats(_ uid: String) {
        chatsListener = db.collection("chats").whereField("memberIds", arrayContains: uid)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    if let error { self?.error = error.localizedDescription; return }
                    let items: [Chat] = (snapshot?.documents ?? []).compactMap { doc in
                        guard let members = doc.data()["memberIds"] as? [String],
                              members.count == 2, let other = members.first(where: { $0 != uid }) else { return nil }
                        return Chat(id: doc.documentID, otherId: other)
                    }
                    self?.chats = items
                    for item in items {
                        self?.db.collection("users").document(item.otherId).getDocument { doc, _ in
                            let name = doc?.get("displayName") as? String ?? "Contacto"
                            Task { @MainActor in
                                if let index = self?.chats.firstIndex(where: { $0.id == item.id }) {
                                    self?.chats[index].name = name
                                }
                            }
                        }
                    }
                }
            }
    }
    func listenMessages(_ chatId: String) {
        messagesListener?.remove()
        messages = []
        messagesListener = db.collection("chats").document(chatId).collection("messages")
            .order(by: "createdAt").limit(toLast: 100).addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    if let error { self?.error = error.localizedDescription; return }
                    self?.messages = (snapshot?.documents ?? []).map { doc in
                        ChatMessage(id: doc.documentID,
                                    senderId: doc.get("senderId") as? String ?? "",
                                    text: doc.get("text") as? String ?? "")
                    }
                }
            }
    }
    func send(_ text: String, chatId: String) {
        guard let uid = user?.uid, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        db.collection("chats").document(chatId).collection("messages")
            .addDocument(data: ["senderId": uid, "text": text, "createdAt": FieldValue.serverTimestamp()])
    }
}
struct ContentView: View {
    @StateObject private var store = ChatStore()
    @State private var email = ""
    @State private var password = ""
    var body: some View {
        NavigationStack {
            Group {
                if store.user == nil {
                    Form {
                        TextField("Correo electrónico", text: $email).textInputAutocapitalization(.never).keyboardType(.emailAddress)
                        SecureField("Contraseña", text: $password)
                        Button("Iniciar sesión") { store.signIn(email, password, create: false) }
                        Button("Crear cuenta") { store.signIn(email, password, create: true) }
                    }
                } else {
                    List(store.chats) { chat in
                        NavigationLink(chat.name) { ChatView(store: store, chat: chat) }
                    }
                    .toolbar { Button("Salir") { store.signOut() } }
                }
            }
            .navigationTitle("MensajeApp")
            .tint(.red)
            .alert("Aviso", isPresented: Binding(get: { !store.error.isEmpty },
                                                  set: { if !$0 { store.error = "" } })) {
                Button("Aceptar") { store.error = "" }
            } message: { Text(store.error) }
        }
    }
}
struct ChatView: View {
    @ObservedObject var store: ChatStore
    let chat: Chat
    @State private var draft = ""
    var body: some View {
        VStack {
            List(store.messages) { message in
                HStack {
                    if message.senderId == store.user?.uid { Spacer() }
                    Text(message.text).padding(10)
                        .background(message.senderId == store.user?.uid ? Color.red.opacity(0.15) : Color.gray.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    if message.senderId != store.user?.uid { Spacer() }
                }
            }
            HStack {
                TextField("Escribe un mensaje", text: $draft).textFieldStyle(.roundedBorder)
                Button("Enviar") { store.send(draft, chatId: chat.id); draft = "" }
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding()
        }
        .navigationTitle(chat.name)
        .onAppear { store.listenMessages(chat.id) }
    }
}