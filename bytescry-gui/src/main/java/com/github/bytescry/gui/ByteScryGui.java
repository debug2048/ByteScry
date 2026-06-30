package com.github.bytescry.gui;

import com.github.bytescry.api.DecompilerEngine;
import com.github.bytescry.api.DecompilerEngineRegistry;
import com.github.bytescry.api.ArtifactDecompilerEngine;
import com.github.bytescry.bytecode.BytecodePrinter;
import com.github.bytescry.loader.ClassFileLoader;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * JavaFX graphical entry point for ByteScry.
 */
public class ByteScryGui extends Application {

    private static final DecompilerOptions DEFAULT_OPTIONS = new DecompilerOptions("17", false, null);
    private static final String STYLESHEET = "/com/github/bytescry/gui/app.css";
    private static final String ABOUT_PROPERTIES = "/com/github/bytescry/gui/about.properties";
    private static final String APP_ICON = "/com/github/bytescry/gui/bytescry-icon.png";
    private static final String APP_NAME = appMetadata("app.name", "ByteScry");
    private static final String APP_VERSION = appMetadata("app.version", "1.0.0");
    private static final String EMPTY_SOURCE = "// Open a .class, .jar, or directory to begin.\n"
            + "// Decompiled source will appear here.";
    private static final String EMPTY_BYTECODE = "// JVM bytecode will appear after selecting a class.";
    private static final String EMPTY_DIAGNOSTICS = "// Diagnostics will appear here.";
    private static final String ENGINE_AUTO = "Auto";
    private static final double DEFAULT_WIDTH = 1280;
    private static final double DEFAULT_HEIGHT = 820;
    private static final double MIN_WIDTH = 900;
    private static final double MIN_HEIGHT = 560;
    private static final double WINDOW_BAR_HEIGHT = 40;

    private TreeView<ClassNode> classTree;
    private TextArea sourceArea;
    private TextArea compareLeftArea;
    private TextArea compareRightArea;
    private TextArea bytecodeArea;
    private TextArea diagnosticsArea;
    private TabPane editorTabs;
    private Tab sourceTab;
    private Tab compareTab;
    private Tab bytecodeTab;
    private Tab diagnosticsTab;
    private Label statusLabel;
    private Label loadedCountLabel;
    private Label selectedClassLabel;
    private Label workspacePathLabel;
    private Label editorTitleLabel;
    private Label editorMetaLabel;
    private ProgressIndicator progressIndicator;
    private ProgressIndicator projectProgressIndicator;
    private Button maximizeButton;
    private Button exportButton;
    private TextField searchField;
    private TextField sourceSearchField;
    private ComboBox<String> sourceEngineCombo;
    private CheckMenuItem showCompareMenuItem;
    private CheckMenuItem showBytecodeMenuItem;
    private CheckMenuItem showDiagnosticsMenuItem;

    private List<ClassFile> loadedClassFiles;
    private ClassFile selectedClassFile;
    private Path loadedPath;
    private final Map<String, Map<String, DecompilationResult>> decompilationCaches = new ConcurrentHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean manuallyMaximized;
    private boolean sourceEnginePinned;
    private boolean updatingSourceEngineCombo;
    private String lastCompareQuery;
    private int lastCompareCursor;
    private long workspaceGeneration;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
    private double dragOffsetX;
    private double dragOffsetY;
    private double dragOffsetRatioX;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setTitle(APP_NAME);
        loadApplicationIcon(primaryStage);
        primaryStage.setResizable(true);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createTopArea(primaryStage));
        root.setCenter(createWorkbench());
        root.setBottom(createStatusBar());
        installWorkspaceDropHandlers(root);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(DEFAULT_WIDTH, Math.max(MIN_WIDTH, visualBounds.getWidth() - 48));
        double initialHeight = Math.min(DEFAULT_HEIGHT, Math.max(MIN_HEIGHT, visualBounds.getHeight() - 48));

        Scene scene = new Scene(root, initialWidth, initialHeight);
        URL stylesheet = getClass().getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(Math.min(MIN_WIDTH, visualBounds.getWidth()));
        primaryStage.setMinHeight(Math.min(MIN_HEIGHT, visualBounds.getHeight()));
        primaryStage.setX(visualBounds.getMinX() + Math.max(0, (visualBounds.getWidth() - initialWidth) / 2));
        primaryStage.setY(visualBounds.getMinY() + Math.max(16, (visualBounds.getHeight() - initialHeight) / 2));
        primaryStage.show();

        setBusy(false);
        setStatus("Ready. Open a .class file, .jar file, or directory.");
    }

    private void loadApplicationIcon(Stage stage) {
        try (InputStream input = getClass().getResourceAsStream(APP_ICON)) {
            if (input != null) {
                stage.getIcons().add(new Image(input));
            }
        } catch (IOException ignored) {
            // The icon is cosmetic; startup should not fail if the resource is unavailable.
        }
    }

    private VBox createTopArea(Stage stage) {
        VBox topArea = new VBox(createWindowBar(stage), createCommandBar());
        topArea.getStyleClass().add("top-area");
        return topArea;
    }

    private BorderPane createWindowBar(Stage stage) {
        MenuBar menuBar = createMenuBar();
        menuBar.getStyleClass().add("window-menu-bar");
        menuBar.setMinWidth(186);
        menuBar.setPrefWidth(210);
        menuBar.setMinHeight(WINDOW_BAR_HEIGHT);
        menuBar.setPrefHeight(WINDOW_BAR_HEIGHT);

        Label title = new Label(APP_NAME);
        title.getStyleClass().add("window-bar-title");

        Region dragRegion = new Region();
        HBox dragArea = new HBox(10, title, dragRegion);
        dragArea.getStyleClass().add("window-drag-area");
        dragArea.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dragRegion, Priority.ALWAYS);
        HBox.setHgrow(dragArea, Priority.ALWAYS);
        installWindowDragHandlers(dragArea, stage);

        Button minimizeButton = createWindowButton("-");
        minimizeButton.setTooltip(new Tooltip("Minimize"));
        minimizeButton.setOnAction(e -> stage.setIconified(true));

        maximizeButton = createWindowButton("[ ]");
        maximizeButton.setTooltip(new Tooltip("Maximize"));
        maximizeButton.setOnAction(e -> toggleMaximized(stage));

        Button closeButton = createWindowButton("X");
        closeButton.getStyleClass().add("close-window-button");
        closeButton.setTooltip(new Tooltip("Close"));
        closeButton.setOnAction(e -> Platform.exit());

        HBox windowButtons = new HBox(minimizeButton, maximizeButton, closeButton);
        windowButtons.getStyleClass().add("window-buttons");
        windowButtons.setAlignment(Pos.CENTER_RIGHT);

        BorderPane windowBar = new BorderPane();
        windowBar.getStyleClass().add("window-bar");
        windowBar.setMinHeight(WINDOW_BAR_HEIGHT);
        windowBar.setPrefHeight(WINDOW_BAR_HEIGHT);
        windowBar.setLeft(menuBar);
        windowBar.setCenter(dragArea);
        windowBar.setRight(windowButtons);
        return windowBar;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem openFile = new MenuItem("Open...");
        openFile.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
        openFile.setOnAction(e -> openFile());

        MenuItem openFolder = new MenuItem("Open Folder...");
        openFolder.setAccelerator(KeyCombination.keyCombination("Ctrl+Shift+O"));
        openFolder.setOnAction(e -> openFolder());

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(openFile, openFolder, new SeparatorMenuItem(), exit);

        Menu viewMenu = new Menu("View");
        showCompareMenuItem = new CheckMenuItem("Compare");
        showCompareMenuItem.setSelected(true);
        showCompareMenuItem.setOnAction(e -> updateOptionalTabs());

        showBytecodeMenuItem = new CheckMenuItem("Bytecode");
        showBytecodeMenuItem.setSelected(true);
        showBytecodeMenuItem.setOnAction(e -> updateOptionalTabs());

        showDiagnosticsMenuItem = new CheckMenuItem("Diagnostics");
        showDiagnosticsMenuItem.setSelected(false);
        showDiagnosticsMenuItem.setOnAction(e -> updateOptionalTabs());

        viewMenu.getItems().addAll(showCompareMenuItem, showBytecodeMenuItem, showDiagnosticsMenuItem);

        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> showAbout());
        helpMenu.getItems().add(about);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private void installWindowDragHandlers(Node dragArea, Stage stage) {
        dragArea.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || isWindowButtonTarget(event.getTarget())) {
                return;
            }
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
            dragOffsetRatioX = stage.getWidth() <= 0 ? 0.5 : event.getSceneX() / stage.getWidth();
        });
        dragArea.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown() || isWindowButtonTarget(event.getTarget())) {
                return;
            }
            if (manuallyMaximized) {
                restoreFromMaximizedForDrag(stage, event.getScreenX(), event.getScreenY());
            }
            moveStageWithinCurrentScreen(stage, event.getScreenX() - dragOffsetX, event.getScreenY() - dragOffsetY,
                    event.getScreenX(), event.getScreenY());
        });
        dragArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && !isWindowButtonTarget(event.getTarget())) {
                toggleMaximized(stage);
            }
        });
    }

    private Button createWindowButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("window-button");
        button.setFocusTraversable(false);
        return button;
    }

    private boolean isWindowButtonTarget(EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node.getStyleClass().contains("window-button")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private void toggleMaximized(Stage stage) {
        if (manuallyMaximized) {
            restoreWindow(stage);
        } else {
            maximizeWindow(stage);
        }
    }

    private void maximizeWindow(Stage stage) {
        saveRestoreBounds(stage);
        Rectangle2D bounds = getScreenForStage(stage).getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        manuallyMaximized = true;
        maximizeButton.setText("][");
        maximizeButton.setTooltip(new Tooltip("Restore"));
    }

    private void restoreWindow(Stage stage) {
        stage.setX(restoreX);
        stage.setY(restoreY);
        stage.setWidth(restoreWidth);
        stage.setHeight(restoreHeight);
        manuallyMaximized = false;
        maximizeButton.setText("[ ]");
        maximizeButton.setTooltip(new Tooltip("Maximize"));
    }

    private void restoreFromMaximizedForDrag(Stage stage, double screenX, double screenY) {
        double width = Math.max(stage.getMinWidth(), restoreWidth > 0 ? restoreWidth : 1280);
        double height = Math.max(stage.getMinHeight(), restoreHeight > 0 ? restoreHeight : 820);
        manuallyMaximized = false;
        maximizeButton.setText("[ ]");
        maximizeButton.setTooltip(new Tooltip("Maximize"));
        stage.setWidth(width);
        stage.setHeight(height);
        dragOffsetX = Math.max(120, Math.min(width - 120, width * dragOffsetRatioX));
        dragOffsetY = 16;
        moveStageWithinCurrentScreen(stage, screenX - dragOffsetX, screenY - dragOffsetY, screenX, screenY);
    }

    private void saveRestoreBounds(Stage stage) {
        if (!manuallyMaximized) {
            restoreX = stage.getX();
            restoreY = stage.getY();
            restoreWidth = stage.getWidth();
            restoreHeight = stage.getHeight();
        }
    }

    private Screen getScreenForStage(Stage stage) {
        List<Screen> screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        if (!screens.isEmpty()) {
            return screens.get(0);
        }
        return Screen.getPrimary();
    }

    private Screen getScreenForPointer(double screenX, double screenY) {
        List<Screen> screens = Screen.getScreensForRectangle(screenX, screenY, 1, 1);
        if (!screens.isEmpty()) {
            return screens.get(0);
        }
        return Screen.getPrimary();
    }

    private void moveStageWithinCurrentScreen(Stage stage, double targetX, double targetY, double pointerX, double pointerY) {
        Rectangle2D bounds = getScreenForPointer(pointerX, pointerY).getVisualBounds();
        double minVisibleWidth = Math.min(180, stage.getWidth());
        double minX = bounds.getMinX() - stage.getWidth() + minVisibleWidth;
        double maxX = bounds.getMaxX() - minVisibleWidth;
        double minY = bounds.getMinY();
        double maxY = bounds.getMaxY() - 40;

        stage.setX(clamp(targetX, minX, maxX));
        stage.setY(clamp(targetY, minY, maxY));
    }

    private double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private HBox createCommandBar() {
        Label appName = new Label(APP_NAME);
        appName.getStyleClass().add("app-name");

        VBox titleBox = new VBox(appName);
        titleBox.getStyleClass().add("title-box");

        Button openButton = new Button("Open...");
        openButton.getStyleClass().addAll("command-button", "primary-command");
        openButton.setTooltip(new Tooltip("Open a .class, .jar, or Android artifact"));
        openButton.setOnAction(e -> openFile());

        exportButton = new Button("Export Sources");
        exportButton.getStyleClass().add("command-button");
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> exportSources());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox commandBar = new HBox(12, titleBox, openButton, spacer, exportButton);
        commandBar.getStyleClass().add("command-bar");
        commandBar.setAlignment(Pos.CENTER_LEFT);
        return commandBar;
    }

    private SplitPane createWorkbench() {
        SplitPane splitPane = new SplitPane(createClassTreePane(), createEditorPane());
        splitPane.getStyleClass().add("workbench");
        splitPane.setDividerPositions(0.26);
        return splitPane;
    }

    private VBox createClassTreePane() {
        TreeItem<ClassNode> root = new TreeItem<>(new ClassNode("Classes", null, true));
        root.setExpanded(true);

        classTree = new TreeView<>(root);
        classTree.getStyleClass().add("class-tree");
        classTree.setShowRoot(false);
        classTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(ClassNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setContextMenu(null);
                } else {
                    setText((item.isFolder() ? "pkg  " : "class ") + item.getDisplayName());
                    setTooltip(new Tooltip(item.getDisplayName()));
                    setContextMenu(createClassTreeContextMenu(item));
                }
            }
        });
        classTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.getValue().isFolder()) {
                selectClass(newVal.getValue().getClassFile());
            }
        });

        Label explorerLabel = new Label("PROJECT");
        explorerLabel.getStyleClass().add("section-label");

        projectProgressIndicator = new ProgressIndicator();
        projectProgressIndicator.getStyleClass().add("project-progress");
        projectProgressIndicator.setMaxSize(14, 14);
        projectProgressIndicator.setVisible(false);
        projectProgressIndicator.setManaged(false);

        Region explorerSpacer = new Region();
        HBox.setHgrow(explorerSpacer, Priority.ALWAYS);
        HBox explorerHeader = new HBox(8, explorerLabel, explorerSpacer, projectProgressIndicator);
        explorerHeader.setAlignment(Pos.CENTER_LEFT);

        loadedCountLabel = new Label("No classes loaded");
        loadedCountLabel.getStyleClass().add("muted-label");

        workspacePathLabel = new Label("Open a build artifact or classes directory");
        workspacePathLabel.getStyleClass().add("path-label");
        workspacePathLabel.setWrapText(false);
        workspacePathLabel.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        workspacePathLabel.setMaxWidth(Double.MAX_VALUE);
        workspacePathLabel.setTooltip(new Tooltip(workspacePathLabel.getText()));

        VBox header = new VBox(5, explorerHeader, loadedCountLabel, workspacePathLabel);
        header.getStyleClass().add("sidebar-header");

        searchField = new TextField();
        searchField.getStyleClass().addAll("search-field", "sidebar-search-field");
        searchField.setPromptText("Search classes");
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyClassFilter(newValue));
        VBox.setMargin(searchField, new Insets(10, 12, 6, 12));

        VBox pane = new VBox(header, searchField, classTree);
        pane.getStyleClass().add("sidebar");
        pane.setMinWidth(250);
        pane.setPrefWidth(320);
        VBox.setVgrow(classTree, Priority.ALWAYS);
        installWorkspaceDropHandlers(pane);
        return pane;
    }

    private ContextMenu createClassTreeContextMenu(ClassNode item) {
        ContextMenu menu = new ContextMenu();
        if (item.isFolder()) {
            MenuItem copyPackage = new MenuItem("Copy Package Name");
            copyPackage.setOnAction(e -> copyToClipboard(packageClipboardValue(item), "Copied package name"));
            menu.getItems().add(copyPackage);
        } else if (item.getClassFile() != null) {
            MenuItem copyClass = new MenuItem("Copy Class Name");
            copyClass.setOnAction(e -> copyToClipboard(item.getClassFile().getClassName().replace('/', '.'),
                    "Copied class name"));
            MenuItem copyInternalName = new MenuItem("Copy Internal Name");
            copyInternalName.setOnAction(e -> copyToClipboard(item.getClassFile().getClassName(),
                    "Copied internal class name"));
            menu.getItems().addAll(copyClass, copyInternalName);
        }
        return menu;
    }

    private String packageClipboardValue(ClassNode item) {
        return "(default package)".equals(item.getDisplayName()) ? "" : item.getDisplayName();
    }

    private void copyToClipboard(String value, String status) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value == null ? "" : value);
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(status);
    }

    private BorderPane createEditorPane() {
        editorTitleLabel = new Label("No class selected");
        editorTitleLabel.getStyleClass().add("editor-title");

        editorMetaLabel = new Label("Open a file to inspect source and bytecode");
        editorMetaLabel.getStyleClass().add("editor-meta");

        VBox titleBox = new VBox(2, editorTitleLabel, editorMetaLabel);

        sourceSearchField = new TextField();
        sourceSearchField.getStyleClass().addAll("search-field", "source-search-field");
        sourceSearchField.setPromptText("Find in class");
        sourceSearchField.setOnAction(e -> findInActiveEditor(false));

        sourceEngineCombo = new ComboBox<>();
        sourceEngineCombo.getStyleClass().add("engine-combo");
        sourceEngineCombo.getItems().addAll(availableSourceEngines());
        setDisplayedSourceEngine(defaultSourceEngine());
        sourceEngineCombo.setTooltip(new Tooltip("Current source engine"));
        sourceEngineCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingSourceEngineCombo && selectedClassFile != null && newValue != null && !newValue.equals(oldValue)) {
                sourceEnginePinned = true;
                loadSelectedSource();
            }
        });

        Button previousMatchButton = new Button("<");
        previousMatchButton.getStyleClass().add("compact-button");
        previousMatchButton.setTooltip(new Tooltip("Previous match"));
        previousMatchButton.setOnAction(e -> findInActiveEditor(true));

        Button nextMatchButton = new Button(">");
        nextMatchButton.getStyleClass().add("compact-button");
        nextMatchButton.setTooltip(new Tooltip("Next match"));
        nextMatchButton.setOnAction(e -> findInActiveEditor(false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, titleBox, spacer, new Label("Engine"), sourceEngineCombo,
                sourceSearchField, previousMatchButton, nextMatchButton);
        header.getStyleClass().add("editor-header");
        header.setAlignment(Pos.CENTER_LEFT);

        sourceArea = createReadOnlyTextArea(EMPTY_SOURCE);
        compareLeftArea = createReadOnlyTextArea("// Primary source will appear here.");
        compareRightArea = createReadOnlyTextArea("// Comparison source will appear here.");
        SplitPane comparePane = new SplitPane(createCodeView(compareLeftArea), createCodeView(compareRightArea));
        comparePane.getStyleClass().add("compare-pane");
        comparePane.setDividerPositions(0.5);
        bytecodeArea = createReadOnlyTextArea(EMPTY_BYTECODE);
        diagnosticsArea = createReadOnlyTextArea(EMPTY_DIAGNOSTICS);

        sourceTab = createEditorTab("Source", createCodeView(sourceArea));
        compareTab = createEditorTab("Compare", comparePane);
        bytecodeTab = createEditorTab("Bytecode", createCodeView(bytecodeArea));
        diagnosticsTab = createEditorTab("Diagnostics", createCodeView(diagnosticsArea));

        editorTabs = new TabPane(sourceTab, compareTab, bytecodeTab);
        editorTabs.getStyleClass().add("editor-tabs");
        editorTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (selectedClassFile != null) {
                if (newTab == sourceTab) {
                    loadSelectedSource();
                } else if (newTab == compareTab) {
                    loadCompareSource(selectedClassFile);
                } else if (newTab == diagnosticsTab) {
                    renderDiagnostics();
                }
            }
            if (sourceSearchField != null && !sourceSearchField.getText().isBlank()) {
                findInActiveEditor(false);
            }
        });

        BorderPane editorPane = new BorderPane();
        editorPane.getStyleClass().add("editor-pane");
        editorPane.setTop(header);
        editorPane.setCenter(editorTabs);
        return editorPane;
    }

    private Tab createEditorTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private void updateOptionalTabs() {
        if (editorTabs == null) {
            return;
        }
        ensureTabVisible(sourceTab, true);
        ensureTabVisible(compareTab, showCompareMenuItem == null || showCompareMenuItem.isSelected());
        ensureTabVisible(bytecodeTab, showBytecodeMenuItem == null || showBytecodeMenuItem.isSelected());
        ensureTabVisible(diagnosticsTab, showDiagnosticsMenuItem != null && showDiagnosticsMenuItem.isSelected());
    }

    private void ensureTabVisible(Tab tab, boolean visible) {
        if (tab == null) {
            return;
        }
        boolean present = editorTabs.getTabs().contains(tab);
        if (visible && !present) {
            if (tab == sourceTab) {
                editorTabs.getTabs().add(0, tab);
            } else if (tab == compareTab) {
                editorTabs.getTabs().add(Math.min(1, editorTabs.getTabs().size()), tab);
            } else if (tab == bytecodeTab) {
                int index = editorTabs.getTabs().contains(compareTab) ? 2 : 1;
                editorTabs.getTabs().add(Math.min(index, editorTabs.getTabs().size()), tab);
            } else {
                editorTabs.getTabs().add(tab);
            }
        } else if (!visible && present) {
            editorTabs.getTabs().remove(tab);
            if (editorTabs.getSelectionModel().getSelectedItem() == null) {
                editorTabs.getSelectionModel().select(sourceTab);
            }
        }
    }

    private TextArea createReadOnlyTextArea(String initialText) {
        TextArea textArea = new TextArea(initialText);
        textArea.getStyleClass().add("code-area");
        textArea.setEditable(false);
        textArea.setWrapText(false);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return textArea;
    }

    private Node createCodeView(TextArea textArea) {
        TextArea lineNumbers = new TextArea();
        lineNumbers.getStyleClass().addAll("code-area", "line-number-gutter");
        lineNumbers.setEditable(false);
        lineNumbers.setFocusTraversable(false);
        lineNumbers.setMouseTransparent(true);
        lineNumbers.setWrapText(false);
        textArea.scrollTopProperty().addListener((obs, oldValue, newValue) ->
                lineNumbers.setScrollTop(newValue.doubleValue()));
        textArea.textProperty().addListener((obs, oldValue, newValue) -> updateLineNumberGutter(lineNumbers, newValue));
        updateLineNumberGutter(lineNumbers, textArea.getText());

        BorderPane codeView = new BorderPane();
        codeView.getStyleClass().add("code-view");
        codeView.setLeft(lineNumbers);
        codeView.setCenter(textArea);
        VBox.setVgrow(codeView, Priority.ALWAYS);
        return codeView;
    }

    private void updateLineNumberGutter(TextArea lineNumbers, String text) {
        int lines = lineCount(text);
        int digits = Math.max(2, Integer.toString(lines).length());
        StringBuilder builder = new StringBuilder(lines * (digits + 1));
        for (int i = 1; i <= lines; i++) {
            builder.append(String.format("%" + digits + "d", i));
            if (i < lines) {
                builder.append(System.lineSeparator());
            }
        }
        lineNumbers.setText(builder.toString());
        double width = 22 + digits * 8.0;
        lineNumbers.setMinWidth(width);
        lineNumbers.setPrefWidth(width);
        lineNumbers.setMaxWidth(width);
    }

    private int lineCount(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private HBox createStatusBar() {
        progressIndicator = new ProgressIndicator();
        progressIndicator.getStyleClass().add("status-progress");
        progressIndicator.setMaxSize(14, 14);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-text");
        statusLabel.setMaxHeight(30);
        statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        statusLabel.setWrapText(false);

        selectedClassLabel = new Label("No selection");
        selectedClassLabel.getStyleClass().add("status-selection");
        selectedClassLabel.setMaxHeight(30);
        selectedClassLabel.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        selectedClassLabel.setWrapText(false);
        selectedClassLabel.setMaxWidth(360);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        HBox statusBar = new HBox(10, progressIndicator, statusLabel, spacer, selectedClassLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setMinHeight(30);
        statusBar.setPrefHeight(30);
        statusBar.setMaxHeight(30);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(0, 12, 0, 12));
        return statusBar;
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open bytecode artifact");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Bytecode artifacts", "*.class", "*.jar", "*.apk", "*.dex", "*.aab", "*.apks", "*.apkm", "*.xapk"),
                new FileChooser.ExtensionFilter("Java files", "*.class", "*.jar"),
                new FileChooser.ExtensionFilter("Android files", "*.apk", "*.dex", "*.aab", "*.apks", "*.apkm", "*.xapk"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        Path initialDirectory = initialOpenDirectory();
        if (Files.isDirectory(initialDirectory)) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        File selected = chooser.showOpenDialog(null);
        if (selected != null) {
            loadPath(resolveWorkspacePath(selected.toPath()));
        }
    }

    private void openFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open class folder");
        Path initialDirectory = initialOpenDirectory();
        if (Files.isDirectory(initialDirectory)) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        File selected = chooser.showDialog(null);
        if (selected != null) {
            loadPath(resolveWorkspacePath(selected.toPath()));
        }
    }

    private Path initialOpenDirectory() {
        if (loadedPath != null) {
            Path current = loadedPath;
            if (Files.isRegularFile(current)) {
                current = current.getParent();
            }
            if (current != null && Files.isDirectory(current)) {
                return current;
            }
        }
        return defaultOpenDirectory();
    }

    private Path defaultOpenDirectory() {
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            Path desktop = Path.of(userHome, "Desktop");
            if (Files.isDirectory(desktop)) {
                return desktop;
            }
            Path home = Path.of(userHome);
            if (Files.isDirectory(home)) {
                return home;
            }
        }
        Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().getRoot();
        return root == null ? Path.of(".").toAbsolutePath().normalize() : root;
    }

    private Path resolveWorkspacePath(Path selectedPath) {
        if (!Files.isDirectory(selectedPath)) {
            return selectedPath;
        }
        try {
            boolean hasClassFiles;
            try (var stream = Files.walk(selectedPath)) {
                hasClassFiles = stream
                        .filter(Files::isRegularFile)
                        .anyMatch(path -> path.toString().toLowerCase().endsWith(".class"));
            }
            if (hasClassFiles) {
                return selectedPath;
            }
            try (var stream = Files.list(selectedPath)) {
                List<Path> artifacts = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> isSupportedWorkspaceFile(path.toFile()))
                        .sorted()
                        .toList();
                if (artifacts.size() == 1) {
                    return artifacts.get(0);
                }
            }
        } catch (IOException ignored) {
        }
        return selectedPath;
    }

    private boolean isSupportedWorkspaceFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".class")
                || lower.endsWith(".jar")
                || lower.endsWith(".apk")
                || lower.endsWith(".dex")
                || lower.endsWith(".aab")
                || lower.endsWith(".apks")
                || lower.endsWith(".apkm")
                || lower.endsWith(".xapk");
    }

    private void installWorkspaceDropHandlers(Node node) {
        node.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles() && firstSupportedDropPath(dragboard.getFiles()) != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        node.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            Path droppedPath = dragboard.hasFiles() ? firstSupportedDropPath(dragboard.getFiles()) : null;
            if (droppedPath != null) {
                loadPath(resolveWorkspacePath(droppedPath));
                event.setDropCompleted(true);
            } else {
                setStatus("Drop a .class, .jar, Android artifact, or class folder");
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private Path firstSupportedDropPath(List<File> files) {
        return files.stream()
                .filter(file -> file.isDirectory() || isSupportedWorkspaceFile(file))
                .map(File::toPath)
                .findFirst()
                .orElse(null);
    }

    private void loadPath(Path path) {
        long generation = ++workspaceGeneration;
        loadedPath = path;
        loadedClassFiles = List.of();
        selectedClassFile = null;
        sourceEnginePinned = false;
        lastCompareQuery = null;
        lastCompareCursor = 0;
        decompilationCaches.clear();
        diagnostics.clear();
        clearClassTree();
        setProjectControlsDisabled(true);
        loadedCountLabel.setText("Loading...");
        setWorkspacePathText(path.toString());
        editorTitleLabel.setText("Loading workspace");
        editorMetaLabel.setText(isAndroidArtifact(path) ? "Indexing Android sources with JADX" : "Scanning bytecode files");
        selectedClassLabel.setText("No selection");
        sourceArea.setText(EMPTY_SOURCE);
        setCompareText("// Primary source will appear here.", "// Comparison source will appear here.");
        bytecodeArea.setText(EMPTY_BYTECODE);
        diagnosticsArea.setText(EMPTY_DIAGNOSTICS);
        exportButton.setDisable(true);
        searchField.clear();
        sourceSearchField.clear();
        refreshSourceEngineChoices();
        setDisplayedSourceEngine(recommendedSourceEngine());
        setProjectLoading(true);
        setBusy(true);
        setStatus("Loading " + path + "...");

        CompletableFuture.runAsync(() -> {
            try {
                List<ClassFile> loaded = loadWorkspaceClasses(path);
                Platform.runLater(() -> {
                    if (!isCurrentWorkspace(generation)) {
                        return;
                    }
                    loadedClassFiles = loaded;
                    populateClassTree(loadedClassFiles);
                    loadedCountLabel.setText(loadedClassFiles.size() + " class(es)");
                    setStatus("Loaded " + loadedClassFiles.size() + " class(es) from " + path);
                    setProjectLoading(false);
                    setProjectControlsDisabled(false);
                    setBusy(false);
                    exportButton.setDisable(loadedClassFiles.isEmpty());
                    selectInitialClass();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!isCurrentWorkspace(generation)) {
                        return;
                    }
                    loadedCountLabel.setText("Load failed");
                    editorTitleLabel.setText("No class selected");
                    editorMetaLabel.setText("Open a file to inspect source and bytecode");
                    setStatus("Failed to load: " + e.getMessage());
                    setProjectLoading(false);
                    setProjectControlsDisabled(false);
                    setBusy(false);
                    showError("Load error", e.getMessage());
                });
            }
        });
    }

    private boolean isCurrentWorkspace(long generation) {
        return generation == workspaceGeneration;
    }

    private void setWorkspacePathText(String pathText) {
        workspacePathLabel.setText(pathText);
        Tooltip tooltip = workspacePathLabel.getTooltip();
        if (tooltip == null) {
            workspacePathLabel.setTooltip(new Tooltip(pathText));
        } else {
            tooltip.setText(pathText);
        }
    }

    private List<ClassFile> loadWorkspaceClasses(Path path) throws IOException, InterruptedException {
        if (isAndroidArtifact(path)) {
            DecompilerEngine engine = DecompilerEngineRegistry.get("jadx");
            if (engine instanceof ArtifactDecompilerEngine artifactEngine) {
                List<ClassFile> artifactClasses = artifactEngine.loadArtifactClasses(path,
                        DEFAULT_OPTIONS.withInputPath(path.toString()).withBestEffort(true).withFallbackEnabled(true));
                if (!artifactClasses.isEmpty()) {
                    return artifactClasses;
                }
            }
        }
        ClassFileLoader loader = new ClassFileLoader();
        if (Files.isDirectory(path)) {
            List<ClassFile> classes = new ArrayList<>(loader.load(path));
            List<Path> androidArtifacts = directAndroidArtifacts(path);
            if (!androidArtifacts.isEmpty()) {
                Set<String> androidSources = new HashSet<>();
                for (Path artifact : androidArtifacts) {
                    androidSources.add(artifact.toString());
                }
                classes.removeIf(classFile -> classFile.getClassName().startsWith("artifact/")
                        && androidSources.contains(classFile.getSource()));
                DecompilerEngine engine = DecompilerEngineRegistry.get("jadx");
                if (engine instanceof ArtifactDecompilerEngine artifactEngine) {
                    for (Path artifact : androidArtifacts) {
                        classes.addAll(artifactEngine.loadArtifactClasses(artifact,
                                DEFAULT_OPTIONS.withInputPath(artifact.toString())
                                        .withBestEffort(true)
                                        .withFallbackEnabled(true)));
                    }
                }
            }
            return classes;
        }
        return loader.load(path);
    }

    private List<Path> directAndroidArtifacts(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isAndroidArtifact)
                    .sorted()
                    .toList();
        }
    }

    private void clearClassTree() {
        TreeItem<ClassNode> root = new TreeItem<>(new ClassNode("Classes", null, true));
        root.setExpanded(true);
        classTree.getSelectionModel().clearSelection();
        classTree.setRoot(root);
        classTree.setShowRoot(false);
    }

    private void setProjectControlsDisabled(boolean disabled) {
        if (classTree != null) {
            classTree.setDisable(disabled);
        }
        if (searchField != null) {
            searchField.setDisable(disabled);
        }
    }

    private void applyClassFilter(String query) {
        if (loadedClassFiles == null) {
            return;
        }
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) {
            populateClassTree(loadedClassFiles);
            return;
        }
        List<ClassFile> filtered = loadedClassFiles.stream()
                .filter(cf -> cf.getClassName().replace('/', '.').toLowerCase().contains(normalized)
                        || cf.getSimpleName().toLowerCase().contains(normalized))
                .toList();
        populateClassTree(filtered);
    }

    private void populateClassTree(List<ClassFile> classFiles) {
        TreeItem<ClassNode> root = new TreeItem<>(new ClassNode("Classes", null, true));
        root.setExpanded(true);

        Map<String, List<ClassFile>> classesByPackage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ClassFile cf : classFiles) {
            String packageName = packageNameOf(cf.getClassName());
            classesByPackage.computeIfAbsent(packageName, ignored -> new ArrayList<>()).add(cf);
        }

        for (Map.Entry<String, List<ClassFile>> entry : classesByPackage.entrySet()) {
            TreeItem<ClassNode> packageNode = new TreeItem<>(new ClassNode(packageDisplayName(entry.getKey()), null, true));
            packageNode.setExpanded(false);
            entry.getValue().sort((a, b) -> simpleClassName(a.getClassName()).compareToIgnoreCase(simpleClassName(b.getClassName())));
            for (ClassFile cf : entry.getValue()) {
                packageNode.getChildren().add(new TreeItem<>(new ClassNode(simpleClassName(cf.getClassName()), cf, false)));
            }
            root.getChildren().add(packageNode);
        }

        classTree.setRoot(root);
        classTree.setShowRoot(false);
    }

    private String packageNameOf(String internalClassName) {
        int idx = internalClassName.lastIndexOf('/');
        if (idx < 0) {
            return "";
        }
        return internalClassName.substring(0, idx).replace('/', '.');
    }

    private String packageDisplayName(String packageName) {
        return packageName.isEmpty() ? "(default package)" : packageName;
    }

    private String simpleClassName(String internalClassName) {
        int idx = internalClassName.lastIndexOf('/');
        return idx >= 0 ? internalClassName.substring(idx + 1) : internalClassName;
    }

    private void selectFirstClass() {
        TreeItem<ClassNode> firstClass = findFirstClass(classTree.getRoot());
        if (firstClass != null) {
            classTree.getSelectionModel().select(firstClass);
        } else {
            editorTitleLabel.setText("No class files found");
            editorMetaLabel.setText(loadedPath == null ? "" : loadedPath.toString());
            selectedClassLabel.setText("No class files");
        }
    }

    private void selectInitialClass() {
        ClassFile entryClass = findEntryClass();
        if (entryClass != null) {
            selectTreeClass(entryClass.getClassName());
        } else {
            selectFirstClass();
        }
    }

    private ClassFile findEntryClass() {
        if (isAndroidArtifactLoaded()) {
            ClassFile preferredAndroidClass = findPreferredAndroidEntryClass();
            if (preferredAndroidClass != null) {
                return preferredAndroidClass;
            }
        }
        String manifestEntryClass = readEntryClassFromManifest();
        if (manifestEntryClass != null) {
            String internalName = manifestEntryClass.replace('.', '/');
            for (ClassFile cf : loadedClassFiles) {
                if (cf.getClassName().equals(internalName)) {
                    return cf;
                }
            }
        }
        ClassFile namedEntryClass = loadedClassFiles.stream()
                .filter(cf -> !isSpringBootLoaderClass(cf.getClassName()))
                .filter(cf -> cf.getClassName().endsWith("/Application") || cf.getClassName().endsWith("/Main"))
                .findFirst()
                .orElse(null);
        if (namedEntryClass != null) {
            return namedEntryClass;
        }
        return loadedClassFiles.stream()
                .filter(cf -> !isSpringBootLoaderClass(cf.getClassName()))
                .findFirst()
                .orElse(null);
    }

    private ClassFile findPreferredAndroidEntryClass() {
        ClassFile namedEntryClass = loadedClassFiles.stream()
                .filter(cf -> !isLikelyAndroidLibraryClass(cf.getClassName()))
                .filter(cf -> cf.getClassName().endsWith("/Application")
                        || cf.getClassName().endsWith("/MainActivity")
                        || cf.getClassName().endsWith("/Main")
                        || cf.getClassName().endsWith("/Activity"))
                .findFirst()
                .orElse(null);
        if (namedEntryClass != null) {
            return namedEntryClass;
        }
        return loadedClassFiles.stream()
                .filter(cf -> !isLikelyAndroidLibraryClass(cf.getClassName()))
                .findFirst()
                .orElse(null);
    }

    private boolean isLikelyAndroidLibraryClass(String internalClassName) {
        if (internalClassName == null) {
            return true;
        }
        String lower = internalClassName.toLowerCase();
        return lower.startsWith("android/")
                || lower.startsWith("androidx/")
                || lower.startsWith("com/google/")
                || lower.startsWith("com/android/")
                || lower.startsWith("kotlin/")
                || lower.startsWith("kotlinx/")
                || lower.startsWith("java/")
                || lower.startsWith("javax/")
                || lower.startsWith("okhttp3/")
                || lower.startsWith("okio/")
                || lower.startsWith("retrofit2/")
                || lower.startsWith("dagger/")
                || lower.startsWith("org/jetbrains/");
    }

    private String readEntryClassFromManifest() {
        if (loadedPath == null || !loadedPath.toString().toLowerCase().endsWith(".jar")) {
            return null;
        }
        try (JarFile jarFile = new JarFile(loadedPath.toFile())) {
            var manifest = jarFile.getManifest();
            if (manifest == null) {
                return null;
            }
            var attributes = manifest.getMainAttributes();
            String startClass = blankToNull(attributes.getValue("Start-Class"));
            if (startClass != null) {
                return startClass;
            }
            String mainClass = blankToNull(attributes.getValue("Main-Class"));
            if (mainClass != null && !isSpringBootLoaderClass(mainClass.replace('.', '/'))) {
                return mainClass;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isSpringBootLoaderClass(String internalClassName) {
        return internalClassName != null && internalClassName.startsWith("org/springframework/boot/loader/");
    }

    private void selectTreeClass(String internalName) {
        TreeItem<ClassNode> match = findClassItem(classTree.getRoot(), internalName);
        if (match != null) {
            TreeItem<ClassNode> parent = match.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
            classTree.getSelectionModel().select(match);
        }
    }

    private TreeItem<ClassNode> findClassItem(TreeItem<ClassNode> root, String internalName) {
        if (root == null) {
            return null;
        }
        ClassFile classFile = root.getValue().getClassFile();
        if (classFile != null && classFile.getClassName().equals(internalName)) {
            return root;
        }
        for (TreeItem<ClassNode> child : root.getChildren()) {
            TreeItem<ClassNode> match = findClassItem(child, internalName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private TreeItem<ClassNode> findFirstClass(TreeItem<ClassNode> root) {
        if (root == null) {
            return null;
        }
        if (!root.getValue().isFolder()) {
            return root;
        }
        for (TreeItem<ClassNode> child : root.getChildren()) {
            TreeItem<ClassNode> match = findFirstClass(child);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private void selectClass(ClassFile classFile) {
        selectedClassFile = classFile;

        String className = classFile.getClassName().replace('/', '.');
        editorTitleLabel.setText(className);
        editorMetaLabel.setText(isAndroidArtifactTarget(classFile) ? "JADX source" : classFile.getBytes().length + " bytes");
        selectedClassLabel.setText(className);
        sourceArea.setText("// Source is pending for " + className + ".");
        setCompareText("// Primary source is pending for " + className + ".",
                "// Comparison source is pending for " + className + ".");
        bytecodeArea.setText(bytecodeText(classFile));
        renderDiagnostics();
        if (!sourceEnginePinned) {
            refreshSourceEngineChoices();
            setDisplayedSourceEngine(recommendedSourceEngine());
        }

        if (editorTabs.getSelectionModel().getSelectedItem() == sourceTab) {
            loadSelectedSource();
        } else {
            editorTabs.getSelectionModel().select(sourceTab);
        }
    }

    private String bytecodeText(ClassFile classFile) {
        if (isAndroidArtifactTarget(classFile) || classFile.getClassName().startsWith("artifact/")) {
            return "// Bytecode view is not available for Android artifacts.\n"
                    + "// Use the Source tab; APK/DEX inputs are handled by JADX.";
        }
        return BytecodePrinter.print(classFile.getBytes());
    }

    private void loadSelectedSource() {
        if (selectedClassFile == null) {
            setStatus("No class selected");
            return;
        }
        String engine = sourceEnginePinned && sourceEngineCombo != null ? sourceEngineCombo.getValue() : ENGINE_AUTO;
        if (engine == null || engine.isBlank()) {
            engine = ENGINE_AUTO;
        }
        loadSource(selectedClassFile, engine, sourceArea, workspaceGeneration);
    }

    private void loadSource(ClassFile target, String engineName, TextArea targetArea, long generation) {
        if (target == null) {
            setStatus("No class selected");
            return;
        }
        String requestedEngine = normalizeEngineName(engineName);
        String cacheEngine = ENGINE_AUTO.equals(requestedEngine) ? "auto" : requestedEngine;
        Map<String, DecompilationResult> cache = engineCache(cacheEngine);
        DecompilationResult cached = cache.get(target.getClassName());
        if (cached != null) {
            renderDecompilationResult(target, targetArea, cached, requestedEngine);
            setBusy(false);
            return;
        }

        targetArea.setText("// Decompiling " + target.getClassName().replace('/', '.')
                + " with " + requestedEngineLabel(requestedEngine) + "...");
        setBusy(true);
        setStatus("Decompiling " + target.getClassName() + "...");

        CompletableFuture.supplyAsync(() -> decompileWithEngine(target, requestedEngine, currentOptions(target), generation))
                .thenAccept(result -> Platform.runLater(() -> {
            if (!isCurrentWorkspace(generation) || target != selectedClassFile) {
                return;
            }
            cache.put(target.getClassName(), result);
            renderDecompilationResult(target, targetArea, result, requestedEngine);
            setBusy(false);
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (isCurrentWorkspace(generation) && target == selectedClassFile) {
                    showError("Decompilation error", ex.getMessage());
                    setStatus("Decompilation error");
                    setBusy(false);
                }
            });
            return null;
        });
    }

    private void renderDecompilationResult(ClassFile target, TextArea targetArea, DecompilationResult result, String requestedEngine) {
        if (result.hasError()) {
            if (targetArea == sourceArea && !ENGINE_AUTO.equals(requestedEngine)) {
                setDisplayedSourceEngine(requestedEngine);
            }
            targetArea.setText("/* Decompilation failed with engine '" + requestedEngine + "':\n"
                    + " * " + result.getError().getMessage() + "\n"
                    + " */");
            addDiagnostic(target.getClassName(), requestedEngine, result.getError().getMessage());
            setStatus("Decompilation failed: " + result.getError().getMessage());
        } else {
            if (targetArea == sourceArea) {
                setDisplayedSourceEngine(result.getEngine());
            }
            targetArea.setText(result.getSourceCode());
            engineCache(result.getEngine()).put(target.getClassName(), result);
            if (ENGINE_AUTO.equals(requestedEngine)) {
                setStatus("Decompiled " + target.getClassName() + " with " + result.getEngine()
                        + " in " + result.getElapsedMillis() + " ms");
            } else {
                String fallbackNote = result.getEngine().equals(requestedEngine) ? "" : " (fallback: " + result.getEngine() + ")";
                setStatus("Decompiled " + target.getClassName() + " with " + requestedEngine + fallbackNote
                        + " in " + result.getElapsedMillis() + " ms");
            }
            if (sourceSearchField != null && !sourceSearchField.getText().isBlank()) {
                findInActiveEditor(false);
            }
        }
    }

    private DecompilerOptions currentOptions() {
        return currentOptions(selectedClassFile);
    }

    private DecompilerOptions currentOptions(ClassFile target) {
        String input = inputPathFor(target);
        return optionsForInput(input);
    }

    private DecompilerOptions optionsForInput(String input) {
        return DEFAULT_OPTIONS.withInputPath(input).withBestEffort(true).withFallbackEnabled(true);
    }

    private String inputPathFor(ClassFile target) {
        if (target != null && target.getSource() != null && isAndroidArtifactName(target.getSource())) {
            return target.getSource();
        }
        return loadedPath == null ? null : loadedPath.toString();
    }

    private boolean isAndroidArtifactLoaded() {
        return loadedPath != null && isAndroidArtifact(loadedPath);
    }

    private boolean isAndroidArtifact(Path path) {
        return isAndroidArtifactName(path.toString());
    }

    private boolean isAndroidArtifactName(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".aab")
                || lower.endsWith(".apks") || lower.endsWith(".apkm") || lower.endsWith(".xapk");
    }

    private boolean isAndroidArtifactTarget(ClassFile classFile) {
        return classFile != null
                && ((classFile.getSource() != null && isAndroidArtifactName(classFile.getSource()))
                || (isAndroidArtifactLoaded() && classFile.getBytes().length == 0));
    }

    private DecompilationResult decompileWithEngine(ClassFile target, String engineName) {
        return decompileWithEngine(target, engineName, currentOptions(target), workspaceGeneration);
    }

    private DecompilationResult decompileWithEngine(ClassFile target, String engineName, DecompilerOptions options) {
        return decompileWithEngine(target, engineName, options, workspaceGeneration);
    }

    private DecompilationResult decompileWithEngine(ClassFile target, String engineName,
                                                    DecompilerOptions options, long generation) {
        if (ENGINE_AUTO.equals(engineName)) {
            if (isAndroidArtifactTarget(target)) {
                DecompilationResult jadx = decompileWithEngine(target, "jadx", options, generation);
                engineCache("auto").put(target.getClassName(), jadx);
                return jadx;
            }
            DecompilationResult cfr = decompileWithEngine(target, "cfr", options, generation);
            if (!cfr.hasError()) {
                engineCache("auto").put(target.getClassName(), cfr);
                return cfr;
            }
            addDiagnosticIfCurrent(generation, target.getClassName(), "cfr", cfr.getError().getMessage());
            if (DecompilerEngineRegistry.availableEngines().contains("vineflower")) {
                DecompilationResult vineflower = decompileWithEngine(target, "vineflower", options, generation);
                if (!vineflower.hasError()) {
                    engineCache("auto").put(target.getClassName(), vineflower);
                    return vineflower;
                }
                addDiagnosticIfCurrent(generation, target.getClassName(), "vineflower", vineflower.getError().getMessage());
            }
            DecompilationResult simple = decompileWithEngine(target, "simple", options, generation);
            engineCache("auto").put(target.getClassName(), simple);
            return simple;
        }

        DecompilationResult cached = engineCache(engineName).get(target.getClassName());
        if (cached != null) {
            return cached;
        }

        DecompilerEngine engine = DecompilerEngineRegistry.get(engineName);
        DecompilationResult result = engine.decompile(target, options);
        engineCache(result.getEngine()).put(target.getClassName(), result);
        return result;
    }

    private void loadCompareSource(ClassFile target) {
        if (target == null) {
            return;
        }
        long generation = workspaceGeneration;
        setCompareText("// Preparing primary source for " + target.getClassName().replace('/', '.') + "...",
                "// Preparing comparison source for " + target.getClassName().replace('/', '.') + "...");
        setBusy(true);
        CompletableFuture.supplyAsync(() -> {
            if (isAndroidArtifactTarget(target)) {
                DecompilationResult jadx = decompileWithEngine(target, "jadx", currentOptions(target), generation);
                return formatCompare(jadx, DecompilationResult.error(target.getClassName(), "bytecode",
                        new IOException("Bytecode compare is not available for Android artifacts yet")));
            }
            DecompilationResult primary = decompileWithEngine(target, "cfr", currentOptions(target), generation);
            DecompilationResult secondary = DecompilerEngineRegistry.availableEngines().contains("vineflower")
                    ? decompileWithEngine(target, "vineflower", currentOptions(target), generation)
                    : decompileWithEngine(target, "simple", currentOptions(target), generation);
            return formatCompare(primary, secondary);
        }).thenAccept(result -> Platform.runLater(() -> {
            if (isCurrentWorkspace(generation) && target == selectedClassFile) {
                setCompareText(result.left(), result.right());
                setStatus("Compare view ready for " + target.getClassName());
                setBusy(false);
            }
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (isCurrentWorkspace(generation) && target == selectedClassFile) {
                    addDiagnostic(target.getClassName(), "compare", ex.getMessage());
                    setCompareText("/* Compare failed: " + ex.getMessage() + " */", "");
                    setBusy(false);
                }
            });
            return null;
        });
    }

    private CompareResult formatCompare(DecompilationResult first, DecompilationResult second) {
        return new CompareResult(
                formatCompareBlock(first.getEngine(), first),
                formatCompareBlock(second.getEngine(), second)
        );
    }

    private String formatCompareBlock(String label, DecompilationResult result) {
        if (result.hasError()) {
            return "// " + label + " failed: " + result.getError().getMessage();
        }
        return "// " + label + " (" + result.getElapsedMillis() + " ms)\n" + result.getSourceCode();
    }

    private void setCompareText(String left, String right) {
        if (compareLeftArea != null) {
            compareLeftArea.setText(left);
        }
        if (compareRightArea != null) {
            compareRightArea.setText(right);
        }
        lastCompareQuery = null;
        lastCompareCursor = 0;
    }

    private Map<String, DecompilationResult> engineCache(String engineName) {
        return decompilationCaches.computeIfAbsent(engineName.toLowerCase(), ignored -> new ConcurrentHashMap<>());
    }

    private String normalizeEngineName(String engineName) {
        if (engineName == null || engineName.isBlank() || ENGINE_AUTO.equalsIgnoreCase(engineName)) {
            return ENGINE_AUTO;
        }
        return engineName.toLowerCase();
    }

    private List<String> availableSourceEngines() {
        return availableSourceEnginesForLoadedPath();
    }

    private List<String> availableSourceEnginesForLoadedPath() {
        List<String> engines = new ArrayList<>();
        for (String engine : List.of("cfr", "vineflower", "simple", "jadx")) {
            if (DecompilerEngineRegistry.availableEngines().contains(engine)
                    && isEngineApplicableToLoadedPath(engine)) {
                engines.add(engine);
            }
        }
        DecompilerEngineRegistry.availableEngines().stream()
                .filter(engine -> !engines.contains(engine))
                .filter(this::isEngineApplicableToLoadedPath)
                .forEach(engines::add);
        return engines;
    }

    private boolean isEngineApplicableToLoadedPath(String engine) {
        if (loadedPath == null) {
            return true;
        }
        if (selectedClassFile != null ? isAndroidArtifactTarget(selectedClassFile) : isAndroidArtifactLoaded()) {
            return "jadx".equals(engine);
        }
        return !"jadx".equals(engine);
    }

    private String defaultSourceEngine() {
        List<String> engines = availableSourceEngines();
        if (engines.contains("cfr")) {
            return "cfr";
        }
        return engines.isEmpty() ? "" : engines.get(0);
    }

    private String recommendedSourceEngine() {
        List<String> engines = availableSourceEngines();
        if (selectedClassFile != null && isAndroidArtifactTarget(selectedClassFile) && engines.contains("jadx")) {
            return "jadx";
        }
        if (selectedClassFile == null && isAndroidArtifactLoaded() && engines.contains("jadx")) {
            return "jadx";
        }
        if (engines.contains("cfr")) {
            return "cfr";
        }
        return engines.isEmpty() ? "" : engines.get(0);
    }

    private void setDisplayedSourceEngine(String engineName) {
        if (sourceEngineCombo == null || engineName == null || engineName.isBlank()) {
            return;
        }
        if (!sourceEngineCombo.getItems().contains(engineName)) {
            return;
        }
        updatingSourceEngineCombo = true;
        try {
            sourceEngineCombo.setValue(engineName);
        } finally {
            updatingSourceEngineCombo = false;
        }
    }

    private void refreshSourceEngineChoices() {
        if (sourceEngineCombo == null) {
            return;
        }
        String current = sourceEngineCombo.getValue();
        List<String> engines = availableSourceEngines();
        updatingSourceEngineCombo = true;
        try {
            sourceEngineCombo.getItems().setAll(engines);
            if (current != null && engines.contains(current)) {
                sourceEngineCombo.setValue(current);
            } else if (!engines.isEmpty()) {
                sourceEngineCombo.setValue(recommendedSourceEngine());
            }
        } finally {
            updatingSourceEngineCombo = false;
        }
    }

    private String requestedEngineLabel(String requestedEngine) {
        return ENGINE_AUTO.equals(requestedEngine) ? "best available engine" : requestedEngine;
    }

    private void addDiagnostic(String className, String engine, String message) {
        Runnable update = () -> {
            diagnostics.add("[" + engine + "] " + className + ": " + message);
            renderDiagnostics();
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void addDiagnosticIfCurrent(long generation, String className, String engine, String message) {
        Runnable update = () -> {
            if (isCurrentWorkspace(generation)) {
                diagnostics.add("[" + engine + "] " + className + ": " + message);
                renderDiagnostics();
            }
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void renderDiagnostics() {
        if (diagnosticsArea == null) {
            return;
        }
        if (diagnostics.isEmpty()) {
            diagnosticsArea.setText(EMPTY_DIAGNOSTICS);
        } else {
            diagnosticsArea.setText(String.join("\n", diagnostics));
        }
    }

    private void exportSources() {
        if (loadedClassFiles == null || loadedClassFiles.isEmpty()) {
            setStatus("No classes to export");
            return;
        }
        ExportRequest request = showExportDialog();
        if (request == null) {
            return;
        }
        Path outputDir = request.outputDir();
        String exportEngine = request.engineName();
        long generation = workspaceGeneration;
        Path exportLoadedPath = loadedPath;
        List<ClassFile> classSnapshot = List.copyOf(loadedClassFiles);
        DecompilerOptions options = optionsForInput(exportLoadedPath == null ? null : exportLoadedPath.toString());
        setBusy(true);
        exportButton.setDisable(true);
        setStatus("Exporting sources to " + outputDir + " with " + exportEngine + "...");
        CompletableFuture.runAsync(() -> {
            try {
                if (exportLoadedPath != null && isAndroidArtifact(exportLoadedPath)
                        && (ENGINE_AUTO.equals(exportEngine) || "jadx".equals(exportEngine))) {
                    DecompilerEngine engine = DecompilerEngineRegistry.get("jadx");
                    if (engine instanceof ArtifactDecompilerEngine artifactEngine) {
                        int written = artifactEngine.exportArtifact(exportLoadedPath, outputDir, options);
                        Platform.runLater(() -> {
                            if (!isCurrentWorkspace(generation)) {
                                return;
                            }
                            setBusy(false);
                            exportButton.setDisable(classSnapshot.isEmpty());
                            setStatus("Exported " + written + " JADX source file(s) to " + outputDir);
                        });
                        return;
                    }
                }
                List<ClassFile> sortedClasses = classSnapshot.stream()
                        .sorted(Comparator.comparing(ClassFile::getClassName))
                        .toList();

                ExportStats stats = ENGINE_AUTO.equals(exportEngine)
                        ? exportAutoSources(outputDir, sortedClasses, options, generation)
                        : exportEngineSources(outputDir, sortedClasses, options, exportEngine, generation);
                Platform.runLater(() -> {
                    if (!isCurrentWorkspace(generation)) {
                        return;
                    }
                    setBusy(false);
                    exportButton.setDisable(classSnapshot.isEmpty());
                    setStatus("Exported " + stats.written() + " source file(s) to " + outputDir
                            + " using " + exportEngine + " (" + stats.enhanced() + " enhanced)");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!isCurrentWorkspace(generation)) {
                        return;
                    }
                    setBusy(false);
                    exportButton.setDisable(classSnapshot.isEmpty());
                    showError("Export error", e.getMessage());
                    setStatus("Export failed: " + e.getMessage());
                });
            }
        });
    }

    private ExportRequest showExportDialog() {
        Dialog<ExportRequest> dialog = new Dialog<>();
        dialog.setTitle("Export Sources");
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStyleClass().add("export-dialog");
        URL stylesheet = getClass().getResource(STYLESHEET);
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField pathField = new TextField();
        pathField.getStyleClass().add("search-field");
        pathField.setPromptText("Export directory");
        pathField.setPrefWidth(420);

        Button browseButton = new Button("Browse");
        browseButton.getStyleClass().add("command-button");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Export decompiled sources");
            File dir = chooser.showDialog(null);
            if (dir != null) {
                pathField.setText(dir.getAbsolutePath());
            }
        });

        ComboBox<String> engineCombo = new ComboBox<>();
        engineCombo.getStyleClass().add("engine-combo");
        engineCombo.getItems().addAll(availableSourceEngines());
        engineCombo.setValue(defaultExportEngine());
        engineCombo.setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label("Export Sources");
        titleLabel.getStyleClass().add("export-dialog-title");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("export-dialog-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        Label pathLabel = new Label("Path");
        pathLabel.getStyleClass().add("export-dialog-label");
        Label engineLabel = new Label("Engine");
        engineLabel.getStyleClass().add("export-dialog-label");
        grid.add(pathLabel, 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(browseButton, 2, 0);
        grid.add(engineLabel, 0, 1);
        grid.add(engineCombo, 1, 1);
        GridPane.setHgrow(pathField, Priority.ALWAYS);
        GridPane.setHgrow(engineCombo, Priority.ALWAYS);
        VBox content = new VBox(14, titleLabel, grid);
        content.getStyleClass().add("export-dialog-content");
        dialog.getDialogPane().setContent(content);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().addAll("command-button", "primary-command");
        okButton.setDisable(true);
        Node cancelButton = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().add("command-button");
        pathField.textProperty().addListener((obs, oldValue, newValue) ->
                okButton.setDisable(newValue == null || newValue.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            return new ExportRequest(Path.of(pathField.getText().trim()), normalizeEngineName(engineCombo.getValue()));
        });
        return dialog.showAndWait().orElse(null);
    }

    private String defaultExportEngine() {
        if (sourceEngineCombo != null && sourceEngineCombo.getValue() != null
                && !sourceEngineCombo.getValue().isBlank()) {
            return sourceEngineCombo.getValue();
        }
        return recommendedSourceEngine();
    }

    private ExportStats exportAutoSources(Path outputDir, List<ClassFile> classes,
                                          DecompilerOptions options, long generation) throws IOException {
        int simpleWritten = 0;
        for (ClassFile classFile : classes) {
            DecompilationResult result = DecompilerEngineRegistry.get("simple").decompile(classFile, options);
            if (!result.hasError()) {
                engineCache("simple").put(classFile.getClassName(), result);
                writeSourceFile(outputDir, classFile, result);
                simpleWritten++;
            }
            if (simpleWritten == classes.size() || simpleWritten % 25 == 0) {
                int exported = simpleWritten;
                setStatusIfCurrent(generation, "Exported " + exported + "/" + classes.size()
                        + " basic source file(s). Enhancing with Auto...");
            }
        }

        int enhanced = 0;
        int processed = 0;
        for (ClassFile classFile : classes) {
            processed++;
            DecompilationResult result = decompileWithEngine(classFile, ENGINE_AUTO, options, generation);
            if (!result.hasError()) {
                writeSourceFile(outputDir, classFile, result);
                enhanced++;
            }
            if (processed == classes.size() || processed % 10 == 0) {
                int current = processed;
                int enhancedCount = enhanced;
                setStatusIfCurrent(generation, "Auto enhanced " + enhancedCount + "/" + classes.size()
                        + " source file(s), processed " + current + "/" + classes.size());
            }
        }
        return new ExportStats(simpleWritten, enhanced);
    }

    private ExportStats exportEngineSources(Path outputDir, List<ClassFile> classes,
                                            DecompilerOptions options, String engineName,
                                            long generation) throws IOException {
        int written = 0;
        int processed = 0;
        for (ClassFile classFile : classes) {
            processed++;
            DecompilationResult result = decompileWithEngine(classFile, engineName, options, generation);
            if (!result.hasError()) {
                writeSourceFile(outputDir, classFile, result);
                written++;
            } else if (!"simple".equals(engineName)) {
                addDiagnosticIfCurrent(generation, classFile.getClassName(), engineName, result.getError().getMessage());
                DecompilationResult fallback = DecompilerEngineRegistry.get("simple").decompile(classFile, options);
                if (!fallback.hasError()) {
                    engineCache("simple").put(classFile.getClassName(), fallback);
                    writeSourceFile(outputDir, classFile, fallback);
                    written++;
                }
            }
            if (processed == classes.size() || processed % 10 == 0) {
                int current = processed;
                int exported = written;
                setStatusIfCurrent(generation, "Exported " + exported + "/" + classes.size()
                        + " source file(s), processed " + current + "/" + classes.size());
            }
        }
        return new ExportStats(written, written);
    }

    private void writeSourceFile(Path outputDir, ClassFile classFile, DecompilationResult result) throws IOException {
        Path file = outputDir.resolve(classFile.getClassName() + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, result.getSourceCode(), StandardCharsets.UTF_8);
    }

    private record ExportRequest(Path outputDir, String engineName) {
    }

    private record ExportStats(int written, int enhanced) {
    }

    private record CompareResult(String left, String right) {
    }

    private record SearchMatch(TextArea area, String paneName, int start, int end, int globalStart, int ordinal) {
    }

    private void findInActiveEditor(boolean backwards) {
        if (sourceSearchField == null || editorTabs == null) {
            return;
        }
        String query = sourceSearchField.getText();
        if (query == null || query.isBlank()) {
            setStatus("Enter text to search in the active class view");
            return;
        }

        if (editorTabs.getSelectionModel().getSelectedItem() == compareTab) {
            findInCompareEditors(query, backwards);
            return;
        }

        TextArea textArea = getActiveEditorTextArea();
        if (textArea == null) {
            setStatus("No active editor to search");
            return;
        }

        String text = textArea.getText();
        String haystack = text.toLowerCase();
        String needle = query.toLowerCase();
        if (needle.isEmpty()) {
            return;
        }

        int index;
        if (backwards) {
            int start = Math.max(0, textArea.getSelection().getStart() - 1);
            index = haystack.lastIndexOf(needle, start);
            if (index < 0) {
                index = haystack.lastIndexOf(needle);
            }
        } else {
            int start = Math.max(0, textArea.getSelection().getEnd());
            index = haystack.indexOf(needle, start);
            if (index < 0) {
                index = haystack.indexOf(needle);
            }
        }

        if (index < 0) {
            setStatus("No matches for '" + query + "'");
            return;
        }

        textArea.selectRange(index, index + query.length());
        int total = countOccurrences(haystack, needle);
        int current = countOccurrences(haystack.substring(0, index), needle) + 1;
        setStatus("Match " + current + "/" + total + " for '" + query + "'");
    }

    private void findInCompareEditors(String query, boolean backwards) {
        String needle = query.toLowerCase();
        List<SearchMatch> matches = compareMatches(needle);
        if (matches.isEmpty()) {
            setStatus("No matches for '" + query + "' in Compare");
            return;
        }

        int cursor = compareSearchCursor(query, backwards);
        SearchMatch selected = null;
        if (backwards) {
            for (int i = matches.size() - 1; i >= 0; i--) {
                SearchMatch match = matches.get(i);
                if (match.globalStart() < cursor) {
                    selected = match;
                    break;
                }
            }
            if (selected == null) {
                selected = matches.get(matches.size() - 1);
            }
        } else {
            for (SearchMatch match : matches) {
                if (match.globalStart() >= cursor) {
                    selected = match;
                    break;
                }
            }
            if (selected == null) {
                selected = matches.get(0);
            }
        }

        selected.area().requestFocus();
        selected.area().selectRange(selected.start(), selected.end());
        lastCompareQuery = query;
        lastCompareCursor = backwards ? selected.globalStart() : selected.globalStart() + needle.length();
        setStatus("Compare " + selected.paneName() + " match " + selected.ordinal() + "/" + matches.size()
                + " for '" + query + "'");
    }

    private List<SearchMatch> compareMatches(String needle) {
        List<SearchMatch> matches = new ArrayList<>();
        addCompareMatches(matches, compareLeftArea, "left", needle, 0);
        int rightOffset = compareLeftArea == null ? 1 : compareLeftArea.getText().length() + 1;
        addCompareMatches(matches, compareRightArea, "right", needle, rightOffset);
        for (int i = 0; i < matches.size(); i++) {
            SearchMatch match = matches.get(i);
            matches.set(i, new SearchMatch(match.area(), match.paneName(), match.start(), match.end(),
                    match.globalStart(), i + 1));
        }
        return matches;
    }

    private void addCompareMatches(List<SearchMatch> matches, TextArea area, String paneName, String needle, int offset) {
        if (area == null || needle.isEmpty()) {
            return;
        }
        String haystack = area.getText().toLowerCase();
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            matches.add(new SearchMatch(area, paneName, index, index + needle.length(), offset + index, 0));
            index += needle.length();
        }
    }

    private int compareSearchCursor(String query, boolean backwards) {
        if (query.equals(lastCompareQuery)) {
            return lastCompareCursor;
        }
        TextArea area = compareLeftArea != null && compareLeftArea.isFocused() ? compareLeftArea : null;
        if (area == null && compareRightArea != null && compareRightArea.isFocused()) {
            area = compareRightArea;
        }
        int offset = area == compareRightArea && compareLeftArea != null ? compareLeftArea.getText().length() + 1 : 0;
        if (area == null) {
            return backwards ? Integer.MAX_VALUE : 0;
        }
        if (area.getSelection().getLength() > 0) {
            return offset + (backwards ? area.getSelection().getStart() : area.getSelection().getEnd());
        }
        return offset + area.getCaretPosition();
    }

    private TextArea getActiveEditorTextArea() {
        Tab selectedTab = editorTabs.getSelectionModel().getSelectedItem();
        if (selectedTab != null && selectedTab.getContent() instanceof TextArea textArea) {
            return textArea;
        }
        return null;
    }

    private int countOccurrences(String text, String query) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(query, index)) >= 0) {
            count++;
            index += query.length();
        }
        return count;
    }

    private void setBusy(boolean busy) {
        Runnable update = () -> {
            if (progressIndicator != null) {
                progressIndicator.setVisible(busy);
                progressIndicator.setManaged(busy);
            }
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void setProjectLoading(boolean loading) {
        Runnable update = () -> {
            if (projectProgressIndicator != null) {
                projectProgressIndicator.setVisible(loading);
                projectProgressIndicator.setManaged(loading);
            }
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void setStatus(String message) {
        Runnable update = () -> statusLabel.setText(message);
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void setStatusIfCurrent(long generation, String message) {
        Runnable update = () -> {
            if (isCurrentWorkspace(generation)) {
                statusLabel.setText(message);
            }
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About " + APP_NAME);
        alert.setHeaderText(APP_NAME);
        alert.setContentText("Version: " + APP_VERSION + "\n\n"
                + "Java and Android bytecode explorer for .class, .jar, .apk and .dex artifacts.\n\n"
                + "Engines: CFR, Vineflower, JADX and Simple.\n"
                + "Built with JavaFX and ASM.");
        alert.showAndWait();
    }

    private static String appMetadata(String key, String fallback) {
        Properties properties = new Properties();
        try (InputStream input = ByteScryGui.class.getResourceAsStream(ABOUT_PROPERTIES)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
        }
        String value = properties.getProperty(key);
        if (value == null || value.isBlank() || value.contains("${")) {
            return fallback;
        }
        return value.trim();
    }
}
