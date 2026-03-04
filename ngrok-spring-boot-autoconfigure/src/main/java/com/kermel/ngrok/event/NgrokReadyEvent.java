package com.kermel.ngrok.event;

import com.kermel.ngrok.core.NgrokTunnel;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Published once when all ngrok tunnels have been established.
 * This is the event that downstream consumers (e.g., webhook auto-registration)
 * should listen for.
 */
public class NgrokReadyEvent extends ApplicationEvent {

    private final Collection<NgrokTunnel> tunnels;

    public NgrokReadyEvent(Object source, Collection<NgrokTunnel> tunnels) {
        super(source);
        this.tunnels = List.copyOf(tunnels);
    }

    public Collection<NgrokTunnel> getTunnels() {
        return tunnels;
    }
}
