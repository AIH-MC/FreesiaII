package com.nguyendevs.freesia.backend.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.Set;
import java.util.UUID;

public class CyanidinTrackerScanEvent extends Event {
    private final Set<UUID> results;
    private final UUID viewer;

    public CyanidinTrackerScanEvent(Set<UUID> results, UUID viewer) {
        this.results = results;
        this.viewer = viewer;
    }

    public UUID getViewer() {
        return this.viewer;
    }

    public Set<UUID> getResultsModifiable() {
        return this.results;
    }

    public Set<UUID> getResultsUnmodifiable() {
        return Set.copyOf(this.results);
    }
}
