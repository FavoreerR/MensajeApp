# Firebase setup for MensajeApp

**Status:** Backend not deployed; the Android demo is offline. These rules are a starting point, not a completed secure messaging product.

1. Create or select a Firebase project in the Firebase Console.
2. Register Android app ID `cl.mensajeapp` and download its `google-services.json`.
3. Enable Firebase Authentication phone sign-in, configure SHA-1/SHA-256 fingerprints and phone verification/anti-abuse protections.
4. Create Firestore database in production mode, then review and deploy `firestore.rules` and `firestore.indexes.json` using Firebase CLI.
5. Implement authenticated Android screens, trusted contact discovery and backend validation before enabling chats for real users.

## Security caveats

- The current rules are a *draft*. Chat creation allows a signed-in user to name any second member. Production should validate consent/contacts and restrict membership creation using trusted server-side code.
- User profiles are private and cannot currently be searched by phone number. A phone lookup requires a privacy-preserving, rate-limited backend; do not expose all phone numbers publicly.
- Firebase phone verification can incur costs and may require billing.
- Do not commit service-account JSON, private keys, passwords or signing keystores.
- Firebase configuration alone does not enable messaging; Android integration is still required.
