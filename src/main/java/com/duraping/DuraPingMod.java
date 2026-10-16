package com.duraping;

import com.duraping.client.DuraPingClientHooks;
import com.duraping.config.DuraPingConfig;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Loads the config and registers tick monitoring plus the
 * {@code /duraping} client commands. Client-side only: no server mod, no
 * packets, no server installation needed.
 */
public class DuraPingMod implements ClientModInitializer {
    public static final String MOD_ID = "duraping";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        DuraPingConfig.load();
        DuraPingClientHooks.register();
        DuraPingConfig config = DuraPingConfig.get();
        LOG.info("[DuraPing] initialized: enabled={} thresholds={} countdown={} slots={}",
                config.enabled, DuraPingConfig.describeThresholds(config),
                config.countdownThreshold, DuraPingConfig.describeSlots(config));
    }
}
