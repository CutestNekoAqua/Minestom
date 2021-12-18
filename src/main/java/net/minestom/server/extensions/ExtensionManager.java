package net.minestom.server.extensions;

import com.google.gson.Gson;
import net.minestom.dependencies.DependencyGetter;
import net.minestom.dependencies.ResolvedDependency;
import net.minestom.dependencies.maven.MavenRepository;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.utils.PropertyUtil;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ExtensionManager {

    public final static Logger LOGGER = LoggerFactory.getLogger(ExtensionManager.class);

    private static final boolean LOAD_ON_START = PropertyUtil.getBoolean("minestom.extension.load-on-start", true);
    private static final boolean AUTOSCAN_ENABLED = PropertyUtil.getBoolean("minestom.extension.autoscan", true);
    private static final String AUTOSCAN_TARGETS = System.getProperty("minestom.extension.autoscan.targets", "extension.json");
    private final static String INDEV_CLASSES_FOLDER = System.getProperty("minestom.extension.indevfolder.classes");
    private final static String INDEV_RESOURCES_FOLDER = System.getProperty("minestom.extension.indevfolder.resources");
    private final static Gson GSON = new Gson();
    
    private final ExceptionManager exceptionManager;
    private final GlobalEventHandler globalEventHandler;

    // LinkedHashMaps are HashMaps that preserve order
    private final Map<String, Extension> extensions = new LinkedHashMap<>();
    private final Map<String, Extension> immutableExtensions = Collections.unmodifiableMap(extensions);

    private Path extensionDataRoot = Paths.get("extensions");
    private Path dependenciesFolder = extensionDataRoot.resolve(".libs");

    private enum State {DO_NOT_START, NOT_STARTED, STARTED, PRE_INIT, INIT, POST_INIT}

    private State state = LOAD_ON_START ? State.NOT_STARTED : State.DO_NOT_START;

    public ExtensionManager(ExceptionManager exceptionManager, GlobalEventHandler globalEventHandler) {
        this.exceptionManager = exceptionManager;
        this.globalEventHandler = globalEventHandler;
    }

    public @NotNull Path getExtensionDataRoot() {
        return extensionDataRoot;
    }

    public void setExtensionDataRoot(@NotNull Path dataRoot) {
        Check.stateCondition(state.ordinal() > State.NOT_STARTED.ordinal(), "Cannot set data root after initialization.");
        this.extensionDataRoot = dataRoot;
        this.dependenciesFolder = extensionDataRoot.resolve(".libs");
    }

    @NotNull
    public Collection<Extension> getExtensions() {
        return immutableExtensions.values();
    }

    @Nullable
    public Extension getExtension(@NotNull String name) {
        return extensions.get(name.toLowerCase());
    }

    public boolean hasExtension(@NotNull String name) {
        return extensions.containsKey(name);
    }

    //
    // Init phases
    //

    @ApiStatus.Internal
    public void start() {
        if (state == State.DO_NOT_START) {
            LOGGER.warn("Extension loadOnStartup option is set to false, extensions are therefore neither loaded or initialized.");
            return;
        }
        Check.stateCondition(state != State.NOT_STARTED, "ExtensionManager has already been started");
        loadExtensions();
        state = State.STARTED;
    }

    @ApiStatus.Internal
    public void gotoPreInit() {
        if (state == State.DO_NOT_START) return;
        Check.stateCondition(state != State.STARTED, "Extensions have already done pre initialization");
        extensions.values().forEach(Extension::preInitialize);
        state = State.PRE_INIT;
    }

    @ApiStatus.Internal
    public void gotoInit() {
        if (state == State.DO_NOT_START) return;
        Check.stateCondition(state != State.PRE_INIT, "Extensions have already done initialization");
        extensions.values().forEach(Extension::initialize);
        state = State.INIT;
    }

    //
    // Loading
    //

    @NotNull
    public File getExtensionFolder() {
        return extensionFolder;
    }

    public @NotNull Path getExtensionDataRoot() {
        return extensionDataRoot;
    }

    public void setExtensionDataRoot(@NotNull Path dataRoot) {
        this.extensionDataRoot = dataRoot;
    }

    @NotNull
    public Collection<Extension> getExtensions() {
        return immutableExtensions.values();
    }

    @Nullable
    public Extension getExtension(@NotNull String name) {
        return extensions.get(name.toLowerCase());
    }

    public boolean hasExtension(@NotNull String name) {
        return extensions.containsKey(name);
    }

    //
    // Loading
    //

    /**
     * Loads all extensions in the extension folder into this server.
     * <br><br>
     * <p>
     * Pipeline:
     * <br>
     * Finds all .jar files in the extensions folder.
     * <br>
     * Per each jar:
     * <br>
     * Turns its extension.json into a DiscoveredExtension object.
     * <br>
     * Verifies that all properties of extension.json are correctly set.
     * <br><br>
     * <p>
     * It then sorts all those jars by their load order (making sure that an extension's dependencies load before it)
     * <br>
     * Note: Cyclic dependencies will stop both extensions from being loaded.
     * <br><br>
     * <p>
     * Afterwards, it loads all external dependencies and adds them to the extension's files
     * <br><br>
     * <p>
     * Then removes any invalid extensions (Invalid being its Load Status isn't SUCCESS)
     * <br><br>
     * <p>
     * After that, it set its classloaders so each extension is self-contained,
     * <br><br>
     * <p>
     * Removes invalid extensions again,
     * <br><br>
     * <p>
     * and loads all of those extensions into Minestom
     * <br>
     * (Extension fields are set via reflection after each extension is verified, then loaded.)
     * <br><br>
     * <p>
     * If the extension successfully loads, add it to the global extension Map (Name to Extension)
     * <br><br>
     * <p>
     * And finally make a scheduler to clean observers per extension.
     */
    private void loadExtensions() {
        // Initialize folders
        try {
            // Make extensions folder if necessary
            if (!Files.exists(extensionDataRoot)) {
                Files.createDirectories(extensionDataRoot);
            }

            // Make dependencies folder if necessary
            if (!Files.exists(dependenciesFolder)) {
                Files.createDirectories(dependenciesFolder);
            }
        } catch (IOException e) {
            LOGGER.error("Could not find nor create an extension folder, extensions will not be loaded!");
            exceptionManager.handleException(e);
            return;
        }

        // Load extensions
        {
            // Get all extensions and order them accordingly.
            List<DiscoveredExtension> discoveredExtensions = discoverExtensions();

            // Don't waste resources on doing extra actions if there is nothing to do.
            if (discoveredExtensions.isEmpty()) return;

            // Create classloaders for each extension (so that they can be used during dependency resolution)
            Iterator<DiscoveredExtension> extensionIterator = discoveredExtensions.iterator();
            while (extensionIterator.hasNext()) {
                DiscoveredExtension discoveredExtension = extensionIterator.next();
                try {
                    discoveredExtension.createClassLoader();
                } catch (Exception e) {
                    discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.FAILED_TO_SETUP_CLASSLOADER;
                    exceptionManager.handleException(e);
                    LOGGER.error("Failed to load extension {}", discoveredExtension.name());
                    LOGGER.error("Failed to load extension", e);
                    extensionIterator.remove();
                }
            }

            discoveredExtensions = generateLoadOrder(discoveredExtensions);
            loadDependencies(discoveredExtensions);

            // remove invalid extensions
            discoveredExtensions.removeIf(ext -> ext.loadStatus != DiscoveredExtension.LoadStatus.LOAD_SUCCESS);

            // Load the extensions
            for (DiscoveredExtension discoveredExtension : discoveredExtensions) {
                try {
                    loadExtension(discoveredExtension);
                } catch (Exception e) {
                    discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.LOAD_FAILED;
                    LOGGER.error("Failed to load extension {}", discoveredExtension.name());
                    exceptionManager.handleException(e);
                }
            }
        }
    }

    /**
     * Loads an extension into Minestom.
     *
     * @param discoveredExtension The extension. Make sure to verify its integrity, set its class loader, and its files.
     * @return An extension object made from this DiscoveredExtension
     */
    @Nullable
    private Extension loadExtension(@NotNull DiscoveredExtension discoveredExtension) {
        // Create Extension (authors, version etc.)
        String extensionName = discoveredExtension.name();
        String mainClass = discoveredExtension.entrypoint();

        ExtensionClassLoader loader = discoveredExtension.classLoader();

        if (extensions.containsKey(extensionName.toLowerCase())) {
            LOGGER.error("An extension called '{}' has already been registered.", extensionName);
            return null;
        }

        Class<?> jarClass;
        try {
            jarClass = Class.forName(mainClass, true, loader);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not find main class '{}' in extension '{}'.",
                    mainClass, extensionName, e);
            return null;
        }

        Class<? extends Extension> extensionClass;
        try {
            extensionClass = jarClass.asSubclass(Extension.class);
        } catch (ClassCastException e) {
            LOGGER.error("Main class '{}' in '{}' does not extend the 'Extension' superclass.", mainClass, extensionName, e);
            return null;
        }

        Constructor<? extends Extension> constructor;
        try {
            constructor = extensionClass.getDeclaredConstructor();
            // Let's just make it accessible, plugin creators don't have to make this public.
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Main class '{}' in '{}' does not define a no-args constructor.", mainClass, extensionName, e);
            return null;
        }
        Extension extension = null;
        try {
            extension = constructor.newInstance();
        } catch (InstantiationException e) {
            LOGGER.error("Main class '{}' in '{}' cannot be an abstract class.", mainClass, extensionName, e);
            return null;
        } catch (IllegalAccessException ignored) {
            // We made it accessible, should not occur
        } catch (InvocationTargetException e) {
            LOGGER.error(
                    "While instantiating the main class '{}' in '{}' an exception was thrown.",
                    mainClass,
                    extensionName,
                    e.getTargetException()
            );
            return null;
        }

        // Set extension origin to its DiscoveredExtension
        try {
            Field originField = Extension.class.getDeclaredField("origin");
            originField.setAccessible(true);
            originField.set(extension, discoveredExtension);
        } catch (IllegalAccessException e) {
            // We made it accessible, should not occur
        } catch (NoSuchFieldException e) {
            LOGGER.error("Main class '{}' in '{}' has no description field.", mainClass, extensionName, e);
            return null;
        }

        // Set logger
        try {
            Field loggerField = Extension.class.getDeclaredField("logger");
            loggerField.setAccessible(true);
            loggerField.set(extension, LoggerFactory.getLogger(extensionClass));
        } catch (IllegalAccessException e) {
            // We made it accessible, should not occur
            exceptionManager.handleException(e);
        } catch (NoSuchFieldException e) {
            // This should also not occur (unless someone changed the logger in Extension superclass).
            LOGGER.error("Main class '{}' in '{}' has no logger field.", mainClass, extensionName, e);
        }

        // Set event node
        try {
            EventNode<Event> eventNode = EventNode.all(extensionName); // Use the extension name
            Field loggerField = Extension.class.getDeclaredField("eventNode");
            loggerField.setAccessible(true);
            loggerField.set(extension, eventNode);

            globalEventHandler.addChild(eventNode);
        } catch (IllegalAccessException e) {
            // We made it accessible, should not occur
            exceptionManager.handleException(e);
        } catch (NoSuchFieldException e) {
            // This should also not occur
            LOGGER.error("Main class '{}' in '{}' has no event node field.", mainClass, extensionName, e);
        }

        // add dependents to pre-existing extensions, so that they can easily be found during reloading
        for (String dependencyName : discoveredExtension.dependencies()) {
            Extension dependency = extensions.get(dependencyName.toLowerCase());
            if (dependency == null) {
                LOGGER.warn("Dependency {} of {} is null? This means the extension has been loaded without its dependency, which could cause issues later.", dependencyName, discoveredExtension.name());
            } else {
                dependency.getDependents().add(discoveredExtension.name());
            }
        }

        // add to a linked hash map, as they preserve order
        extensions.put(extensionName.toLowerCase(), extension);

        return extension;
    }


    /**
     * Get all extensions from the extensions folder and make them discovered.
     * <p>
     * It skims the extension folder, discovers and verifies each extension, and returns those created DiscoveredExtensions.
     *
     * @return A list of discovered extensions from this folder.
     */
    private @NotNull List<DiscoveredExtension> discoverExtensions() {
        List<DiscoveredExtension> extensions = new ArrayList<>();

        // Attempt to find all the extensions
        try {
            Files.list(extensionDataRoot)
                    .filter(file -> file != null &&
                            !Files.isDirectory(file) &&
                            file.getFileName().toString().endsWith(".jar"))
                    .map(this::discoverFromJar)
                    .filter(ext -> ext != null && ext.loadStatus == DiscoveredExtension.LoadStatus.LOAD_SUCCESS)
                    .forEach(extensions::add);
        } catch (IOException e) {
            exceptionManager.handleException(e);
        }

        // Dynamic load (autoscan and indev extension)
        // this allows developers to have their extension discovered while working on it, without having to build a jar and put in the extension folder

        if (AUTOSCAN_ENABLED) {
            for (String target : AUTOSCAN_TARGETS.split(",")) {
                URL extensionJsonUrl = MinecraftServer.class.getClassLoader().getResource(target);
                if (extensionJsonUrl != null) try {
                    LOGGER.info("Autoscan found {}. Adding to list of discovered extensions.", target);
                    DiscoveredExtension extension = discoverDynamic(Paths.get(extensionJsonUrl.toURI()));

                    if (extension != null && extension.loadStatus == DiscoveredExtension.LoadStatus.LOAD_SUCCESS) {
                        extensions.add(extension);
                    }
                } catch (URISyntaxException e) {
                    exceptionManager.handleException(e);
                }
            }
        } else {
            LOGGER.trace("Autoscan disabled");
        }

        if (INDEV_CLASSES_FOLDER != null && INDEV_RESOURCES_FOLDER == null) {
            LOGGER.warn("Found indev classes folder, but not indev resources folder. This is likely a mistake.");
        } else if (INDEV_CLASSES_FOLDER == null && INDEV_RESOURCES_FOLDER != null) {
            LOGGER.warn("Found indev resources folder, but not indev classes folder. This is likely a mistake.");
        } else if (INDEV_CLASSES_FOLDER != null) try {
            LOGGER.info("Found indev folders for extension. Adding to list of discovered extensions.");
            DiscoveredExtension extension = discoverDynamic(Paths.get(INDEV_RESOURCES_FOLDER, "extension.json"),
                    new URL("file://" + INDEV_CLASSES_FOLDER),
                    new URL("file://" + INDEV_RESOURCES_FOLDER));

            if (extension != null && extension.loadStatus == DiscoveredExtension.LoadStatus.LOAD_SUCCESS) {
                extensions.add(extension);
            }
        } catch (MalformedURLException e) {
            exceptionManager.handleException(e);
        }
        return extensions;
    }

    /**
     * Grabs a discovered extension from a jar.
     *
     * @param file The jar to grab it from (a .jar is a formatted .zip file)
     * @return The created DiscoveredExtension.
     */
    private @Nullable DiscoveredExtension discoverFromJar(@NotNull Path file) {
        try (ZipFile f = new ZipFile(file.toFile())) {

            ZipEntry entry = f.getEntry("extension.json");

            if (entry == null)
                throw new IllegalStateException("Missing extension.json in extension " + file.getFileName() + ".");

            InputStreamReader reader = new InputStreamReader(f.getInputStream(entry));

            // Initialize DiscoveredExtension from GSON.
            DiscoveredExtension extension = GSON.fromJson(reader, DiscoveredExtension.class);
            extension.originFile = file;
            extension.files.add(file.toUri().toURL());
            extension.dataDirectory = getExtensionDataRoot().resolve(extension.name());

            // Verify integrity and ensure defaults
            DiscoveredExtension.verifyIntegrity(extension);

            return extension;
        } catch (IOException e) {
            exceptionManager.handleException(e);
            return null;
        }
    }

    private @Nullable DiscoveredExtension discoverDynamic(@NotNull Path extensionJson, @NotNull URL... files) {
        try (BufferedReader reader = Files.newBufferedReader(extensionJson)) {
            DiscoveredExtension extension = GSON.fromJson(reader, DiscoveredExtension.class);
            // No files, since the extension.json is in the root classloader.
            extension.dataDirectory = getExtensionDataRoot().resolve(extension.name());

            // Verify integrity and ensure defaults
            DiscoveredExtension.verifyIntegrity(extension);

            return extension;
        } catch (IOException e) {
            exceptionManager.handleException(e);
            return null;
        }
    }

    @NotNull
    private List<DiscoveredExtension> generateLoadOrder(@NotNull List<DiscoveredExtension> discoveredExtensions) {
        // Extension --> Extensions it depends on.
        Map<DiscoveredExtension, List<DiscoveredExtension>> dependencyMap = new HashMap<>();

        // Put dependencies in dependency map
        {
            Map<String, DiscoveredExtension> extensionMap = new HashMap<>();

            // go through all the discovered extensions and assign their name in a map.
            for (DiscoveredExtension discoveredExtension : discoveredExtensions) {
                extensionMap.put(discoveredExtension.name().toLowerCase(), discoveredExtension);
            }

            allExtensions:
            // go through all the discovered extensions and get their dependencies as extensions
            for (DiscoveredExtension discoveredExtension : discoveredExtensions) {

                List<DiscoveredExtension> dependencies = new ArrayList<>(discoveredExtension.dependencies().size());

                // Map the dependencies into DiscoveredExtensions.
                for (String dependencyName : discoveredExtension.dependencies()) {

                    DiscoveredExtension dependencyExtension = extensionMap.get(dependencyName.toLowerCase());
                    // Specifies an extension we don't have.
                    if (dependencyExtension == null) {

                        // attempt to see if it is not already loaded (happens with dynamic (re)loading)
                        if (extensions.containsKey(dependencyName.toLowerCase())) {

                            dependencies.add(extensions.get(dependencyName.toLowerCase()).origin());
                            continue; // Go to the next loop in this dependency loop, this iteration is done.

                        } else {

                            // dependency isn't loaded, move on.
                            LOGGER.error("Extension {} requires an extension called {}.", discoveredExtension.name(), dependencyName);
                            LOGGER.error("However the extension {} could not be found.", dependencyName);
                            LOGGER.error("Therefore {} will not be loaded.", discoveredExtension.name());
                            discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.MISSING_DEPENDENCIES;
                            continue allExtensions; // the above labeled loop will go to the next extension as this dependency is invalid.

                        }
                    }
                    // This will add null for an unknown-extension
                    dependencies.add(dependencyExtension);

                }

                dependencyMap.put(
                        discoveredExtension,
                        dependencies
                );

            }
        }

        // List containing the load order.
        List<DiscoveredExtension> sortedList = new ArrayList<>();

        // TODO actually have to read this
        {
            // entries with empty lists
            List<Map.Entry<DiscoveredExtension, List<DiscoveredExtension>>> loadableExtensions;

            // While there are entries with no more elements (no more dependencies)
            while (!(
                    loadableExtensions = dependencyMap.entrySet().stream().filter(entry -> isLoaded(entry.getValue())).toList()
            ).isEmpty()
            ) {
                // Get all "loadable" (not actually being loaded!) extensions and put them in the sorted list.
                for (var entry : loadableExtensions) {
                    // Add to sorted list.
                    sortedList.add(entry.getKey());
                    // Remove to make the next iterations a little quicker (hopefully) and to find cyclic dependencies.
                    dependencyMap.remove(entry.getKey());

                    // Remove this dependency from all the lists (if they include it) to make way for next level of extensions.
                    for (var dependencies : dependencyMap.values()) {
                        dependencies.remove(entry.getKey());
                    }
                }
            }
        }

        // Check if there are cyclic extensions.
        if (!dependencyMap.isEmpty()) {
            LOGGER.error("Minestom found {} cyclic extensions.", dependencyMap.size());
            LOGGER.error("Cyclic extensions depend on each other and can therefore not be loaded.");
            for (var entry : dependencyMap.entrySet()) {
                DiscoveredExtension discoveredExtension = entry.getKey();
                LOGGER.error("{} could not be loaded, as it depends on: {}.",
                        discoveredExtension.name(),
                        entry.getValue().stream()
                                .map(DiscoveredExtension::name)
                                .collect(Collectors.joining(", ")));
            }

        }

        return sortedList;
    }

    /**
     * Checks if this list of extensions are loaded
     *
     * @param extensions The list of extensions to check against.
     * @return If all of these extensions are loaded.
     */
    private boolean isLoaded(@NotNull List<DiscoveredExtension> extensions) {
        return extensions.isEmpty() // Don't waste CPU on checking an empty array
                // Make sure the internal extensions list contains all of these.
                || extensions.stream().allMatch(ext -> this.extensions.containsKey(ext.name().toLowerCase()));
    }

    private void loadDependencies(@NotNull List<DiscoveredExtension> extensions) {
        for (DiscoveredExtension discoveredExtension : extensions) {
            try {
                DependencyGetter getter = new DependencyGetter();
                DiscoveredExtension.ExternalDependencies externalDependencies = discoveredExtension.externalDependencies();
                List<MavenRepository> repoList = new ArrayList<>();
                for (var repository : externalDependencies.repositories()) {
                    Check.stateCondition(repository.name().isEmpty(), "Missing 'name' element in repository object.");
                    Check.stateCondition(repository.url().isEmpty(), "Missing 'url' element in repository object.");

                    repoList.add(new MavenRepository(repository.name(), repository.url()));
                }

                getter.addMavenResolver(repoList);

                for (String artifact : externalDependencies.artifacts()) {
                    var resolved = getter.get(artifact, dependenciesFolder.toFile());
                    addDependencyFile(resolved, discoveredExtension);
                    LOGGER.trace("Dependency of extension {}: {}", discoveredExtension.name(), resolved);
                }

                ExtensionClassLoader extensionClassLoader = discoveredExtension.classLoader();
                for (String dependencyName : discoveredExtension.dependencies()) {
                    var resolved = extensions.stream()
                            .filter(ext -> ext.name().equalsIgnoreCase(dependencyName))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Unknown dependency '" + dependencyName + "' of '" + discoveredExtension.name() + "'"));

                    ExtensionClassLoader dependencyClassLoader = resolved.classLoader();

                    extensionClassLoader.addChild(dependencyClassLoader);
                    LOGGER.trace("Dependency of extension {}: {}", discoveredExtension.name(), resolved);
                }
            } catch (Exception e) {
                discoveredExtension.loadStatus = DiscoveredExtension.LoadStatus.MISSING_DEPENDENCIES;
                LOGGER.error("Failed to load dependencies for extension {}", discoveredExtension.name());
                LOGGER.error("Extension '{}' will not be loaded", discoveredExtension.name());
                LOGGER.error("This is the exception", e);
            }
        }
    }

    private void addDependencyFile(@NotNull ResolvedDependency dependency, @NotNull DiscoveredExtension extension) {
        URL location = dependency.getContentsLocation();
        extension.files.add(location);
        extension.classLoader().addURL(location);
        LOGGER.trace("Added dependency {} to extension {} classpath", location.toExternalForm(), extension.name());

        // recurse to add full dependency tree
        if (!dependency.getSubdependencies().isEmpty()) {
            LOGGER.trace("Dependency {} has subdependencies, adding...", location.toExternalForm());
            for (ResolvedDependency sub : dependency.getSubdependencies()) {
                addDependencyFile(sub, extension);
            }
            LOGGER.trace("Dependency {} has had its subdependencies added.", location.toExternalForm());
        }
    }

    //
    // Shutdown / Unload
    //

    /**
     * Shutdowns all the extensions by unloading them.
     */
    public void shutdown() {// copy names, as the extensions map will be modified via the calls to unload
        Set<String> extensionNames = new HashSet<>(extensions.keySet());
        for (String ext : extensionNames) {
            if (extensions.containsKey(ext)) { // is still loaded? Because extensions can depend on one another, it might have already been unloaded
                unloadExtension(ext);
            }
        }
    }

    private void unloadExtension(@NotNull String extensionName) {
        Extension ext = extensions.get(extensionName.toLowerCase());

        if (ext == null) {
            throw new IllegalArgumentException("Extension " + extensionName + " is not currently loaded.");
        }

        List<String> dependents = new ArrayList<>(ext.getDependents()); // copy dependents list

        for (String dependentID : dependents) {
            Extension dependentExt = extensions.get(dependentID.toLowerCase());
            LOGGER.info("Unloading dependent extension {} (because it depends on {})", dependentID, extensionName);
            unload(dependentExt);
        }

        LOGGER.info("Unloading extension {}", extensionName);
        unload(ext);
    }

    private void unload(@NotNull Extension ext) {
        ext.terminate();

        globalEventHandler.removeChild(ext.eventNode());

        // remove from loaded extensions
        String id = ext.origin().name().toLowerCase();
        extensions.remove(id);

        // cleanup classloader
        // TODO: Is it necessary to remove the CLs since this is only called on shutdown?
    }
}
