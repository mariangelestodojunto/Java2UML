# Java2UML

Genera **diagramas de clases UML** a partir del **código fuente Java** y los guarda como archivo
**`.uxf` editable en [UMLetino](https://www.umletino.com/umletino.html)**.

No hace falta Eclipse ni compilar el proyecto: se leen directamente los archivos `.java`
(Java 8 a 25: records, sealed, pattern matching, text blocks, etc.).

---

## Cómo se usa

### Requisitos

**Java 17 o superior** (por ejemplo *Eclipse Temurin 21*: https://adoptium.net/).

### Interfaz de escritorio

Doble clic en `java2uml.jar` (en Windows también `windows\Java2UML.bat`), o:

```bash
java -jar java2uml.jar
```

1. **Seleccionar proyecto…** → elegí la carpeta raíz del proyecto (la que contiene `src`).
2. Elegí el **paquete** en el árbol (marcá *Incluir subpaquetes* si querés también los paquetes hijos).
3. Ajustá las **opciones** del diagrama.
4. **Generar diagrama** → se muestra un resumen con las clases, las relaciones y lo que se omitió.
5. Elegí carpeta y nombre y presioná **Exportar .uxf**.

### Interfaz web

```bash
java -jar java2uml.jar --web          # http://localhost:8080
java -jar java2uml.jar --web 9000     # otro puerto
```

1. Arrastrá los archivos `.java`, la carpeta del proyecto o un `.zip`.
2. Se selecciona automáticamente el paquete con más clases (se puede cambiar).
3. Elegí las opciones y presioná **Generar diagrama**.
4. **Descargar .uxf** o **Copiar XML**.

Para publicarla con un link, el proyecto incluye un `Dockerfile` (y `render.yaml` para Render):

```bash
docker build -t java2uml .
docker run -p 8080:8080 java2uml
```

### Consola

```
java -jar java2uml.jar --cli <carpetaProyecto> --listar
java -jar java2uml.jar --cli <carpetaProyecto> <paquete> <salida.uxf> [opciones]
```

| Opción | Efecto |
|---|---|
| `--subpaquetes` | incluye los subpaquetes |
| `--metodos` / `--constructores` | muestra métodos / constructores |
| `--parametros` | muestra los nombres de los parámetros |
| `--accesores` | muestra también los getters y setters simples |
| `--externas` | incluye clases del proyecto de otros paquetes |
| `--sin-atributos` / `--sin-dependencias` | oculta atributos / dependencias |
| `--sin-interfaces` / `--sin-enums` | omite interfaces / enumeraciones |
| `--incluir-ejecucion` | dibuja también las clases de ejecución (Test, Main, App) |
| `--todas-las-dependencias` | no omite las dependencias alcanzables por asociaciones |
| `--constructores-servicio` | muestra los constructores de las clases de servicio |
| `--capas` | disposición por capas en lugar de cuadrícula |
| `--incluir-tests` | analiza también `src/test` |

Usá `.` como paquete para el paquete por defecto.

### Opciones del diagrama

| Opción | Por defecto |
|---|---|
| Mostrar atributos | ✔ |
| Mostrar métodos | ✘ |
| Mostrar constructores | ✘ |
| Mostrar nombres de parámetros | ✘ |
| Ocultar getters y setters simples | ✔ |
| Incluir interfaces / enumeraciones | ✔ |
| Mostrar dependencias | ✔ |
| Mostrar clases de otros paquetes | ✘ |
| Omitir clases de ejecución | ✔ |
| Omitir dependencias alcanzables por asociaciones | ✔ |
| Ocultar constructores de clases de servicio | ✔ |
| Disposición por capas | ✘ |

### Abrir el resultado en UMLetino

Abrí https://www.umletino.com/umletino.html y **arrastrá el `.uxf` sobre el área de dibujo** (o *File → Import*).
Cada clase y cada relación es un elemento independiente: se pueden mover y editar.

### Compilar desde el código fuente

```bash
mvn package                       # genera target/java2uml.jar
```

Sin Maven (Windows): `windows\compilar-sin-maven.bat`. Para un `.exe` autocontenido (incluye Java):
`windows\crear-exe.bat` (requiere JDK 17+).

---

## Reglas

### Cajas

```
<<interface>>                     /Material/                  <<enum>>
Prestable                         --                          Genero
--                                # titulo: String            --
_+ DIAS_MAXIMOS: int = 14_        - anio: int                 _+ NOVELA_
--                                --                          _+ ENSAYO_
/+ prestar(Socio): boolean/       + Material(String, int)
                                  + prestar(Socio): boolean
```

- Tres compartimentos separados por `--`: nombre, atributos y operaciones. Los enums sin operaciones
  visibles llevan sólo dos.
- Clases abstractas en itálica (`/Nombre/`); interfaces con `<<interface>>`; enums con `<<enum>>`.
- Atributos: `[visibilidad] [nombre]: [Tipo]`. Sólo atributos simples (primitivos, `String`, `LocalDate`…);
  los que referencian clases o enums del diagrama se dibujan como flecha y se quitan de la caja.
- `static` → subrayado (`_- CONTADOR: int_`). Constantes `static final` con valor literal →
  `_- MAX: int = 100_`.
- Los `final` de instancia con valor literal (valores por defecto como `VELOCIDAD_MAXIMA_DEFAULT = 100`)
  no se dibujan. Los `final` sin literal se muestran como un atributo normal (`- dni: String`).
- Constantes de un enum subrayadas: `_+ NOVELA_`.
- Operaciones: constructores primero, luego métodos. Parámetros sólo con tipos
  (`+ setNombre(String): void`) y `: void` explícito. Los métodos abstractos van en itálica.
- Los getters y setters simples (`getX()` que sólo devuelve un atributo, `setX(valor)` que sólo lo
  asigna) se ocultan por defecto. Los que validan o calculan algo se muestran.
- Las clases de servicio (usan otras clases sólo por parámetro y ninguna las referencia) muestran sus
  operaciones sin constructores.
- Visibilidad: `+` public, `-` private, `#` protected, `~` package.

### Relaciones

Se usan sólo dos tipos de flecha, ambas con punta abierta:

| Relación | Cuándo | En UMLetino |
|---|---|---|
| Asociación obligatoria | el atributo se fija al construir el objeto y ningún método lo cambia | `lt=<-` · `m1=1` · `- nombreAtributo` |
| Asociación opcional | no se asigna en ningún constructor, o un método lo modifica | `lt=<-` · `m1=0..1` · `- nombreAtributo` |
| Asociación 1 a N | `List<B>`, `ArrayList<B>`, `Set<B>`, `Map<K,B>`, `B[]` | `lt=<-` · `m1=*` · `- nombreAtributos` |
| Herencia | `extends` | `lt=<-` |
| Implementación | `implements` | `lt=<.` |
| Dependencia | parámetro, retorno, variable local, `new` o llamada estática, sin atributo | `lt=<.` |

**Multiplicidad indicada en el código:** si el enunciado define la multiplicidad, escribila como
comentario en el atributo (al final de la línea o en la línea anterior). Tiene prioridad sobre la regla
automática:

```java
private Fecha fechaFabricacion;   // 1
private Fecha fechaUltimaVTV;     // 0..1
private ArrayList<Persona> personas;   // 1..*
```

Valores válidos: `1`, `0..1`, `*`, `0..*`, `1..*` o cualquier `n..m`. Otros comentarios se ignoran.

**Multiplicidad automática de una referencia simple** (cuando no hay marca):

- `m1=1` si el atributo es `final`, o si se le asigna un valor (aunque venga por parámetro) al declararlo o
  en algún constructor **y ningún método lo vuelve a asignar**.
- `m1=0..1` en el resto de los casos (sólo `null` en los constructores, un setter, métodos agregar/quitar…).
- Asignar el literal `null` no cuenta como asignación. `Optional<B>` es `0..1`.

**Dependencias:**

- Si entre dos clases hay una asociación, herencia o implementación (en cualquier sentido), no se dibuja
  además una dependencia entre ellas.
- Si A ya llega a B navegando sus asociaciones (A → X → B), tampoco se dibuja la dependencia A → B.

**Qué no se dibuja:**

- Clases de ejecución: las llamadas `Test`, `Main` o `App`, y las que sólo contienen `main(...)`.
- Clases de la biblioteca estándar (`String`, `List`, `Date`…): las colecciones sólo aportan la
  multiplicidad `*`.
- Relaciones duplicadas.
- Referencias ambiguas (por ejemplo, dos clases con el mismo nombre que podrían corresponder): se
  informan en el resumen.

### Disposición

Las clases se ubican en una cuadrícula 2D de modo que las relaciones sean cortas, ninguna línea pase por
encima de otra caja, haya pocos cruces y el diagrama se lea de izquierda a derecha (contenedor a la
izquierda, contenido a la derecha; superclases arriba). Las relaciones son rectas de borde a borde y las
clases sin relaciones van en una fila al pie. Para el mismo código el resultado es siempre el mismo.
