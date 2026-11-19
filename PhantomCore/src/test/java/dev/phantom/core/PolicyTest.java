package dev.phantom.core;

import dev.phantom.config.ServerProfileStore;
import dev.phantom.core.module.Category;
import dev.phantom.core.module.Module;
import dev.phantom.core.policy.PolicyEvaluator;
import dev.phantom.core.policy.PolicyEvaluator.Context;
import dev.phantom.core.policy.ServerPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PolicyTest {
    static final class Cosmetic extends Module {
        Cosmetic() {
            super("Zoom", "", Category.MISC);
        }
    }

    static final class Esp extends Module {
        Esp() {
            super("ESP", "", Category.VISUALS);
            restricted();
        }
    }

    static final class Gui extends Module {
        Gui() {
            super("ClickGUI", "", Category.MISC);
            notToggleable();
        }
    }

    private final Module zoom = new Cosmetic(), esp = new Esp(), gui = new Gui();

    @Test
    void privateWorldsAllowEverything() {
        ServerPolicy denyAll = ServerPolicy.parse("{\"deny\":[\"*\"]}", "test");
        for (Context c : new Context[]{Context.MENU, Context.PRIVATE_WORLD}) {
            assertNull(PolicyEvaluator.deny(esp, c, denyAll, Set.of("esp")));
        }
    }

    @Test
    void defaultRemotePolicyAllowsOnlyCosmetic() {
        assertNull(PolicyEvaluator.deny(zoom, Context.REMOTE_SERVER, ServerPolicy.DEFAULT, Set.of()));
        assertEquals(PolicyEvaluator.NOT_PERMITTED, PolicyEvaluator.deny(esp, Context.REMOTE_SERVER, ServerPolicy.DEFAULT, Set.of()));
        assertNull(PolicyEvaluator.deny(gui, Context.REMOTE_SERVER, ServerPolicy.parse("{\"deny\":[\"*\"]}", "s"), Set.of()),
                "the GUI itself always opens");
    }

    @Test
    void serverGrantsAndDenials() {
        ServerPolicy p = ServerPolicy.parse("{\"allow\":[\"esp\"],\"deny\":[\"Zoom\"],\"message\":\"hi\"}", "phantom:policy");
        assertNull(PolicyEvaluator.deny(esp, Context.REMOTE_SERVER, p, Set.of()));
        assertEquals(PolicyEvaluator.DENIED_BY_SERVER, PolicyEvaluator.deny(zoom, Context.REMOTE_SERVER, p, Set.of()));
        // The user may restrict further but never loosen.
        assertEquals(PolicyEvaluator.BLOCKED_BY_USER, PolicyEvaluator.deny(esp, Context.REMOTE_SERVER, p, Set.of("esp")));
        ServerPolicy both = ServerPolicy.parse("{\"allow\":[\"*\"],\"deny\":[\"ESP\"]}", "s");
        assertEquals(PolicyEvaluator.DENIED_BY_SERVER, PolicyEvaluator.deny(esp, Context.REMOTE_SERVER, both, Set.of()), "deny wins");
    }

    @Test
    void rejectsMalformedPolicies() {
        assertThrows(RuntimeException.class, () -> ServerPolicy.parse("[1,2]", "s"));
        assertThrows(RuntimeException.class, () -> ServerPolicy.parse("{\"allow\":\"esp\"}", "s"));
        assertThrows(RuntimeException.class, () -> ServerPolicy.parse("x".repeat(20_000), "s"));
        assertThrows(RuntimeException.class, () -> ServerPolicy.parse("{not json", "s"));
        assertEquals("espcolors", ServerPolicy.key("ESP Colors"));
    }

    @Test
    void serverProfilesPersistWithoutCachingGrants(@TempDir Path dir) throws Exception {
        ServerProfileStore store = new ServerProfileStore(dir);
        store.recordJoin("Play.Example.NET:25565", "Example", "Paper", "1.21.1", 767, 1000);
        store.recordJoin("play.example.net", null, "Paper", "", 0, 2000);
        store.recordPolicy("play.example.net", ServerPolicy.parse("{\"allow\":[\"ESP\"]}", "phantom:policy"));
        store.setBlocked("play.example.net", "Fullbright", true);
        store.get("play.example.net").configProfile = "PvE";
        store.save();

        ServerProfileStore again = new ServerProfileStore(dir);
        again.load();
        ServerProfileStore.Profile p = again.find("play.example.net:25565");
        assertEquals(2, p.joins);
        assertEquals("1.21.1", p.version);
        assertEquals(767, p.protocol);
        assertEquals(Set.of("fullbright"), p.blocked);
        assertEquals("PvE", p.configProfile);
        assertEquals("esp", p.lastAllowed.get(0));
        assertEquals(1, again.all().size());

        Files.writeString(dir.resolve("servers.json"), "{broken");
        ServerProfileStore broken = new ServerProfileStore(dir);
        broken.load();
        assertTrue(broken.all().isEmpty(), "corrupt file is ignored, not fatal");
    }

    @Test
    void pausedModulesResumeAndStaySavedAsEnabled() {
        var mm = new dev.phantom.core.module.ModuleManager(new dev.phantom.core.event.EventBus());
        Module e = mm.register(new Esp());
        boolean[] remote = {false};
        mm.setGuard(m -> PolicyEvaluator.deny(m, remote[0] ? Context.REMOTE_SERVER : Context.PRIVATE_WORLD, ServerPolicy.DEFAULT, Set.of()));
        e.setEnabled(true);
        remote[0] = true;
        e.suspend(mm.guard().deny(e));
        assertFalse(e.isEnabled());
        assertTrue(e.isSuspended());
        var json = dev.phantom.config.ConfigSerializer.snapshot(mm, new dev.phantom.core.hud.HudRegistry());
        assertTrue(json.getAsJsonObject("modules").getAsJsonObject("ESP").get("enabled").getAsBoolean());
        assertFalse(e.resume(), "still not permitted");
        assertNotNull(e.setEnabled(true), "user cannot force it on");
        remote[0] = false;
        assertTrue(e.resume());
        assertTrue(e.isEnabled());
        // Profile loaded while on a server: wanted modules come back paused, not lost.
        remote[0] = true;
        e.setEnabled(false);
        e.restoreEnabled(true);
        assertTrue(e.isSuspended());
        // Turning off a paused module clears the intent.
        e.setEnabled(false);
        assertFalse(e.isSuspended());
    }

    @Test
    void throwingHandlerDisablesOnlyThatModule() {
        var bus = new dev.phantom.core.event.EventBus();
        var mm = new dev.phantom.core.module.ModuleManager(bus);
        final class Boom extends Module {
            Boom() {
                super("Boom", "", Category.MISC);
                on(TestModules.Tick.class, t -> {
                    throw new IllegalStateException("bug");
                });
            }
        }
        Module boom = mm.register(new Boom());
        TestModules.Counter counter = mm.register(new TestModules.Counter());
        String[] err = new String[1];
        mm.addListener(new dev.phantom.core.module.ModuleManager.Listener() {
            @Override
            public void onError(Module module, RuntimeException error) {
                err[0] = module.getName() + ": " + error.getMessage();
            }
        });
        boom.setEnabled(true);
        counter.setEnabled(true);
        assertDoesNotThrow(() -> bus.post(new TestModules.Tick()));
        assertFalse(boom.isEnabled());
        assertEquals("Boom: bug", err[0]);
        assertEquals(1, counter.ticks, "other modules still ran");
        bus.post(new TestModules.Tick());
        assertEquals(2, counter.ticks);
    }
}
