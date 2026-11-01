package com.umlgen.ui;

import com.umlgen.app.App;
import com.umlgen.config.DiagramOptions;
import com.umlgen.model.ClassifierKind;
import com.umlgen.project.ProjectStructure;
import com.umlgen.service.DiagramGenerator;
import com.umlgen.service.GeneratedDiagram;
import com.umlgen.service.GenerationException;
import com.umlgen.uxf.ValidationResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** Ventana principal de la aplicación (Swing, en español). */
public final class MainWindow extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);

    private final DiagramGenerator generator = new DiagramGenerator();
    private ProjectStructure project;
    private GeneratedDiagram diagram;

    private final JTextField projectField = new JTextField();
    private final JButton chooseProjectButton = new JButton("Seleccionar proyecto…");
    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Paquetes");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree packageTree = new JTree(treeModel);
    private final JCheckBox subpackagesBox = new JCheckBox("Incluir subpaquetes");

    private final JCheckBox showAttributes = new JCheckBox("Mostrar atributos", true);
    private final JCheckBox showMethods = new JCheckBox("Mostrar métodos", false);
    private final JCheckBox showConstructors = new JCheckBox("Mostrar constructores", false);
    private final JCheckBox showParamNames = new JCheckBox("Mostrar nombres de parámetros", false);
    private final JCheckBox includeInterfaces = new JCheckBox("Incluir interfaces", true);
    private final JCheckBox includeEnums = new JCheckBox("Incluir enumeraciones", true);
    private final JCheckBox showDependencies = new JCheckBox("Mostrar dependencias", true);
    private final JCheckBox includeExternal = new JCheckBox("Mostrar clases externas (otros paquetes)", false);
    private final JCheckBox excludeTests = new JCheckBox("Ignorar código de pruebas (src/test)", true);
    private final JCheckBox excludeRunners = new JCheckBox("Omitir clases de ejecución (Test, Main, App)", true);
    private final JCheckBox omitNavigable = new JCheckBox("Omitir dependencias ya alcanzables por asociaciones", true);
    private final JCheckBox hideServiceCtors = new JCheckBox("Ocultar constructores de clases de servicio", true);
    private final JCheckBox layered = new JCheckBox("Disposición por capas (en lugar de cuadrícula)", false);

    private final JButton generateButton = new JButton("Generar diagrama");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel countsLabel = new JLabel("Clases: –   Relaciones: –");
    private final JTextArea summaryArea = new JTextArea();

    private final JTextField outputDirField = new JTextField();
    private final JButton chooseOutputButton = new JButton("Elegir carpeta…");
    private final JTextField fileNameField = new JTextField("diagrama.uxf", 18);
    private final JButton exportButton = new JButton("Exportar .uxf");

    public MainWindow() {
        super(App.NAME + " — Generador de diagramas de clases para UMLetino");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 640));
        setSize(1080, 800);
        setLocationRelativeTo(null);
        buildUi();
        wireEvents();
        updateButtons();
    }

    // ------------------------------------------------------------------------------------- construcción

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setContentPane(content);

        JPanel header = new JPanel(new BorderLayout(6, 6));
        JLabel title = new JLabel(App.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("Genera diagramas de clases UML editables (.uxf) para UMLetino a partir de código Java");
        JPanel titles = new JPanel(new BorderLayout());
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);
        header.add(titles, BorderLayout.NORTH);

        JPanel projectRow = new JPanel(new BorderLayout(6, 0));
        projectRow.setBorder(BorderFactory.createTitledBorder("1. Proyecto Java"));
        projectField.setEditable(false);
        projectRow.add(new JLabel("Carpeta raíz:"), BorderLayout.WEST);
        projectRow.add(projectField, BorderLayout.CENTER);
        projectRow.add(chooseProjectButton, BorderLayout.EAST);
        header.add(projectRow, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        // árbol de paquetes
        JPanel packagesPanel = new JPanel(new BorderLayout(4, 4));
        packagesPanel.setBorder(BorderFactory.createTitledBorder("2. Paquete"));
        packageTree.setRootVisible(false);
        packageTree.setShowsRootHandles(true);
        packageTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        packagesPanel.add(new JScrollPane(packageTree), BorderLayout.CENTER);
        packagesPanel.add(subpackagesBox, BorderLayout.SOUTH);

        // opciones
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("3. Opciones del diagrama"));
        for (JCheckBox b : new JCheckBox[]{showAttributes, showMethods, showConstructors, showParamNames,
                includeInterfaces, includeEnums, showDependencies, includeExternal}) {
            optionsPanel.add(b);
        }
        optionsPanel.add(Box.createVerticalStrut(8));
        JLabel advanced = new JLabel("Avanzadas:");
        advanced.setFont(advanced.getFont().deriveFont(Font.ITALIC));
        optionsPanel.add(advanced);
        optionsPanel.add(excludeRunners);
        optionsPanel.add(omitNavigable);
        optionsPanel.add(hideServiceCtors);
        optionsPanel.add(layered);
        hideServiceCtors.setToolTipText("Clases que sólo usan otras por parámetro, como PlantaVTV: se muestran sus operaciones");
        optionsPanel.add(excludeTests);
        excludeRunners.setToolTipText("Clases llamadas Test, Main o App, o que sólo contienen main(...)");
        omitNavigable.setToolTipText("Ej.: Agenda recibe una Fecha pero ya llega a ella por Agenda → Persona → Fecha");
        includeExternal.setToolTipText("Agrega las clases del proyecto de otros paquetes que son referenciadas");
        JPanel optionsWrapper = new JPanel(new BorderLayout());
        optionsWrapper.add(optionsPanel, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, packagesPanel, new JScrollPane(optionsWrapper));
        split.setResizeWeight(0.55);
        split.setDividerLocation(520);

        // generación y resumen
        JPanel generationPanel = new JPanel(new BorderLayout(6, 6));
        generationPanel.setBorder(BorderFactory.createTitledBorder("4. Generación"));
        JPanel genRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        generateButton.setFont(generateButton.getFont().deriveFont(Font.BOLD));
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setPreferredSize(new Dimension(260, 22));
        genRow.add(generateButton);
        genRow.add(progressBar);
        genRow.add(countsLabel);
        generationPanel.add(genRow, BorderLayout.NORTH);
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        summaryArea.setText("Seleccioná la carpeta de un proyecto Java para comenzar.");
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setPreferredSize(new Dimension(400, 200));
        generationPanel.add(summaryScroll, BorderLayout.CENTER);

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, generationPanel);
        vertical.setResizeWeight(0.5);
        vertical.setDividerLocation(400);
        content.add(vertical, BorderLayout.CENTER);

        // exportación
        JPanel exportPanel = new JPanel(new GridBagLayout());
        exportPanel.setBorder(BorderFactory.createTitledBorder("5. Exportación"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.gridy = 0;
        c.gridx = 0;
        exportPanel.add(new JLabel("Carpeta de destino:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        exportPanel.add(outputDirField, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        exportPanel.add(chooseOutputButton, c);
        c.gridy = 1;
        c.gridx = 0;
        exportPanel.add(new JLabel("Nombre del archivo:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        exportPanel.add(fileNameField, c);
        c.gridx = 2;
        exportButton.setFont(exportButton.getFont().deriveFont(Font.BOLD));
        exportPanel.add(exportButton, c);
        content.add(exportPanel, BorderLayout.SOUTH);
    }

    private void wireEvents() {
        chooseProjectButton.addActionListener(e -> chooseProject());
        chooseOutputButton.addActionListener(e -> chooseOutputDir());
        generateButton.addActionListener(e -> generate(false));
        exportButton.addActionListener(e -> {
            if (diagram == null) {
                generate(true);
            } else {
                export();
            }
        });
        packageTree.addTreeSelectionListener(e -> {
            invalidateDiagram();
            PackageNode p = selectedPackage();
            if (p != null) {
                String last = p.fullName.isEmpty() ? "diagrama" : p.fullName.substring(p.fullName.lastIndexOf('.') + 1);
                fileNameField.setText(last + ".uxf");
                if (!p.real) {
                    subpackagesBox.setSelected(true);
                }
            }
            updateButtons();
        });
        for (JCheckBox b : new JCheckBox[]{subpackagesBox, showAttributes, showMethods, showConstructors,
                showParamNames, includeInterfaces, includeEnums, showDependencies, includeExternal, excludeTests, excludeRunners, omitNavigable, hideServiceCtors, layered}) {
            b.addActionListener(e -> invalidateDiagram());
        }
    }

    // ------------------------------------------------------------------------------------- acciones

    private void chooseProject() {
        JFileChooser chooser = new JFileChooser(PREFS.get("lastProject", System.getProperty("user.home")));
        chooser.setDialogTitle("Seleccionar la carpeta raíz del proyecto Java");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = chooser.getSelectedFile().toPath();
        PREFS.put("lastProject", dir.toString());
        loadProject(dir);
    }

    /** Explora el proyecto en segundo plano y carga el árbol de paquetes. */
    public void loadProject(Path dir) {
        projectField.setText(dir.toString());
        if (outputDirField.getText().isBlank()) {
            outputDirField.setText(dir.toString());
        }
        setBusy(true, "Explorando proyecto…");
        new SwingWorker<ProjectStructure, Void>() {
            @Override
            protected ProjectStructure doInBackground() throws Exception {
                return generator.scan(dir);
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    project = get();
                    fillTree();
                    summaryArea.setText("Proyecto: " + dir + "\n"
                            + "Archivos .java: " + project.files().size() + "\n"
                            + "Paquetes: " + project.packages().size() + "\n"
                            + "Carpetas de código fuente detectadas:\n  " + String.join("\n  ",
                            project.sourceRoots().stream().map(p -> project.projectRoot().relativize(p).toString())
                                    .map(s -> s.isEmpty() ? "." : s).toList())
                            + (project.warnings().isEmpty() ? "" : "\n\nAdvertencias:\n  " + String.join("\n  ", project.warnings()))
                            + "\n\nElegí un paquete y presioná \"Generar diagrama\".");
                } catch (ExecutionException ex) {
                    project = null;
                    treeRoot.removeAllChildren();
                    treeModel.reload();
                    showError("No se pudo cargar el proyecto", cause(ex));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                updateButtons();
            }
        }.execute();
    }

    private void fillTree() {
        treeRoot.removeAllChildren();
        Map<String, DefaultMutableTreeNode> byName = new HashMap<>();
        for (String pkg : project.packages()) {
            if (pkg.isEmpty()) {
                PackageNode pn = new PackageNode("", ProjectStructure.DEFAULT_PACKAGE_LABEL);
                pn.real = true;
                pn.fileCount = project.fileCount("");
                DefaultMutableTreeNode n = new DefaultMutableTreeNode(pn);
                treeRoot.add(n);
                continue;
            }
            String[] parts = pkg.split("\\.");
            DefaultMutableTreeNode parent = treeRoot;
            StringBuilder name = new StringBuilder();
            for (String part : parts) {
                if (name.length() > 0) {
                    name.append('.');
                }
                name.append(part);
                String full = name.toString();
                DefaultMutableTreeNode node = byName.get(full);
                if (node == null) {
                    node = new DefaultMutableTreeNode(new PackageNode(full, part));
                    byName.put(full, node);
                    parent.add(node);
                }
                parent = node;
            }
            PackageNode pn = (PackageNode) parent.getUserObject();
            pn.real = true;
            pn.fileCount = project.fileCount(pkg);
        }
        treeModel.reload();
        // expandir todo y seleccionar el primer paquete con archivos
        for (int i = 0; i < packageTree.getRowCount(); i++) {
            packageTree.expandRow(i);
        }
        Enumeration<?> e = treeRoot.depthFirstEnumeration();
        DefaultMutableTreeNode first = null;
        while (e.hasMoreElements()) {
            Object o = e.nextElement();
            if (o instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof PackageNode p && p.real) {
                if (first == null || n.getLevel() < first.getLevel()) {
                    first = n;
                }
            }
        }
        if (first != null) {
            packageTree.setSelectionPath(new TreePath(first.getPath()));
        }
    }

    private void chooseOutputDir() {
        JFileChooser chooser = new JFileChooser(outputDirField.getText().isBlank()
                ? System.getProperty("user.home") : outputDirField.getText());
        chooser.setDialogTitle("Carpeta donde guardar el archivo .uxf");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void generate(boolean thenExport) {
        PackageNode pkg = selectedPackage();
        if (project == null || pkg == null) {
            return;
        }
        DiagramOptions options = readOptions();
        if (!pkg.real && !options.includeSubpackages) {
            showError("Paquete vacío", "El paquete " + pkg.fullName
                    + " no contiene clases propias. Activá \"Incluir subpaquetes\" o elegí otro paquete.");
            return;
        }
        setBusy(true, "Analizando…");
        new SwingWorker<GeneratedDiagram, Object[]>() {
            @Override
            protected GeneratedDiagram doInBackground() throws Exception {
                return generator.generate(project, pkg.fullName, options,
                        (p, m) -> publish(new Object[]{p, m}));
            }

            @Override
            protected void process(java.util.List<Object[]> chunks) {
                Object[] last = chunks.get(chunks.size() - 1);
                progressBar.setValue((Integer) last[0]);
                progressBar.setString((String) last[1]);
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    diagram = get();
                    long classes = diagram.model().classifiers().size();
                    countsLabel.setText("Clases: " + classes + "  (interfaces: "
                            + diagram.model().count(ClassifierKind.INTERFACE) + ", enums: "
                            + diagram.model().count(ClassifierKind.ENUM) + ")   Relaciones: "
                            + diagram.model().relations().size());
                    summaryArea.setText(diagram.summary());
                    summaryArea.setCaretPosition(0);
                    progressBar.setValue(100);
                    progressBar.setString("Diagrama generado");
                    if (thenExport) {
                        export();
                    }
                } catch (ExecutionException ex) {
                    diagram = null;
                    progressBar.setValue(0);
                    progressBar.setString("Error");
                    countsLabel.setText("Clases: –   Relaciones: –");
                    summaryArea.setText("No se pudo generar el diagrama:\n" + cause(ex));
                    showError("No se pudo generar el diagrama", cause(ex));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                updateButtons();
            }
        }.execute();
    }

    private void export() {
        if (diagram == null) {
            return;
        }
        String dirText = outputDirField.getText().trim();
        if (dirText.isEmpty()) {
            showError("Falta la carpeta de destino", "Elegí la carpeta donde guardar el archivo.");
            return;
        }
        String name = fileNameField.getText().trim();
        if (name.isEmpty()) {
            showError("Falta el nombre del archivo", "Escribí un nombre para el archivo .uxf.");
            return;
        }
        if (name.matches(".*[\\\\/:*?\"<>|].*")) {
            showError("Nombre inválido", "El nombre no puede contener los caracteres \\ / : * ? \" < > |");
            return;
        }
        if (!name.toLowerCase().endsWith(".uxf")) {
            name += ".uxf";
            fileNameField.setText(name);
        }
        Path dir = Path.of(dirText);
        Path file = dir.resolve(name);
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this,
                "El archivo " + file.getFileName() + " ya existe. ¿Querés reemplazarlo?", "Confirmar",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        GeneratedDiagram d = diagram;
        setBusy(true, "Exportando…");
        new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() throws Exception {
                return generator.export(d, file);
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    ValidationResult v = get();
                    progressBar.setString("Archivo exportado");
                    StringBuilder msg = new StringBuilder();
                    msg.append(v.isValid() ? "El diagrama se exportó correctamente.\n\n" : "El archivo se escribió, pero la validación encontró problemas.\n\n");
                    msg.append("Archivo: ").append(file.toAbsolutePath()).append("\n");
                    msg.append("Cajas: ").append(v.classCount()).append("   Relaciones: ").append(v.relationCount()).append("\n");
                    if (!v.errors().isEmpty()) {
                        msg.append("\nErrores:\n  ").append(String.join("\n  ", v.errors())).append('\n');
                    }
                    if (!v.warnings().isEmpty()) {
                        msg.append("\nAvisos de diseño: ").append(v.warnings().size()).append('\n');
                    }
                    msg.append("\nPara verlo: abrí https://www.umletino.com → File → Import… y elegí el archivo.");
                    summaryArea.append("\n\n=== Exportación ===\n" + msg);
                    Object[] buttons = Desktop.isDesktopSupported() ? new Object[]{"Aceptar", "Abrir carpeta"} : new Object[]{"Aceptar"};
                    int choice = JOptionPane.showOptionDialog(MainWindow.this, msg.toString(), "Exportación",
                            JOptionPane.DEFAULT_OPTION, v.isValid() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                            null, buttons, buttons[0]);
                    if (choice == 1) {
                        try {
                            Desktop.getDesktop().open(dir.toFile());
                        } catch (Exception ex) {
                            showError("No se pudo abrir la carpeta", ex.getMessage());
                        }
                    }
                } catch (ExecutionException ex) {
                    showError("No se pudo exportar", cause(ex));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                updateButtons();
            }
        }.execute();
    }

    // ------------------------------------------------------------------------------------- utilidades

    private DiagramOptions readOptions() {
        DiagramOptions o = new DiagramOptions();
        o.includeSubpackages = subpackagesBox.isSelected();
        o.showAttributes = showAttributes.isSelected();
        o.showMethods = showMethods.isSelected();
        o.showConstructors = showConstructors.isSelected();
        o.showParameterNames = showParamNames.isSelected();
        o.includeInterfaces = includeInterfaces.isSelected();
        o.includeEnums = includeEnums.isSelected();
        o.showDependencies = showDependencies.isSelected();
        o.includeExternalClasses = includeExternal.isSelected();
        o.excludeTestSources = excludeTests.isSelected();
        o.excludeRunnerClasses = excludeRunners.isSelected();
        o.omitNavigableDependencies = omitNavigable.isSelected();
        o.hideServiceConstructors = hideServiceCtors.isSelected();
        o.layeredLayout = layered.isSelected();
        return o;
    }

    private PackageNode selectedPackage() {
        TreePath path = packageTree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return o instanceof PackageNode p ? p : null;
    }

    private void invalidateDiagram() {
        if (diagram != null) {
            diagram = null;
            countsLabel.setText("Clases: –   Relaciones: –");
            progressBar.setValue(0);
            progressBar.setString("Opciones modificadas: volvé a generar");
        }
    }

    private boolean busy;

    private void setBusy(boolean value, String message) {
        busy = value;
        progressBar.setIndeterminate(value && message.startsWith("Explorando"));
        if (value) {
            progressBar.setValue(0);
            progressBar.setString(message);
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean ready = !busy && project != null && selectedPackage() != null;
        chooseProjectButton.setEnabled(!busy);
        generateButton.setEnabled(ready);
        exportButton.setEnabled(ready);
    }

    private static String cause(ExecutionException ex) {
        Throwable c = ex.getCause() == null ? ex : ex.getCause();
        if (c instanceof GenerationException) {
            return c.getMessage();
        }
        return "Error inesperado: " + c;
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /** Último recurso: muestra cualquier excepción no controlada sin cerrar la aplicación. */
    public static void showUnexpectedError(Throwable e) {
        e.printStackTrace();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "Ocurrió un error inesperado:\n" + e + "\n\nLa aplicación sigue funcionando.",
                App.NAME, JOptionPane.ERROR_MESSAGE));
    }
}
