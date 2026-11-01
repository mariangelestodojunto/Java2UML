package com.umlgen.analysis;

import java.util.Map;
import java.util.Set;

/**
 * Conocimiento sobre tipos de la biblioteca estándar de Java. Se usa para:
 * no dibujar String, Integer, List, Date, etc. como clases del diagrama;
 * reconocer colecciones (multiplicidad "*") y detectar ambigüedades con imports "*".
 */
public final class JavaStandardTypes {
    private JavaStandardTypes() {
    }

    private static final Set<String> JAVA_LANG = Set.of(
            "Object", "String", "StringBuilder", "StringBuffer", "Integer", "Long", "Short", "Byte", "Double",
            "Float", "Boolean", "Character", "Number", "Math", "StrictMath", "System", "Thread", "Runnable",
            "Exception", "RuntimeException", "Error", "Throwable", "Iterable", "Comparable", "CharSequence",
            "Enum", "Record", "Class", "Void", "Cloneable", "AutoCloseable", "IllegalArgumentException",
            "IllegalStateException", "NullPointerException", "ArithmeticException", "IndexOutOfBoundsException",
            "ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException", "NumberFormatException",
            "UnsupportedOperationException", "ClassCastException", "InterruptedException",
            "CloneNotSupportedException", "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
            "SafeVarargs", "Process", "Runtime", "StackOverflowError", "OutOfMemoryError", "ThreadLocal",
            "AssertionError", "ClassNotFoundException", "NegativeArraySizeException", "SecurityException",
            "ReflectiveOperationException", "ProcessBuilder", "Package", "Module", "ClassLoader");

    private static final Map<String, Set<String>> KNOWN_PACKAGES = Map.of(
            "java.util", Set.of("ArrayList", "LinkedList", "List", "Map", "HashMap", "TreeMap", "LinkedHashMap",
                    "Set", "HashSet", "TreeSet", "LinkedHashSet", "Collection", "Collections", "Arrays", "Iterator",
                    "ListIterator", "Optional", "OptionalInt", "OptionalDouble", "OptionalLong", "Scanner", "Random",
                    "Date", "Calendar", "GregorianCalendar", "Queue", "Deque", "ArrayDeque", "PriorityQueue", "Stack",
                    "Vector", "Hashtable", "Objects", "UUID", "Comparator", "Locale", "Properties", "StringTokenizer",
                    "NavigableMap", "NavigableSet", "SortedMap", "SortedSet", "EnumMap", "EnumSet", "Timer",
                    "TimerTask", "Formatter", "Currency", "BitSet", "NoSuchElementException", "InputMismatchException",
                    "ConcurrentModificationException", "Spliterator", "StringJoiner", "IdentityHashMap",
                    "WeakHashMap", "AbstractList", "AbstractMap", "AbstractSet", "TimeZone"),
            "java.time", Set.of("LocalDate", "LocalDateTime", "LocalTime", "Instant", "Duration", "Period",
                    "DayOfWeek", "Month", "Year", "YearMonth", "MonthDay", "ZonedDateTime", "ZoneId", "ZoneOffset",
                    "OffsetDateTime", "OffsetTime", "Clock", "DateTimeException"),
            "java.io", Set.of("File", "IOException", "InputStream", "OutputStream", "PrintStream", "BufferedReader",
                    "FileReader", "FileWriter", "BufferedWriter", "Serializable", "FileNotFoundException",
                    "PrintWriter", "Reader", "Writer", "InputStreamReader", "OutputStreamWriter", "ObjectInputStream",
                    "ObjectOutputStream", "Closeable", "UncheckedIOException", "FileInputStream", "FileOutputStream",
                    "EOFException", "Console"),
            "java.math", Set.of("BigDecimal", "BigInteger", "RoundingMode", "MathContext"),
            "java.util.function", Set.of("Function", "Supplier", "Consumer", "Predicate", "BiFunction",
                    "BiConsumer", "BiPredicate", "UnaryOperator", "BinaryOperator", "IntFunction", "ToIntFunction",
                    "IntPredicate", "IntConsumer", "IntSupplier", "BooleanSupplier", "DoubleFunction"),
            "java.util.stream", Set.of("Stream", "Collectors", "IntStream", "LongStream", "DoubleStream",
                    "Collector", "StreamSupport"),
            "java.time.format", Set.of("DateTimeFormatter", "DateTimeParseException", "FormatStyle"),
            "java.text", Set.of("SimpleDateFormat", "DecimalFormat", "NumberFormat", "DateFormat",
                    "ParseException", "MessageFormat"),
            "java.util.concurrent", Set.of("ConcurrentHashMap", "CopyOnWriteArrayList", "ExecutorService",
                    "Executors", "Future", "CompletableFuture", "TimeUnit", "Callable", "BlockingQueue",
                    "LinkedBlockingQueue", "ConcurrentLinkedQueue", "ThreadLocalRandom"));

    /** Contenedores con muchos elementos: sus argumentos de tipo generan multiplicidad "*". */
    private static final Set<String> COLLECTIONS = Set.of(
            "Collection", "List", "Set", "Queue", "Deque", "ArrayList", "LinkedList", "HashSet", "TreeSet",
            "LinkedHashSet", "Vector", "Stack", "ArrayDeque", "PriorityQueue", "Iterable", "SortedSet",
            "NavigableSet", "Map", "HashMap", "TreeMap", "LinkedHashMap", "Hashtable", "SortedMap", "NavigableMap",
            "EnumMap", "EnumSet", "ConcurrentHashMap", "CopyOnWriteArrayList", "IdentityHashMap", "WeakHashMap",
            "BlockingQueue", "LinkedBlockingQueue", "ConcurrentLinkedQueue", "AbstractList", "AbstractSet",
            "AbstractMap");

    private static final Set<String> OPTIONALS = Set.of("Optional");

    public static boolean isJavaLang(String simpleName) {
        return JAVA_LANG.contains(simpleName);
    }

    /** true si el paquete (de un import "*") es estándar y contiene un tipo con ese nombre simple. */
    public static boolean isKnownInPackage(String pkg, String simpleName) {
        Set<String> names = KNOWN_PACKAGES.get(pkg);
        return names != null && names.contains(simpleName);
    }

    /** true si es un nombre simple conocido de la biblioteca estándar (aunque falte el import). */
    public static boolean isKnownStandardName(String simpleName) {
        if (JAVA_LANG.contains(simpleName)) {
            return true;
        }
        for (Set<String> names : KNOWN_PACKAGES.values()) {
            if (names.contains(simpleName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStandardPackage(String qualifiedName) {
        return qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax.")
                || qualifiedName.startsWith("jdk.") || qualifiedName.startsWith("sun.")
                || qualifiedName.startsWith("javafx.");
    }

    public static boolean isCollection(String simpleName) {
        return COLLECTIONS.contains(simpleName);
    }

    public static boolean isOptional(String simpleName) {
        return OPTIONALS.contains(simpleName);
    }
}
