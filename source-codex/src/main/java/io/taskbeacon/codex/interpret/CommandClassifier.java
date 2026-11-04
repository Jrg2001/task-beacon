package io.taskbeacon.codex.interpret;

import io.taskbeacon.core.model.Activity;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CommandClassifier {
    private static final Pattern TEST = Pattern.compile("(^|[ /\\\\])(mvnw?|gradlew?|npm|pnpm|cargo|go)(\\.cmd|\\.exe)?\\s+test(\\s|$)|(^|\\s)pytest(\\s|$)");

    public Activity classify(String command) {
        if (command == null) return Activity.EXECUTING_COMMAND;
        return TEST.matcher(command.toLowerCase(Locale.ROOT)).find() ? Activity.TESTING : Activity.EXECUTING_COMMAND;
    }
}
