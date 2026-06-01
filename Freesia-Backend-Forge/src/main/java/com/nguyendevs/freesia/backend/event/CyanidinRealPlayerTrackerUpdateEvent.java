package com.nguyendevs.freesia.backend.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class CyanidinRealPlayerTrackerUpdateEvent extends Event {
    private final Player watcher;
    private final Player beingWatched;

    public CyanidinRealPlayerTrackerUpdateEvent(Player watcher, Player beingWatched) {
        this.watcher = watcher;
        this.beingWatched = beingWatched;
    }

    public Player getWatcher() {
        return this.watcher;
    }

    public Player getBeingWatched() {
        return this.beingWatched;
    }
}
