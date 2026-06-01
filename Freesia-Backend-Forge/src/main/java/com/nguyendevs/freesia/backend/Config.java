package com.nguyendevs.freesia.backend;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue REFRESH_INTERVAL;

    static {
        BUILDER.push("General Settings");
        REFRESH_INTERVAL = BUILDER
                .comment("The interval in ticks between tracker sync updates. (20 ticks = 1 second)")
                .defineInRange("refreshInterval", 40, 1, 1200);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
