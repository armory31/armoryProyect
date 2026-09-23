# Guía de Solución de Problemas (Debug)

Este documento detalla los errores comunes detectados en el entorno de desarrollo y sus soluciones.

## 1. Google Play Services (GMS) SecurityException
**Error:** `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'`

### Causa
Este error ocurre cuando el Sistema Operativo Android (en el emulador o dispositivo) no confía en la identidad del paquete de Google Play Services. No es un error de la aplicación, sino del framework del sistema que maneja el broker de servicios de Google.

### Solución
1.  **Wipe Data:** En el AVD Manager de Android Studio, selecciona tu emulador y haz clic en "Wipe Data". Esto reiniciará el estado de los servicios de sistema.
2.  **Imagen de Sistema:** Asegúrate de estar usando una imagen de sistema con "Google Play API" y que esté actualizada.
3.  **Fecha y Hora:** Verifica que la fecha y hora del emulador sean correctas, ya que esto afecta la validación de certificados de GMS.

---

## 2. InteractionJankMonitor (READ_DEVICE_CONFIG)
**Advertencia:** `Initializing without READ_DEVICE_CONFIG permission. enabled=false...`

### Causa
La librería de métricas de Jetpack Compose intenta leer configuraciones de rendimiento internas de Android que solo están disponibles para aplicaciones del sistema.

### Solución
Esta advertencia es informativa y **segura de ignorar**. No afecta la funcionalidad ni el rendimiento de la aplicación para el usuario final.

---

## 3. SLF4J (No providers found)
**Advertencia:** `SLF4J(W): No SLF4J providers were found.`

### Solución
Se ha incluido `slf4j-nop` en las dependencias de depuración para silenciar este aviso. No afecta al registro principal de la app que se realiza vía Timber.
