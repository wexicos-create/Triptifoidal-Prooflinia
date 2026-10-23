# ⛏️ BOCAMINA PLUS — Mensajería de transporte oportunista sin Internet

**Proyecto Android nativo (Java), paquete `mx.rompebocas.bocamina`. Estado: v1.0.0-alpha.**

> «El mensaje se mueve como el mercurio: por donde haya hueco.»

El sistema mantiene un buzón local DTN (*Delay-Tolerant Networking*, almacenamiento y acarreo) para enviar mensajes cuando hay encuentro físico entre dispositivos. No necesita Internet ni una cuenta en la nube. No promete comunicación sin un medio físico ni entrega instantánea.

## Funciones implementadas en el código

- Identidad permanente por dispositivo a partir de una clave pública EC P-256 con huella SHA-256 y código QR `BID1:`.
- Contactos agregados **dentro de la app**, mediante lectura QR o código; no necesita agenda del teléfono, SIM ni número celular. Hay que verificar manualmente la huella para evitar suplantación.
- Mensajes de hasta 1200 caracteres, con TTL configurable (1 hora / 24 horas / 7 días / 14 días), prioridad normal o urgente y estados «en la mina / en camino / entregado / caducado».
- **Bundle Router** con paquete firmado por emisor, identificador único, timestamp, TTL, destino, prioridad y texto cifrado para el destinatario. Intermediarios solo transportan la carga opaca; no necesitan ser contactos.
- Cifrado E2E **EC P-256 ECDH + HKDF-HMAC-SHA256 + AES-256-GCM** y firmas **ECDSA-SHA256**. Mensajes e identidades cifrados también en reposo con AES-GCM y claves dentro de Android Keystore (cuando el dispositivo lo admite respaldadas por hardware). **No es aún Ed25519/XChaCha20-Poly1305 del diseño conceptual**, ni ofrece secreto hacia adelante o doble ratchet.
- Base SQLite con contenido sensible cifrado por registro e índices HMAC opacos. La estructura SQL y el tamaño del archivo no quedan ocultos; no equivale al cifrado integral de archivo SQLCipher. Android Backup deshabilitado.
- Transporte **Bluetooth Classic RFCOMM** con dispositivos ya emparejados desde Ajustes del sistema, intercambio bidireccional de hasta 25 bundles por visita. Enlace Bluetooth autenticado por emparejamiento más cifrado E2E del paquete. No se incorpora BLE mesh ni detección/discovery automáticos.
- «Correo Molinero»: carga cifrada mostrada en **QR animados** y escaneo sucesivo de fragmentos `BQR1:`; el teléfono acarreador puede volver a mostrarlos sin descifrar. Cada QR lleva un fragmento de 420 bytes, con límite de 50 fotogramas por paquete.
- Reenvío de acuse de entrega firmado por el destino. El remitente solo marca entregado cuando recibe y verifica el recibo, por el canal que sea. Deduplicación por ID.
- Interfaz oscura tinta-sepia/dorado; pantalla de buzón, conversación, contactos, carteros Bluetooth y QR; ajuste de las barras superiores/inferiores de Android 10–15 mediante `WindowInsets`.
- Workflow de GitHub Actions para compilar APK de **depuración firmado automáticamente** y subirlo como artefacto al completar CI.

## No implementado / limitaciones importantes

- **Radio enjambre BLE mesh, Wi-Fi Direct, transporte por luz LED-cámara, sonido casi-ultrasónico y modo molino solar autónomo**: están en la hoja de ruta, **no** están codificados como transporte activo. No se ofrece una barra ni botones que pretendan que funcionan.
- No hay llamadas de audio o video entre teléfonos en esta versión. Las llamadas exigen un enlace bidireccional estable y un motor de voz aparte; DTN no permite llamadas por acarreo humano diferido.
- No hay retransmisión automática programada a pares Bluetooth sin interacción del usuario ni descubrimiento continuo en segundo plano. El servicio receptor usa notificación persistente y Android puede detenerlo.
- No hay protección de secreto hacia adelante, revisión formal de seguridad, endurecimiento anti-DOS, rotación de claves, recuperación de identidad ni prueba E2E instrumentada en teléfonos físicos. No debe usarse aún para comunicaciones cuya exposición implique riesgo vital.
- Un paquete recibido por el destino **sin tener emparejada la identidad del remitente** queda guardado sin poder descifrarse; se necesita vincular previamente el contacto (por seguridad no se confía en claves autodeclaradas).
- Un emisor podrá reenviar los mismos paquetes muchas veces entre encuentros; la deduplicación del receptor evita duplicados lógicos, pero aumenta tráfico. Los acuses son retrasados y necesitan otra ruta de retorno.
- Esta rama experimental usa un protocolo propietario documentado en código, no es una implementación completa del estándar Bundle Protocol v7 de DTN.
- La promesa de «100 % offline» significa sin Internet **pero con un medio físico funcional**: Bluetooth, QR y transporte humano. No significa comunicar dispositivos completamente aislados.

## Cómo compilar

**Requisitos**: JDK 17, Android SDK 35, Gradle 8.9, Android Gradle Plugin 8.7.3.

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

En GitHub, `Actions > Compilar BOCAMINA Plus APK > Run workflow`. Una ejecución exitosa produce el artefacto `bocamina-plus-debug-apk` (APK de pruebas firmado con certificado debug). Para distribución comercial, crear y custodiar un keystore **release**, compilar `assembleRelease` y verificar la firma. Nunca subir llaves privadas al repositorio.

## Prueba funcional reproducible (2 teléfonos Android 10–15)

1. Instalar el mismo APK en A y B. Apagar datos móviles y desconectar Wi-Fi, dejando Bluetooth activo. Emparejarlos desde los Ajustes de Android.
2. Abrir BOCAMINA. Autorizar «Dispositivos cercanos» para Bluetooth. En cada equipo abrir `Contactos` y enseñar QR de identidad al otro; importar, comparar las huellas por un medio independiente.
3. En A escribir un mensaje a B, con TTL 24 h. Debe quedar **En la mina** sin conexiones.
4. En A abrir `Carteros` y pulsar `Intercambiar buzones` bajo el dispositivo emparejado B. En B debe aparecer el texto recibido, A aún puede quedar pendiente hasta un próximo intercambio del acuse.
5. Visitar B otra vez desde A: el acuse firmado vuelve y el estado cambia a **Entregado**.
6. Desemparejar ambos. Generar QR del mensaje y escanear sucesivamente todos sus fragmentos en B. El mensaje debe recibirse sin Bluetooth.
7. Prueba de acarreo: generar QR desde A; escanearlo con dispositivo intermediario C sin tener agregado al destinatario; desde C, abrir `Carteros` → `Ver paquetes` → `Mostrar QR` y permitir que B lo lea. C no podrá descifrar el texto.
8. Probar alteración de un byte, paquete caducado y falsificación de acuse: deben ser rechazados y no actualizar el estado de entrega.

## Mapa técnico

- `Bundle.java`: sobre DTN cifrado y firmado, con validación de versión/TTL/identidad.
- `BundleRouter.java`: buzón y acuses; selección de cargas a transportar.
- `Store.java`: Android SQLite con registros cifrados, contactos e identidad.
- `KeyVault.java`: AES/HMAC de Android Keystore.
- `CryptoEngine.java`: cifrado, firmas y acuerdo de claves.
- `CourierService.java`: servidor/cliente Bluetooth Classic con intercambio de cargas.
- `QrCarrier.java`: fotogramas QR binarios y reensamblaje.
- `MainActivity.java`: interfaz para operar los canales implementados.

Autoría del concepto y proyecto: **Josué Israel Cervantes Alvarado**.
