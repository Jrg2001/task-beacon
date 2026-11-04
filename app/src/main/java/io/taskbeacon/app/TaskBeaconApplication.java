package io.taskbeacon.app;

import io.taskbeacon.api.*;
import io.taskbeacon.codex.*;
import io.taskbeacon.claude.*;
import io.taskbeacon.core.registry.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import io.taskbeacon.core.selection.DefaultPrimaryTaskSelector;
import io.taskbeacon.core.stall.DefaultStallDetector;
import io.taskbeacon.demo.DemoTaskSource;
import io.taskbeacon.ui.DesktopController;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class TaskBeaconApplication extends Application {
    private final Clock clock = Clock.systemUTC();
    private final List<TaskSource> sources = new CopyOnWriteArrayList<>();
    private final Map<String, SourceHealth> health = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private DesktopController desktop;
    private DefaultTaskRegistry registry;
    private Logger log;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        AppPaths paths = AppPaths.resolve(System.getenv(), System.getProperty("os.name"),
                Path.of(System.getProperty("user.home")));
        System.setProperty("taskbeacon.logDir", paths.logDirectory().toString());
        log = LoggerFactory.getLogger("app");
        TaskBeaconConfig config = new ConfigLoader().load(paths);
        registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(), clock,
                Duration.ofMinutes(config.core().recentTaskRetentionMinutes()));
        desktop = new DesktopController(registry, new DefaultPrimaryTaskSelector(), clock,
                Duration.ofSeconds(config.ui().completedDisplaySeconds()), () -> {
                    Map<String, String> view = new LinkedHashMap<>();
                    health.forEach((name, value) -> view.put(name, value.level().name()));
                    return Map.copyOf(view);
                });
        desktop.start(stage);
        configureSources(config, paths);
        startSources();
        startCoreScheduler(config);
        log.info("TaskBeacon started with {} source(s)", sources.size());
    }

    private void configureSources(TaskBeaconConfig config, AppPaths paths) {
        List<String> args = getParameters().getRaw();
        boolean demoOnly = args.contains("--demo");
        if (!demoOnly && config.sources().codex().enabled() && config.sources().codex().enableSessionLogs()) {
            CodexSourceConfig codexConfig = new CodexSourceConfig(paths.codexSessionsDirectory(),
                    512 * 1024, 2 * 1024 * 1024, Duration.ofSeconds(2), Duration.ofHours(8),
                    Duration.ofSeconds(15));
            sources.add(new CodexTaskSource(codexConfig, clock));
        }
        if (!demoOnly && config.sources().claude().enabled() && config.sources().claude().enableSessionLogs()) {
            ClaudeSourceConfig claudeConfig = new ClaudeSourceConfig(paths.claudeProjectsDirectory(),
                    512 * 1024, 2 * 1024 * 1024, Duration.ofSeconds(2), Duration.ofHours(8),
                    Duration.ofMinutes(3));
            sources.add(new ClaudeTaskSource(claudeConfig, clock));
        }
        if (demoOnly || args.contains("--with-demo")) sources.add(new DemoTaskSource(clock));
    }

    private void startSources() {
        for (TaskSource source : sources) {
            try {
                source.start(registry::accept);
                health.put(source.descriptor().displayName(), source.health());
            } catch (Exception error) {
                health.put(source.descriptor().displayName(), new SourceHealth(HealthLevel.ERROR,
                        "Source failed to start", clock.instant(), Map.of()));
                log.warn("Source {} failed to start", source.descriptor().id(), error);
            }
        }
    }

    private void startCoreScheduler(TaskBeaconConfig config) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "taskbeacon-core-scheduler"); thread.setDaemon(true); return thread;
        });
        DefaultStallDetector detector = new DefaultStallDetector(
                Duration.ofSeconds(config.core().stallThresholdSeconds()));
        scheduler.scheduleWithFixedDelay(() -> {
            Instant now = clock.instant();
            for (var state : registry.activeTasks()) detector.evaluate(state, now).ifPresent(registry::accept);
            for (TaskSource source : sources) health.put(source.descriptor().displayName(), source.health());
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        for (TaskSource source : sources) {
            try { source.close(); } catch (Exception error) {
                if (log != null) log.debug("Source close failed", error);
            }
        }
        if (desktop != null) desktop.close();
    }
}
