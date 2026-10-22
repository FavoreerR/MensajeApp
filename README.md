# MensajeApp

Aplicación **nativa Android** en desarrollo.

## Estado
- Interfaz inicial de conversaciones y chat local de demostración.
- **No** envía mensajes entre dispositivos ni autentica números todavía.
- Pendiente: integrar Firebase Authentication (teléfono), Firestore, reglas de seguridad y notificaciones.

## APK de prueba
En GitHub abre **Actions → Android APK → última ejecución correcta → Artifacts → MensajeApp-debug-apk**.
El APK de depuración es solo para pruebas, no para publicación.

## Compilación local
Requiere JDK 17, Android SDK 35 y Gradle 8.9.
Ejecuta `gradle :app:assembleDebug`.
