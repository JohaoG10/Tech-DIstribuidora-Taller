# TechDistribuidora con Apache Camel

Taller de Integración de Sistemas. **Johao Gavilanes y Erick Granda**, Ingeniería de Software, UDLA.

Apache Camel detecta archivos CSV en `input`, conserva los originales y escribe copias en `output`. Registra las copias y los errores. La práctica se ejecuta desde Google Colab usando Google Drive como carpeta compartida. La aplicación también puede ejecutarse localmente con Java y Maven.

## Empezar en el navegador

[Abrir el notebook en Colab](https://colab.research.google.com/github/JohaoG10/Tech-DIstribuidora-Taller/blob/main/TechDistribuidora_Camel_Colab.ipynb)

1. **Johao, destino:** crea `TechDistribuidora` en Mi unidad de Google Drive y dentro `input`, `output`, `logs`.
2. Comparte la carpeta con **Erick, origen**, como editor. Cada uno entra con su propia cuenta.
3. Abre el notebook, guarda una copia en Drive y ejecuta las celdas 1 a 5 en orden. Autoriza el montaje de tu cuenta. La preparación descarga Maven y dependencias al entorno remoto de Colab; no necesitas un IDE ni instalar programas en tu computadora.
4. Si ejecuta Erick, agrega un acceso directo de la carpeta compartida a Mi unidad y verifica la ruta. Solo un destino activo por carpeta.
5. **Erick:** descarga `ejemplos/pedidos_001.csv` de este repositorio y súbelo a `input` después de iniciar Camel. También sube `no_procesar.txt`.
6. **Johao:** ejecuta la celda 6. Espera la línea `COPIADO pedidos_001.csv -> output`; confirma la copia en Drive y usa la celda 7 para comparar contenido y ver el log.
7. Detén Camel con la celda 8 al terminar. Para la ampliación, activa `VALIDAR_PEDIDOS=True`, inicia de nuevo y sube `pedidos_invalidos.csv` con un nombre nuevo. Debe quedar en input, sin copia en output, con `ERROR_VALIDACION` en `logs/errores.csv`.
8. Guarden las capturas de su ejecución y completen el informe y la bitácora. El documento académico usa una estructura estándar; debe ajustarse si el docente entrega una plantilla UDLA.

## Distribución sugerida de los 35 minutos

| Minutos | Actividad | Responsable |
| --- | --- | --- |
| 0 a 5 | Compartir carpeta y abrir notebook | Ambos |
| 5 a 15 | Montar Drive y compilar Camel | Johao |
| 15 a 22 | Iniciar destino y subir CSV nuevo | Johao y Erick |
| 22 a 28 | Revisar copia, log, hashes y TXT | Ambos |
| 28 a 35 | Capturas, explicación y bitácora | Ambos |

La primera compilación depende de la conexión; preparen el notebook antes de clase si el docente lo permite.

## Qué hace cada archivo

- `pom.xml`: fija dependencias y genera un JAR ejecutable.
- `src/main/java/ec/edu/udla/App.java`: recibe carpetas, configura historial persistente e inicia Camel.
- `src/main/java/ec/edu/udla/FileTransferRoute.java`: ruta de transferencia, validación opcional y auditoría.
- `TechDistribuidora_Camel_Colab.ipynb`: prepara y ejecuta la aplicación Java en Colab.
- `ejemplos/`: CSV válidos, CSV inválido y TXT de control.
- El informe académico se entrega por separado en el chat, con espacios para capturas.
- `docs/BITACORA_PROMPTS.md`: prompts usados y registro de trabajo.
- `evidencias/pruebas_locales.txt`: resultado real de pruebas locales, separado de la evidencia pendiente de Drive.
- `tests/verificar_integracion.py`: prueba reproducible de la aplicación real con carpetas temporales.

## Ejecución local alternativa

Java 17, 21 o 25 y Maven 3.9.9 o compatible. Desde la raíz del proyecto:

```text
mvn --batch-mode package
java -jar target/techdistribuidora-1.0.0.jar
```

En otra ventana, copia un CSV desde ejemplos a input. Las rutas son **relativas a la carpeta donde inicias la aplicación**. `input/` y `output/` representan las carpetas lógicas del ejercicio; no se usa una carpeta raíz del sistema operativo.

Para activar validación:

```text
java -jar target/techdistribuidora-1.0.0.jar --validate=true
```

Para utilizar otras carpetas, pasa `--input=RUTA --output=RUTA --logs=RUTA`. En una terminal entrecomilla cada argumento completo si hay espacios. Los paths montados de Google Drive los pasa el notebook como argumentos seguros.

## Ruta y decisiones

```java
from("file:input?include=.*[.]csv&noop=true&delay=2000&readLock=changed")
    .to("file:output");
```

Esta es la idea básica. El código completo añade historial persistente, un archivo temporal de salida, logs y validación opcional.

- `include=.*[.]csv` acepta CSV, también extensiones en mayúsculas porque Camel compara el filtro sin distinguir el caso.
- `noop=true` conserva el original y activa idempotencia.
- `readLock=changed` espera estabilidad del archivo antes de leer; reduce el riesgo de copia incompleta, pero no es una transacción distribuida.
- Historial en `logs/processed-files.dat`: clave de ruta, tamaño y modificación; un archivo sin cambios no se procesa otra vez tras reiniciar. Capacidad de 10 000 entradas; para producción conviene una base de datos y una clave de negocio.
- `fileExist=Override`: una nueva versión con el mismo nombre reemplaza la copia previa. Usa nombres únicos si necesitas conservar versiones.
- `tempFileName`: escribe temporalmente antes de renombrar la salida. Drive montado puede tener demoras de sincronización.
- CSV inválido: se registra una vez y permanece en input. Corrígelo y súbelo de nuevo para cambiar su versión.
- Fallo técnico: tres reintentos por intercambio, log de error y original conservado. Si el problema continúa, el consumidor puede volver a intentar en futuros sondeos.
- Los logs y la copia no se guardan en una misma transacción: un fallo después de copiar podría dejar salida sin fila de auditoría. No se afirma garantía de exactamente una vez.

## Pruebas reproducibles

Después de compilar, con Python 3 instalado en un entorno local:

```text
python tests/verificar_integracion.py
```

El notebook no requiere esta prueba local para la práctica. El script inicia el JAR real, usa carpetas temporales y comprueba detección de CSV nuevo, conservación, contenido, filtro TXT, reinicio y validación.

## Preguntas del taller

**Patrón utilizado:** File Transfer. Dos sistemas intercambian datos mediante archivos en una carpeta compartida, sin requerir una API directa. La idempotencia y el filtro son mecanismos adicionales.

**Mejoras:** nombres únicos e historial en una base de datos, validación de esquema, detección de duplicados por pedido, confirmación de recepción, alertas y métricas, permisos mínimos, respaldo, manejo de archivos terminados con marcador y un servicio permanente fuera de Colab.

**Casos reales:** envío de pedidos entre ERP y distribuidor, consolidación de inventarios de sucursales, importación de facturación por lotes, integración con sistemas legados y recepción periódica de reportes.

## Limitaciones

Colab puede desconectarse; se usa para demostrar la integración, no para operación continua. El historial admite un único consumidor en esta demo y no coordina notebooks simultáneos. La validación es opcional y el esquema de ejemplos es una decisión del proyecto, no un esquema impuesto por el enunciado. Deben añadir capturas reales de la práctica en Drive antes de entregar.

## Referencias oficiales

- [Apache Camel File](https://camel.apache.org/components/4.22.x/file-component.html)
- [Camel 4.22.0 y Java compatible](https://camel.apache.org/releases/release-4.22.0/)
- [Colab y Google Drive](https://colab.research.google.com/notebooks/io.ipynb)
- [Preguntas frecuentes de Colab](https://research.google.com/colaboratory/faq.html)
