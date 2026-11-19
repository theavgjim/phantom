package dev.phantom.integration.event;

import net.minecraft.entity.Entity;

/** Posted when the local player is about to attack an entity (any source). */
public final class AttackEntityEvent {
    public static final AttackEntityEvent INSTANCE = new AttackEntityEvent();

    public Entity target;

    private AttackEntityEvent() {
    }
}
