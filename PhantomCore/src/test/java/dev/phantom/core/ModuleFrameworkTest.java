package dev.phantom.core;

import dev.phantom.core.event.EventBus;
import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import dev.phantom.core.util.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleFrameworkTest {

    @Test
    void handlersOnlyRunWhileEnabled() {
        EventBus bus = new EventBus();
        ModuleManager mm = new ModuleManager(bus);
        TestModules.Counter c = mm.register(new TestModules.Counter());

        bus.post(new TestModules.Tick());
        assertEquals(0, c.ticks);
        assertEquals(0, bus.listenerCount());

        c.setEnabled(true);
        bus.post(new TestModules.Tick());
        bus.post(new TestModules.Tick());
        assertEquals(2, c.ticks);

        c.setEnabled(false);
        bus.post(new TestModules.Tick());
        assertEquals(2, c.ticks);
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void guardRefusesPrivateWorldModules() {
        ModuleManager mm = new ModuleManager(new EventBus());
        Module r = mm.register(new TestModules.Restricted());
        mm.setGuard(m -> m.isPrivateWorldOnly() ? "remote server" : null);
        String[] refused = new String[1];
        mm.addListener(new ModuleManager.Listener() {
            @Override
            public void onRefused(Module module, String reason) {
                refused[0] = reason;
            }
        });
        assertEquals("remote server", r.setEnabled(true));
        assertFalse(r.isEnabled());
        assertEquals("remote server", refused[0]);

        mm.setGuard(null);
        assertNull(r.setEnabled(true));
        assertTrue(r.isEnabled());
    }

    @Test
    void numberSettingClampsAndSnaps() {
        TestModules.Counter c = new TestModules.Counter();
        c.range.set(99.0);
        assertEquals(6.0, c.range.get());
        c.range.set(3.04);
        assertEquals(3.0, c.range.get());
        c.range.setFraction(0.5);
        assertEquals(3.5, c.range.get());
        assertEquals("3.5", c.range.format());
    }

    @Test
    void modeSettingRejectsUnknown() {
        TestModules.Counter c = new TestModules.Counter();
        c.mode.set("b");
        assertEquals("B", c.mode.get());
        c.mode.set("nope");
        assertEquals("B", c.mode.get());
        c.mode.cycle(1);
        assertEquals("C", c.mode.get());
        c.mode.cycle(1);
        assertEquals("A", c.mode.get());
    }

    @Test
    void searchMatchesNameDescriptionAndCategory() {
        ModuleManager mm = new ModuleManager(new EventBus());
        mm.register(new TestModules.Counter());
        mm.register(new TestModules.Restricted());
        assertEquals(1, mm.search("count").size());
        assertEquals(1, mm.search("combat").size());
        assertEquals(2, mm.search("").size());
    }

    @Test
    void versionsCompare() {
        assertTrue(Version.parse("1.21.1").compareTo(Version.parse("1.21")) > 0);
        assertTrue(Version.parse("0.16.14").atLeast(Version.parse("0.16.0")));
        assertTrue(Version.parse("1.0.0").compareTo(Version.parse("1.0.0-beta.1")) > 0);
        assertTrue(Version.parse("v1.2.0").compareTo(Version.parse("1.10.0")) < 0);
    }
}
