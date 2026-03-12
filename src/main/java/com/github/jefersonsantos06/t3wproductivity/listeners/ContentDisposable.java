package com.github.jefersonsantos06.t3wproductivity.listeners;

import com.intellij.openapi.Disposable;

public final class ContentDisposable implements Disposable {
    @Override
    public void dispose() {
        // No-op: used to bind listeners lifecycle to ToolWindow content.
    }
}