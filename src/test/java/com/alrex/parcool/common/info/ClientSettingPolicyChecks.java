package com.alrex.parcool.common.info;

import com.alrex.parcool.config.ParCoolConfig;
import com.electronwill.nightconfig.core.CommentedConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientSettingPolicyChecks {
    private ClientSettingPolicyChecks() {}

    public static void run() throws ReflectiveOperationException {
        ModConfigSpec spec = ParCoolConfig.Client.getConfigSpec();
        var loadedField = ModConfigSpec.class.getDeclaredField("loadedConfig");
        loadedField.setAccessible(true);
        var previous = (IConfigSpec.ILoadedConfig) loadedField.get(spec);
        CommentedConfig values = CommentedConfig.inMemory();
        spec.correct(values);
        try {
            spec.acceptConfig(
                    new IConfigSpec.ILoadedConfig() {
                        @Override
                        public CommentedConfig config() {
                            return values;
                        }

                        @Override
                        public void save() {
                            throw new AssertionError("The fixture must not write a config file");
                        }
                    });
            for (var item : policyItems()) item.set(false);
            var snapshot = ClientSetting.readFromLocalConfig();
            for (var item : policyItems()) check(!snapshot.get(item), "pre-JOIN stored false");

            // A getter override installed at JOIN has the same late change in effective values.
            for (var item : policyItems()) item.set(true);
            for (var item : policyItems()) check(snapshot.get(item), "local sees late policy");
            ByteBuf buffer = Unpooled.buffer();
            try {
                snapshot.writeTo(buffer);
                var remote = ClientSetting.readFrom(buffer);
                for (var item : policyItems()) check(remote.get(item), "wire sees late policy");
                check(!buffer.isReadable(), "one complete native ClientSetting");
            } finally {
                buffer.release();
            }
            for (var item : policyItems()) item.set(false);
            for (var item : policyItems())
                check(!snapshot.get(item), "disconnect uses stored value");
        } finally {
            spec.acceptConfig(previous);
        }
    }

    private static ParCoolConfig.Client.Booleans[] policyItems() {
        return new ParCoolConfig.Client.Booleans[] {
            ParCoolConfig.Client.Booleans.EnableAnimation,
            ParCoolConfig.Client.Booleans.EnableFallingAnimation,
            ParCoolConfig.Client.Booleans.EnableLeanAnimationOfFastRun
        };
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
