package io.taskbeacon.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

final class TrayController implements AutoCloseable {
    private final Runnable toggle;
    private final Runnable settings;
    private final Runnable quit;
    private final Consumer<String> onSwitchMode;
    private TrayIcon icon;
    private MenuItem activeItem;
    private MenuItem attentionItem;
    private MenuItem switchModeItem;
    private String currentMode = "compact-card";

    TrayController(Runnable toggle, Runnable settings, Runnable quit, Consumer<String> onSwitchMode) {
        this.toggle = toggle;
        this.settings = settings;
        this.quit = quit;
        this.onSwitchMode = onSwitchMode;
    }

    void install() {
        if (!SystemTray.isSupported()) return;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem title = new MenuItem("TaskBeacon"); title.setEnabled(false); menu.add(title);
            menu.addSeparator();
            MenuItem show = new MenuItem("Show / Hide"); show.addActionListener(event -> toggle.run()); menu.add(show);
            switchModeItem = new MenuItem(modeLabel(currentMode));
            switchModeItem.addActionListener(event -> {
                String next = currentMode.equals("pet") ? "compact-card" : "pet";
                if (onSwitchMode != null) onSwitchMode.accept(next);
            });
            menu.add(switchModeItem);
            menu.addSeparator();
            activeItem = new MenuItem("Active tasks: 0"); activeItem.setEnabled(false); menu.add(activeItem);
            attentionItem = new MenuItem("Attention required: 0"); attentionItem.setEnabled(false); menu.add(attentionItem);
            menu.addSeparator();
            MenuItem sources = new MenuItem("Sources / Settings"); sources.addActionListener(event -> settings.run()); menu.add(sources);
            MenuItem exit = new MenuItem("Quit"); exit.addActionListener(event -> quit.run()); menu.add(exit);
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(59, 130, 246)); graphics.fillOval(2, 2, 12, 12); graphics.dispose();
            icon = new TrayIcon(image, "TaskBeacon", menu); icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException | RuntimeException ignored) { icon = null; }
    }

    void update(int active, int attention) {
        if (activeItem != null) activeItem.setLabel("Active tasks: " + active);
        if (attentionItem != null) attentionItem.setLabel("Attention required: " + attention);
    }

    void updateRendererMode(String mode) {
        this.currentMode = mode;
        if (switchModeItem != null) {
            switchModeItem.setLabel(modeLabel(mode));
        }
    }

    private static String modeLabel(String mode) {
        return mode.equals("pet") ? "Switch to Compact Card" : "Switch to Desktop Pet";
    }

    @Override public void close() { if (icon != null) SystemTray.getSystemTray().remove(icon); }
}
