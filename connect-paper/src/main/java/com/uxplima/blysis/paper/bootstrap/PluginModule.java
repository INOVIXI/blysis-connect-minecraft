package com.uxplima.blysis.paper.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.blysis.paper.adapter.inbound.Announcements;
import com.uxplima.blysis.paper.adapter.inbound.Redeeming;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.ChestCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.CreditsCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.LanguageSuggestions;
import com.uxplima.blysis.paper.adapter.inbound.command.LinkCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.StoreCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.VerifyCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.VoucherCommand;
import com.uxplima.blysis.paper.adapter.inbound.gui.ChestMenus;
import com.uxplima.blysis.paper.adapter.inbound.gui.Purchasing;
import com.uxplima.blysis.paper.adapter.inbound.gui.StoreMenus;
import com.uxplima.blysis.paper.adapter.inbound.gui.Windows;
import com.uxplima.blysis.paper.adapter.inbound.gui.Words;
import com.uxplima.blysis.paper.adapter.inbound.listener.LanguageListener;
import com.uxplima.blysis.paper.adapter.inbound.listener.PresenceListener;
import com.uxplima.blysis.paper.adapter.inbound.listener.VoucherListener;
import com.uxplima.blysis.paper.adapter.inbound.placeholder.BlysisPlaceholders;
import com.uxplima.blysis.paper.adapter.outbound.OnlinePlayers;
import com.uxplima.blysis.paper.adapter.outbound.PluginHealth;
import com.uxplima.blysis.paper.adapter.outbound.PluginSettings;
import com.uxplima.blysis.paper.adapter.outbound.VoucherItems;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.CommandMessages;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.content.ContentHooks;
import com.uxplima.uxmlib.content.Operands;
import com.uxplima.uxmlib.gui.CatalogueWords;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.input.TextInputInstaller;
import com.uxplima.uxmlib.hook.placeholder.PlaceholderExpansions;
import com.uxplima.uxmlib.hook.placeholder.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.MenuBasics;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuListener;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.language.FilePlayerLanguages;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageFileUpdates;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.LanguageServices;
import com.uxplima.uxmlib.text.language.Languages;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import com.uxplima.uxmlib.text.style.ThemeFiles;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The composition root. Every object in the plugin is built here and handed its collaborators through a
 * constructor. Nothing reaches for a singleton, and nothing calls {@code JavaPlugin.getInstance()}, which
 * is what lets every other class be built in a test with a fake.
 *
 * <p>This is the only class that names {@link JavaPlugin}.
 */
final class PluginModule {

    private static final String MESSAGES = "messages";

    /**
     * The folder beside the plugins that our suite keeps its one theme file in. The library asks for the
     * name rather than choosing it, because which plugins form a suite is our convention and not its
     * business.
     */
    private static final String THEME_FOLDER = "uxmTheme";

    /** How often the choices a player made reach the disk. A leave writes nothing on its own. */
    private static final Duration SAVE_PERIOD = Duration.ofMinutes(5);

    private final JavaPlugin plugin;
    private final PluginSettings settings;

    private @Nullable Styler styler;

    private @Nullable Messages messages;

    // The files that were found the last time they were read. A reload reads them again, so a language file
    // written while the server runs becomes a choice without a restart.
    private volatile Languages languages;

    private @Nullable FilePlayerLanguages store;

    private @Nullable TaskHandle saveTask;

    private @Nullable ConnectionWiring connection;

    private @Nullable MenuListener menuClicks;
    private @Nullable AnvilInput anvil;
    private TextInputInstaller.@Nullable Installed typing;
    private @Nullable Windows windows;
    private @Nullable Runnable unpublish;

    /** The window files and their rows when a file is unreadable. */
    private static final Map<String, Integer> WINDOWS = Map.of(
            StoreMenus.STORE, 6,
            StoreMenus.OPTIONS, 4,
            Purchasing.BASKET, 5,
            Purchasing.RECIPIENTS, 6,
            ChestMenus.CHEST, 6,
            ChestMenus.SENT, 6);

    PluginModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Path dataFolder = plugin.getDataFolder().toPath();
        extractDefaults(dataFolder);
        this.settings = new PluginSettings(dataFolder.resolve("config.conf"));
        this.languages = readLanguages();
    }

    /** Build the graph and register everything that the server must see. */
    void wire() {
        // What a skill plugin says a player's level is, so an announcement line can name one. uxmLib is
        // relocated into this plugin, so a source another plugin of ours installed never reaches here.
        Operands.readingSkills(ContentHooks.skillLevels(plugin.getServer()));
        Scheduler scheduler = new PaperScheduler(plugin);
        reportLanguageProblems();

        FilePlayerLanguages chosen =
                FilePlayerLanguages.loadedFrom(plugin.getDataFolder().toPath().resolve("languages.conf"));
        this.store = chosen;

        // The resolver reads the settings on every call, so a reload changes what a player reads without
        // anything here being built again. uxmLang is asked first when a server runs it, and the lookup is a
        // service and not an import: this plugin works the same on a server that has never heard of it.
        LanguageResolver resolver =
                new LanguageResolver(() -> settings.current().language(), chosen, LanguageServices::registered);

        Messages messages = new Messages(styledCatalog(), resolver);
        this.messages = messages;
        settings.afterReload(this::reload);

        LanguageChoices choices = new LanguageChoices(
                this::foundLocales, resolver, settings.current().language());

        OnlinePlayers online = new OnlinePlayers();
        // A reload enables the plugin with players already on: they joined before this list existed.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.joined(player.getUniqueId(), player.getName());
        }
        // The replies read the session through this holder, which is filled one line below: the connection
        // needs the announcements, and the announcements need the replies.
        java.util.concurrent.atomic.AtomicReference<@Nullable ConnectionWiring> wiring =
                new java.util.concurrent.atomic.AtomicReference<>();
        Replies replies = new Replies(messages, scheduler, () -> {
            ConnectionWiring held = wiring.get();
            return held == null ? java.util.Optional.empty() : held.session();
        });
        Announcements announcements = new Announcements(
                plugin.getServer(),
                scheduler,
                messages,
                styler(),
                () -> settings.current().announcements(),
                replies);
        ConnectionWiring connection = new ConnectionWiring(
                plugin.getServer(),
                scheduler,
                plugin.getSLF4JLogger(),
                plugin.getDataFolder().toPath(),
                plugin.getPluginMeta().getVersion(),
                online,
                () -> settings.current().connect(),
                announcements);
        wiring.set(connection);
        this.connection = connection;
        settings.afterReload(connection::reloaded);

        plugin.getServer().getPluginManager().registerEvents(new LanguageListener(resolver), plugin);
        plugin.getServer()
                .getPluginManager()
                .registerEvents(new PresenceListener(online, connection::statusSoon), plugin);
        // The store answers from memory and owes the disk a write. The write happens here, on a timer and
        // once more at shutdown, so a busy server never touches the disk on the thread that draws a message.
        this.saveTask = scheduler.asyncTimer(SAVE_PERIOD, SAVE_PERIOD, handle -> saveChoices());

        VoucherItems vouchers = new VoucherItems(
                new NamespacedKey(plugin, "voucher"),
                messages,
                () -> materialOr(settings.current().voucherMaterial()));
        Redeeming redeeming = new Redeeming(replies, messages, vouchers);
        plugin.getServer().getPluginManager().registerEvents(new VoucherListener(redeeming), plugin);

        // registered, not register: PlaceholderAPI keeps an expansion until somebody takes it away, and a
        // reload would otherwise leave it answering out of the graph this enable replaced. The presence guard
        // is inside, so a server without PlaceholderAPI gets nothing and no error.
        this.unpublish = PlaceholderExpansions.registered(
                        BlysisPlaceholders.IDENTIFIER,
                        new PlaceholderRegistry().fallback(new BlysisPlaceholders(replies::session, scheduler)),
                        "INOVIXI",
                        plugin.getPluginMeta().getVersion())
                .orElse(null);
        Surfaces surfaces = registerSurfaces(scheduler, messages, replies);
        registerCommands(scheduler, messages, resolver, choices, replies, vouchers, redeeming, surfaces);
        connection.start();
    }

    /**
     * What this plugin says about itself when an operator asks.
     *
     * <p>Asked for on every run rather than built once, so a reload that fixed a file is a doctor run that
     * says so. Every check is read only, which is why the branch runs the whole list on the lane.
     */
    private java.util.List<com.uxplima.uxmlib.health.HealthCheck> healthChecks() {
        ConnectionWiring wired = connection;
        if (wired == null) {
            return java.util.List.of();
        }
        Windows open = windows;
        return java.util.List.of(
                PluginHealth.site(wired::state),
                PluginHealth.journal(wired::unreported),
                PluginHealth.windows(() -> open == null ? 0 : open.specs().size()),
                PluginHealth.placeholders(() -> unpublish != null));
    }

    /**
     * Give back everything that the server does not take back by itself. A reload disables and enables the
     * plugin again, so anything left open here is leaked once per reload.
     *
     * <p>Closed in the reverse order of {@link #wire()}: the timer first, so the last write is this one and
     * not a task that runs while the server is already tearing the plugin down.
     */
    void shutdown() {
        Operands.forgetEverything();
        if (unpublish != null) {
            unpublish.run();
            unpublish = null;
        }
        if (menuClicks != null) {
            menuClicks.uninstall();
            menuClicks = null;
        }
        if (typing != null) {
            typing.uninstall().run();
            typing = null;
        }
        if (anvil != null) {
            anvil.uninstall();
            anvil = null;
        }
        ConnectionWiring wired = connection;
        if (wired != null) {
            wired.stop();
            connection = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveChoices();
        store = null;
    }

    /**
     * Write the language a player chose, when there is one to write.
     *
     * <p>A failed write must not stop a shutdown, so the reason goes to the console and the server carries
     * on. The operator needs to see it, because a lost choice is a player who has to choose again.
     */
    private void saveChoices() {
        FilePlayerLanguages open = store;
        if (open == null || !open.dirty()) {
            return;
        }
        try {
            open.save();
        } catch (RuntimeException unwritable) {
            plugin.getSLF4JLogger().warn("Cannot write the language store.", unwritable);
        }
    }

    /** The voucher's material, or paper when the file names one this server does not know as an item. */
    private static Material materialOr(String name) {
        Material found = Material.matchMaterial(name);
        return found == null || !found.isItem() || found.isAir() ? Material.PAPER : found;
    }

    private void registerCommands(
            Scheduler scheduler,
            Messages messages,
            LocaleSource locales,
            LanguageChoices choices,
            Replies replies,
            VoucherItems vouchers,
            Redeeming redeeming,
            Surfaces surfaces) {
        // commands.conf names every command. @FromConfig carries only a key, and this replacer
        // rewrites it into the @Command the DSL expects. A handler must never also declare
        // @Command: uxmLib keeps a declared annotation over a produced one, so the file would be
        // read and then ignored, which is worse than not reading it at all.
        ConfiguredCommands commands =
                ConfiguredCommands.load(plugin.getDataFolder().toPath().resolve("commands.conf"));
        ParamResolvers resolvers = commandResolvers(messages, locales, commands, choices);

        if (commands.isEnabled(CreditsCommand.KEY, "credits")) {
            AnnotatedCommands.register(plugin, new CreditsCommand(replies, messages), resolvers, scheduler);
        }
        if (commands.isEnabled(StoreCommand.KEY, "store")) {
            AnnotatedCommands.register(plugin, new StoreCommand(surfaces.store()), resolvers, scheduler);
        }
        if (commands.isEnabled(ChestCommand.KEY, "chest")) {
            AnnotatedCommands.register(plugin, new ChestCommand(surfaces.chest()), resolvers, scheduler);
        }
        if (commands.isEnabled(LinkCommand.KEY, "link")) {
            AnnotatedCommands.register(plugin, new LinkCommand(replies, messages), resolvers, scheduler);
        }
        if (commands.isEnabled(VerifyCommand.KEY, "verify")) {
            AnnotatedCommands.register(plugin, new VerifyCommand(replies, messages), resolvers, scheduler);
        }
        if (commands.isEnabled(VoucherCommand.KEY, "voucher")) {
            AnnotatedCommands.register(
                    plugin,
                    new VoucherCommand(replies, messages, vouchers, redeeming, Log.of(plugin.getLogger())),
                    resolvers,
                    scheduler);
        }
        if (commands.isEnabled(AdminCommand.KEY, "blysis")) {
            AnnotatedCommands.register(
                    plugin,
                    new AdminCommand(
                            settings,
                            messages,
                            plugin.getPluginMeta().getVersion(),
                            scheduler,
                            this::healthChecks,
                            choices,
                            Log.of(plugin.getLogger())),
                    resolvers,
                    scheduler);
        }
    }

    /** The windows a command opens. */
    private record Surfaces(StoreMenus store, ChestMenus chest) {}

    /**
     * The windows and the text input. The menu engine draws a Bedrock player a native form on its own, and
     * the text input sends one a form with a single field, so nothing here asks which client a player has.
     */
    private Surfaces registerSurfaces(Scheduler scheduler, Messages messages, Replies replies) {
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(
                        new CatalogueWords(messages, styler(), CatalogueWords.Window.none()),
                        () -> styler().theme(),
                        bindings.placeholders()),
                bindings.conditions());
        BedrockDetector bedrock = BedrockDetector.forServer(plugin.getServer());
        BedrockScreen screen = BedrockScreen.forServer(plugin.getServer());
        Menus engine = Menus.builder(renderer, scheduler, bindings.lists())
                .openRegistries(bindings.actions(), bindings.conditions())
                .bedrock(bedrock, screen)
                .build();
        MenuBasics.register(bindings, engine);
        MenuListener clicks = new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        clicks.install();
        this.menuClicks = clicks;

        Log log = Log.of(plugin.getLogger());
        AnvilInput anvilInput = new AnvilInput(plugin);
        anvilInput.install();
        this.anvil = anvilInput;
        TextInputInstaller.Installed input = TextInputInstaller.install(
                plugin,
                plugin.getDataFolder().toPath(),
                anvilInput,
                new CatalogueWords(messages, styler()),
                scheduler,
                log,
                bedrock,
                screen);
        this.typing = input;

        Windows open = new Windows(engine, messages, plugin.getDataFolder().toPath(), log);
        this.windows = open;
        Words words = new Words(messages, () -> settings.current().icons());
        StoreMenus store = new StoreMenus(bindings, open, replies, messages, words);
        new Purchasing(bindings, open, replies, messages, input.textInput(), words);
        ChestMenus chest = new ChestMenus(
                bindings,
                open,
                replies,
                messages,
                words,
                () -> settings.current().connect().address());
        readWindows();
        for (String unanswered : chest.problems(bindings)) {
            plugin.getSLF4JLogger().warn("A window names '{}', and nothing answers it.", unanswered);
        }
        return new Surfaces(store, chest);
    }

    /** Reads every window file again, so a reload shows what an operator changed. */
    private void readWindows() {
        Windows open = windows;
        if (open == null) {
            return;
        }
        WINDOWS.forEach(open::read);
        if (typing != null) {
            typing.settings().reload();
        }
    }

    /**
     * What every command reads its words, its lines and its readers with. A player's language is the one the
     * messages answer in, the one they chose: the library's own default reads the client, so its refusals
     * and a command word for one language followed the client and not the choice.
     */
    static ParamResolvers commandResolvers(
            Messages messages, LocaleSource locales, ConfiguredCommands commands, LanguageChoices choices) {
        return ParamResolvers.withDefaults()
                .messages(CommandMessages.fromCatalogue(messages))
                .locales(locales)
                .replacer(FromConfig.class, commands.replacer())
                .suggestions(LanguageSuggestions.KEY, new LanguageSuggestions(choices));
    }

    /** The languages this plugin found, which is the set a player may choose from. */
    private Set<Locale> foundLocales() {
        return languages.locales();
    }

    /**
     * The words, read from the folder rather than from a list in this class.
     *
     * <p>A plugin gains a language by gaining a file. Nothing here names {@code en} or {@code tr}, so an
     * operator who writes {@code messages_de.conf} has German and needs no new build.
     */
    private Languages readLanguages() {
        // The jar's own files sit under the operator's, so a line an update added is never shown as its key.
        return Languages.load(
                plugin.getDataFolder().toPath().resolve(MESSAGES),
                settings.current().defaultLocale(),
                getClass().getClassLoader(),
                MESSAGES);
    }

    /**
     * Say once what is wrong with the files, and start anyway.
     *
     * <p>A file that cannot be read and a translation that is missing a key are both survivable: the catalog
     * falls back through the default language to the text each key carries. An operator still has to be told,
     * because a silent fallback looks like a plugin that ignores their file.
     */
    private void reportLanguageProblems() {
        for (String problem : languages.problems()) {
            plugin.getSLF4JLogger().warn(problem);
        }
    }

    /**
     * The palette, read once. The menus need it for the parts of a tile that are not text, and the message
     * catalog needs it for every line, so it is built here and handed to both.
     */
    private Styler styler() {
        if (styler == null) {
            styler = new Styler(loadTheme());
        }
        return styler;
    }

    private Theme loadTheme() {
        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            return ThemeFiles.load(ThemeFiles.shared(dataFolder, THEME_FOLDER), ThemeFiles.own(dataFolder));
        } catch (ConfigurateException | IllegalArgumentException unreadableTheme) {
            // A broken palette must not stop a server. The shipped colours are always correct.
            plugin.getSLF4JLogger().warn("Cannot read theme.conf. The shipped palette is used.", unreadableTheme);
            return Theme.defaults();
        }
    }

    /**
     * The words, painted. The style pass covers the keys the code knows and the paths a file adds.
     *
     * <p>A plugin can build a key at run time: one lore line of several, one cosmetic of a pack, one lobby of
     * a network. A path like that is in no enum, so without the file entries the line an operator wrote
     * themselves would be the one line that reached a player with its tokens unexpanded.
     */
    private MessageCatalog styledCatalog() {
        Map<Locale, Map<String, String>> entries = languages.entries();
        return styler().style(
                        languages.catalog(),
                        List.of(Keys.values()),
                        entries,
                        settings.current().defaultLocale());
    }

    /**
     * Re-read the words and the palette, and hand them to the {@link Messages} that every port already holds.
     *
     * <p>Both go in even though the catalog is usually the only one that changed: a reload that repaints half
     * of what a player sees is worse than one that repaints none. The locale source is not replaced, because
     * it reads the settings on every call and the new snapshot is already in place.
     *
     * <p>The styler takes the new palette rather than being replaced, because every menu holds this one
     * instance and asks it for the theme each time it builds a tile. Replacing it would repaint the chat and
     * leave the menus on the old colours.
     */
    private void reload() {
        if (messages == null) {
            return;
        }
        this.languages = readLanguages();
        reportLanguageProblems();
        styler().reload(loadTheme());
        messages.reload(styledCatalog());
        readWindows();
    }

    /**
     * Give an operator who already has the file the keys this release added.
     *
     * <p>A plugin that only writes its config on the first run hands every later knob to a new server and
     * to no existing one: the operator who has run it longest is the one who never gets it, and they find
     * out by reading a changelog. The merge adds what is absent and changes no value anybody wrote, and it
     * keeps the file as it was beside it before it renders the document again.
     */
    private void mergeShippedConfig(Path dataFolder) {
        Path file = dataFolder.resolve("config.conf");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            if (PluginSettings.bringUpToDate(file, getClass().getClassLoader())) {
                plugin.getSLF4JLogger()
                        .info("config.conf has the keys this release adds. Nothing you wrote changed, and the"
                                + " file as it was is beside it as config.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            // A merge that cannot run must not stop a server: the file the operator has still loads, and a
            // key they are missing is a knob they do not have rather than a server that does not start.
            plugin.getSLF4JLogger().warn("config.conf could not be brought up to date: {}", unreadable.getMessage());
        }
    }

    /** Write the shipped defaults on first run. An existing file is never overwritten. */
    private void extractDefaults(Path dataFolder) {
        saveIfAbsent(dataFolder, "config.conf");
        mergeShippedConfig(dataFolder);
        saveIfAbsent(dataFolder, "commands.conf");
        saveIfAbsent(dataFolder, "text-input.conf");
        for (String window : WINDOWS.keySet()) {
            saveIfAbsent(dataFolder, "menus/" + window + ".conf");
        }
        saveSharedTheme(dataFolder);
        // The language files are discovered in the jar rather than named in a list here. A plugin gains a
        // language by gaining a file, and a list is the one place that gets forgotten when it does.
        reportCorrectedLines(
                LanguageFileUpdates.bringUpToDate(getClass().getClassLoader(), MESSAGES, dataFolder.resolve(MESSAGES)));
    }

    /**
     * Write the shipped palette where every plugin of ours reads it, on first run.
     *
     * <p>It goes beside this plugin's folder rather than inside it, so the second plugin a server installs
     * finds the file the first one wrote and the whole suite reads one look. An existing file is never
     * touched, whoever wrote it.
     */
    private void saveSharedTheme(Path dataFolder) {
        Path shared = ThemeFiles.shared(dataFolder, THEME_FOLDER);
        if (Files.isRegularFile(shared)) {
            return;
        }
        try (InputStream shipped = plugin.getResource("theme.conf")) {
            if (shipped == null) {
                return;
            }
            Path folder = shared.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            Files.copy(shipped, shared);
        } catch (IOException unwritable) {
            plugin.getSLF4JLogger()
                    .warn("Cannot write the theme to {}. The shipped palette is used.", shared, unwritable);
        }
    }

    private void saveIfAbsent(Path dataFolder, String resource) {
        if (Files.isRegularFile(dataFolder.resolve(resource))) {
            return;
        }
        plugin.saveResource(resource, false);
    }

    /**
     * Say which language lines this start corrected. A line nobody changed takes the words this release ships, and
     * an operator who wonders why a line moved finds the answer in the console.
     */
    private void reportCorrectedLines(LanguageFileUpdates.Report report) {
        if (!report.updated().isEmpty()) {
            plugin.getSLF4JLogger()
                    .info(
                            "Corrected {} language lines nobody had changed: {}",
                            report.updated().size(),
                            report.updated());
        }
    }
}
