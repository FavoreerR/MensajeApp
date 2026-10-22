const encoder = new TextEncoder();
const decode = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - s.length % 4) % 4)), c => c.charCodeAt(0));
const b64 = (data) => btoa(String.fromCharCode(...new Uint8Array(data))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
const jsonB64 = (obj) => b64(encoder.encode(JSON.stringify(obj)));
const response = (status, message) => new Response(JSON.stringify({message}), {status, headers: {"content-type":"application/json", "cache-control":"no-store"}});
const jwksUrl = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

async function verifyFirebaseIdToken(token, projectId) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid token");
  const header = JSON.parse(new TextDecoder().decode(decode(parts[0])));
  const payload = JSON.parse(new TextDecoder().decode(decode(parts[1])));
  if (header.alg !== "RS256" || !header.kid || payload.aud !== projectId ||
      payload.iss !== "https://securetoken.google.com/" + projectId ||
      !payload.sub || payload.exp <= Date.now() / 1000 ||
      payload.iat > Date.now() / 1000) throw new Error("Invalid claims");
  const res = await fetch(jwksUrl);
  if (!res.ok) throw new Error("Auth verification unavailable");
  const keys = (await res.json()).keys;
  const key = keys.find(k => k.kid === header.kid && k.kty === "RSA");
  if (!key) throw new Error("Unknown signing key");
  const cryptoKey = await crypto.subtle.importKey("jwk", key, {name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"}, false, ["verify"]);
  if (!await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, decode(parts[2]), encoder.encode(parts[0] + "." + parts[1]))) throw new Error("Invalid signature");
  return payload.sub;
}

async function getAdminToken(account) {
  const now = Math.floor(Date.now()/1000);
  const input = jsonB64({alg:"RS256",typ:"JWT"}) + "." + jsonB64({
    iss:account.client_email, scope:"https://www.googleapis.com/auth/cloud-platform",
    aud:"https://oauth2.googleapis.com/token", iat:now, exp:now+3600
  });
  const keyText = account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(keyText), c=>c.charCodeAt(0)), {name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"}, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5",key,encoder.encode(input));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},
    body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:input+"."+b64(signature)})
  });
  if (!res.ok) throw new Error("Google service authentication failed");
  return (await res.json()).access_token;
}

const field = (doc, key) => doc?.fields?.[key]?.stringValue;
async function getDoc(projectId, accessToken, path) {
  const res = await fetch("https://firestore.googleapis.com/v1/projects/"+projectId+"/databases/(default)/documents/"+path, {
    headers:{authorization:"Bearer "+accessToken}
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error("Firestore request failed: "+res.status);
  return res.json();
}

export default {
  async fetch(request, env) {
    if (request.method !== "POST" || new URL(request.url).pathname !== "/notify") return response(404,"Not found");
    if (Number(request.headers.get("content-length") || 0) > 8192) return response(413,"Too large");
    const auth = request.headers.get("authorization") || "";
    if (!auth.startsWith("Bearer ")) return response(401,"Authentication required");
    let sender;
    try { sender = await verifyFirebaseIdToken(auth.slice(7), env.FIREBASE_PROJECT_ID); }
    catch { return response(401,"Invalid Firebase login"); }
    let body;
    try { body = await request.json(); } catch { return response(400,"Invalid JSON"); }
    const {chatId,messageId} = body || {};
    if (typeof chatId !== "string" || typeof messageId !== "string" ||
        !/^[A-Za-z0-9_-]{1,180}$/.test(chatId) || !/^[A-Za-z0-9_-]{1,180}$/.test(messageId)) return response(400,"Invalid identifiers");
    try {
      const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
      if (account.project_id !== env.FIREBASE_PROJECT_ID) throw new Error("Wrong Firebase project");
      const accessToken = await getAdminToken(account);
      const chat = await getDoc(env.FIREBASE_PROJECT_ID,accessToken,"chats/"+chatId);
      const members = chat?.fields?.memberIds?.arrayValue?.values?.map(v=>v.stringValue) || [];
      if (members.length !== 2 || !members.includes(sender)) return response(403,"Not a chat member");
      const message = await getDoc(env.FIREBASE_PROJECT_ID,accessToken,"chats/"+chatId+"/messages/"+messageId);
      if (!message || field(message,"senderId") !== sender) return response(403,"Not your message");
      const created = message.fields?.createdAt?.timestampValue;
      if (!created || Date.now()-Date.parse(created)>120000 || Date.parse(created)>Date.now()+30000) return response(409,"Message is not recent");
      const recipient = members.find(uid=>uid!==sender);
      const tokenDoc = await getDoc(env.FIREBASE_PROJECT_ID,accessToken,"notificationTokens/"+recipient);
      const token = field(tokenDoc,"token");
      if (!token) return response(202,"Recipient has not enabled notifications");
      const profile = await getDoc(env.FIREBASE_PROJECT_ID,accessToken,"users/"+sender);
      const name = field(profile,"displayName") || "MensajeApp";
      const messageText = field(message,"text") || "Tienes un mensaje nuevo";
      const sent = await fetch("https://fcm.googleapis.com/v1/projects/"+env.FIREBASE_PROJECT_ID+"/messages:send", {
        method:"POST", headers:{authorization:"Bearer "+accessToken,"content-type":"application/json"},
        body:JSON.stringify({message:{token,data:{title:name.slice(0,80),body:messageText.slice(0,160),chatId},android:{priority:"HIGH"}}})
      });
      if (!sent.ok) { console.error("FCM error",sent.status); return response(502,"FCM delivery failed"); }
      return response(200,"Notification submitted");
    } catch (error) {
      console.error("Notification server error",error.message);
      return response(500,"Notification service unavailable");
    }
  }
};
