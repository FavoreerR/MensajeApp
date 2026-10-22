# MensajeApp: notificaciones push sin Firebase Blaze

El Worker recibe una solicitud autenticada del remitente **después** de guardar un mensaje en Firestore, comprueba en Firestore que el mensaje existe, que el usuario autenticado lo escribió y que pertenece al chat, y envía FCM al otro participante. Las credenciales administrativas **nunca** se incluyen en el APK.

## Publicación
1. Crea una cuenta en Cloudflare y habilita Workers Free.
2. En un equipo con Node.js, entra a esta carpeta y ejecuta `npx wrangler login`.
3. Crea una cuenta de servicio de Firebase con permisos mínimos para leer documentos necesarios de Firestore y enviar mensajes FCM. Descarga su JSON **solo a tu equipo**. Nunca lo subas a GitHub ni lo pegues en un chat.
4. Ejecuta `npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON` y pega el JSON cuando Wrangler lo solicite.
5. Ejecuta `npx wrangler deploy` y guarda la URL HTTPS del Worker.
6. Configura la URL del Worker en el cliente Android antes de compilar.

## Advertencias
- El plan Free de Cloudflare tiene cuotas; revisa los límites vigentes.
- Las solicitudes se disparan desde Android, no desde Firestore. Si el remitente pierde Internet inmediatamente después de guardar el mensaje, el aviso puede no salir; el mensaje permanece guardado.
- Para producción, añadir control de frecuencia y deduplicación persistente para impedir notificaciones repetidas, y evitar almacenar tokens en un único documento por usuario cuando use varios dispositivos.
- FCM no garantiza entrega inmediata en todos los dispositivos (modo ahorro de batería, permisos, conectividad).
