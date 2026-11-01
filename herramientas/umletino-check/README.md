# Verificación de compatibilidad con UMLetino

Herramienta de desarrollo (no forma parte de la aplicación) usada para comprobar que los `.uxf`
generados se abren correctamente en UMLetino **usando el propio código fuente de UMLetino 15.1**.

`UmletinoCompatCheck.java`:

1. Lee el `.uxf` replicando línea por línea `DiagramXmlParser.xmlToDiagram` de `umlet-gwt`
   (el código que ejecuta UMLetino en el navegador al importar un archivo).
2. Crea cada elemento con `ElementFactory` / `NewGridElement` de `umlet-elements` 15.1, el módulo que
   comparten UMLet (escritorio) y UMLetino (web). Si algún elemento fuera inválido, UMLetino mostraría
   "Diagram has invalid element"; la herramienta lo informa.
3. Verifica con `Stickables` (la lógica real de UMLetino) que **ambos extremos de cada relación quedan
   pegados a una clase**, condición para que las líneas acompañen a las cajas cuando se mueven.
4. Dibuja el diagrama en PNG con los comandos de dibujo que genera UMLet (`vista_previa_*.png`).

## Cómo ejecutarla

```bash
git clone --depth 1 --branch v15.1 https://github.com/umlet/umlet.git
# compilar umlet-elements (los parsers JavaCC se reemplazan por los stubs de ./stubs,
# que sólo afectan a "custom drawings" y diagramas de secuencia, no a clases ni relaciones)
javac -d ue -cp slf4j-api-1.7.36.jar $(find umlet/umlet-elements/src/main/java stubs -name "*.java")
javac -d chk -cp ue:slf4j-api-1.7.36.jar UmletinoCompatCheck.java
java -cp ue:slf4j-api-1.7.36.jar:chk UmletinoCompatCheck diagrama.uxf vista_previa.png
```

Salida esperada:

```
zoom_level=10  clases=10  relaciones=16  elementos inválidos=0  relaciones con extremos sueltos=0
RESULTADO: COMPATIBLE
```
