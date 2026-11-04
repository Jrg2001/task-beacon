package io.taskbeacon.ui;

public record TaskCardViewModel(String icon, String source, String title, String status,
                                String elapsed, String badge, String evidenceHint, int activeTaskCount) { }
