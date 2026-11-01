package com.baselet.element.facet.customdrawings.gen;
import com.baselet.diagram.draw.DrawHandler;
/** STUB de pruebas: el parser real lo genera JavaCC. Sólo se usa para dibujos personalizados (no aplica a UMLClass/Relation). */
public abstract class CustomDrawingParser {
  public CustomDrawingParser(CustomDrawingParserTokenManager tm){}
  public CustomDrawingParser(Provider p){}
  public abstract double getWidth();
  public abstract double getHeight();
  public abstract DrawHandler getDrawHandler();
  public final void start() throws ParseException { throw new ParseException("custom drawings no disponibles en el stub"); }
}
