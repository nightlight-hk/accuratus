package org.nightcat.accuratus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AccuratusClient implements ClientModInitializer {

    private static boolean fixedTargetEnabled;
    private static boolean trackTargetEnabled;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("fixedtarget")
                            .executes(context -> {
                                fixedTargetEnabled = !fixedTargetEnabled;
                                if (fixedTargetEnabled && trackTargetEnabled) {
                                    trackTargetEnabled = false;
                                    context.getSource().sendFeedback(Text.literal(
                                            "Prediction tracking mode disabled because fixed target aiming mode was enabled."
                                    ));
                                }
                                context.getSource().sendFeedback(Text.literal(
                                        "Fixed target aiming mode " + (fixedTargetEnabled ? "enabled" : "disabled") + "."
                                ));
                                return 1;
                            })
            );

            dispatcher.register(
                    literal("tracktarget")
                            .executes(context -> {
                                trackTargetEnabled = !trackTargetEnabled;
                                if (trackTargetEnabled && fixedTargetEnabled) {
                                    fixedTargetEnabled = false;
                                    context.getSource().sendFeedback(Text.literal(
                                            "Fixed target aiming mode disabled because prediction tracking mode was enabled."
                                    ));
                                }
                                context.getSource().sendFeedback(Text.literal(
                                        "Prediction tracking mode " + (trackTargetEnabled ? "enabled" : "disabled") + "."
                                ));
                                return 1;
                            })
            );
        });
    }

    public static boolean isFixedTargetEnabled() {
        return fixedTargetEnabled;
    }

    public static boolean isTrackTargetEnabled() {
        return trackTargetEnabled;
    }
}