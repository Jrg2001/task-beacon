package io.taskbeacon.ui;

/** One row in a task list: a top-level task, or a subtask (e.g. a Claude subagent) shown beneath it. */
record TaskListItem(TaskCardViewModel model, boolean subtask) { }
