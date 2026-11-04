package io.taskbeacon.ui;

import javafx.scene.Node;

public interface StatusRenderer {
    String id();
    String displayName();
    Node create();
    default void update(TaskCardViewModel model) {
        update(model, null);
    }
    void update(TaskCardViewModel primary, TaskCardViewModel secondary);
    void idle();
}
