package com.kermel.ngrok.event;

import com.kermel.ngrok.core.NgrokTunnel;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a single ngrok tunnel has been closed.
 * Fired once for each tunnel during shutdown.
 */
public class NgrokTunnelClosedEvent extends ApplicationEvent {

    private final NgrokTunnel tunnel;

    public NgrokTunnelClosedEvent(Object source, NgrokTunnel tunnel) {
        super(source);
        this.tunnel = tunnel;
    }

    public NgrokTunnel getTunnel() {
        return tunnel;
    }
}
