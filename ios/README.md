# MensajeApp iOS — primera base nativa
Esta carpeta añade una app SwiftUI sin modificar la app Android. Usa las colecciones existentes de Firebase: users, chats, chats/{id}/messages y notificationTokens.

## Estado
Base inicial no compilada ni probada en iPhone. Incluye correo/contraseña, lista de chats, lectura/envío de texto y registro de token FCM. Faltan búsqueda de usuarios, fotos, audios y pruebas de push.

## Para generar el proyecto
1. En un Mac o runner macOS instala Xcode y XcodeGen.
2. Descarga desde Firebase Console el GoogleService-Info.plist de una app iOS registrada con bundle ID cl.mensajeapp.ios; guárdalo en ios/MensajeApp/GoogleService-Info.plist. **No publiques configuraciones sensibles ni credenciales privadas.**
3. En ios ejecuta xcodegen generate y abre MensajeApp.xcodeproj.
4. Configura Signing & Capabilities con tu equipo Apple; para push habilita Push Notifications y Background Modes / Remote notifications, y configura APNs en Firebase.
5. Compila y prueba en simulador; para instalar y distribuir a un iPhone sin Mac necesitas una solución de firma/distribución autorizada (por ejemplo, TestFlight con membresía Apple Developer).

## Cuenta Apple gratuita
Con Apple ID normal se puede probar localmente mediante Xcode en un Mac, con restricciones y perfiles de 7 días. GitHub no convierte por sí solo este proyecto en una IPA instalable. TestFlight requiere membresía Apple Developer.
