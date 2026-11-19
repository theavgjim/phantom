package dev.phantom.core.module;

/**
 * Decides whether a module may be enabled in the current game context.
 * Implemented by the Minecraft integration (private-world policy).
 */
@FunctionalInterface
public interface ActivationGuard {
    ActivationGuard ALLOW_ALL = m -> null;

    /** @return {@code null} to allow, otherwise a human readable reason for refusing. */
    String deny(Module module);
}
