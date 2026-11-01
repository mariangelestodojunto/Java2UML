package especial;

/** Una interfaz que extiende otra: herencia entre interfaces. */
public interface Auditable extends Pagable, java.io.Serializable {
    String auditar();
}
