package dev.phantom.core.policy;

import dev.phantom.core.module.Module;

import java.util.Set;

/**
 * The single rule deciding whether a module may run:
 *
 * <ol>
 *   <li>Menu / private world (singleplayer or own LAN host): everything is allowed.</li>
 *   <li>Remote server: a module the server {@code deny}s is off; a module the user blocked for
 *       this server is off; a restricted module is off unless the server {@code allow}s it.</li>
 * </ol>
 * The user can only add restrictions; nothing on the client can grant what the server did not.
 */
public final class PolicyEvaluator {
    public enum Context { MENU, PRIVATE_WORLD, REMOTE_SERVER }

    public static final String DENIED_BY_SERVER = "Disabled by this server";
    public static final String BLOCKED_BY_USER = "Blocked in your profile for this server";
    public static final String NOT_PERMITTED = "Not permitted on this server";

    private PolicyEvaluator() {
    }

    /** @return {@code null} if allowed, otherwise the reason. */
    public static String deny(Module module, Context context, ServerPolicy policy, Set<String> userBlocked) {
        if (context != Context.REMOTE_SERVER) return null;
        if (!module.isToggleable() && !module.isHoldToActivate()) return null; // ClickGUI etc. always work
        ServerPolicy p = policy == null ? ServerPolicy.DEFAULT : policy;
        String key = ServerPolicy.key(module.getName());
        if (p.denies(key)) return DENIED_BY_SERVER;
        if (userBlocked != null && userBlocked.contains(key)) return BLOCKED_BY_USER;
        if (module.isRestricted() && !p.allows(key)) return NOT_PERMITTED;
        return null;
    }
}
