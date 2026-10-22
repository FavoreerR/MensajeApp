const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {logger} = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.notifyNewMessage = onDocumentCreated(
  {document: "chats/{chatId}/messages/{messageId}", region: "southamerica-west1"},
  async (event) => {
    const message = event.data?.data();
    if (!message || typeof message.senderId !== "string") return;
    const db = admin.firestore();
    const chat = await db.doc("chats/" + event.params.chatId).get();
    if (!chat.exists) return;
    const members = chat.get("memberIds");
    if (!Array.isArray(members) || members.length !== 2 || !members.includes(message.senderId)) return;
    const recipient = members.find((id) => id !== message.senderId);
    if (!recipient) return;
    const tokenDoc = await db.doc("notificationTokens/" + recipient).get();
    const token = tokenDoc.get("token");
    if (typeof token !== "string" || !token) return;
    const senderDoc = await db.doc("users/" + message.senderId).get();
    const name = senderDoc.get("displayName") || "Nuevo mensaje";
    try {
      await admin.messaging().send({
        token,
        data: {
          title: String(name).slice(0, 80),
          body: typeof message.text === "string" ? message.text.slice(0, 160) : "Tienes un mensaje nuevo",
          chatId: event.params.chatId
        },
        android: {priority: "high"}
      });
      logger.info("Notification delivered to FCM", {recipient});
    } catch (error) {
      logger.error("Notification send failed", {code: error.code});
      if (error.code === "messaging/registration-token-not-registered") {
        await tokenDoc.ref.delete();
      }
    }
  }
);
