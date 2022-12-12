package eu.hexsz.chunkclaimer;

import eu.hexsz.chunkclaimer.commands.CountryCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class MainEntryPoint implements ModInitializer {

    private static boolean flag = true;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(CountryCommand::register);
        CommandRegistrationCallback.EVENT.register(
                ((dispatcher, registryAccess, environment) ->
                    dispatcher.register(
                            CommandManager.literal("foo")
                                    .executes(
                                            context -> {
                                                context
                                                        .getSource()
                                                        .sendMessage(
                                                                Text.literal("Called /foo noice!")
                                                        );
                                                flag = false;
                                                return 1;
                                            }
                                    )
                    )
                )
        );
        CommandRegistrationCallback.EVENT.register(
                ((dispatcher, registryAccess, environment) ->
                        dispatcher.register(
                                CommandManager.literal("bar")
                                        .executes(
                                                context -> {
                                                    context
                                                            .getSource()
                                                            .sendMessage(
                                                                    Text.literal("Called /bar noice!")
                                                            );
                                                    flag = true;
                                                    return 1;
                                                }
                                        )
                        )
                )
        );
        AttackBlockCallback.EVENT.register(((player, world, hand, pos, direction) -> {
            if (world.isClient() || flag) return ActionResult.PASS;
            return ActionResult.FAIL;
        }));
        //TODO IMPORTANT: show title when entering country
    }
}
