package com.github.jefersonsantos06.t3wproductivity;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

@NonNls
public final class MyBundle extends DynamicBundle {
    private static final String BUNDLE = "messages.MyBundle";
    private static final MyBundle INSTANCE = new MyBundle();

    private MyBundle() {
        super(BUNDLE);
    }

    public static @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    @SuppressWarnings("unused")
    public static @NotNull Supplier<@Nls String> messagePointer(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
