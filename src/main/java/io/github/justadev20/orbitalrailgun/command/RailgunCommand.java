package io.github.justadev20.orbitalrailgun.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.justadev20.orbitalrailgun.OrbitalRailgunPlugin;
import io.github.justadev20.orbitalrailgun.RailgunKeys;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** /railgun give &lt;players&gt; | reload | strike &lt;position&gt; | stop */
@SuppressWarnings("UnstableApiUsage")
public final class RailgunCommand {
    private final OrbitalRailgunPlugin plugin;

    public RailgunCommand(OrbitalRailgunPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("railgun")
                .requires(source -> has(source, RailgunKeys.PERMISSION_GIVE) || has(source, RailgunKeys.PERMISSION_ADMIN))
                .then(Commands.literal("give")
                        .requires(source -> has(source, RailgunKeys.PERMISSION_GIVE))
                        .then(Commands.argument("players", ArgumentTypes.players()).executes(this::give)))
                .then(Commands.literal("reload")
                        .requires(source -> has(source, RailgunKeys.PERMISSION_ADMIN))
                        .executes(this::reload))
                .then(Commands.literal("strike")
                        .requires(source -> has(source, RailgunKeys.PERMISSION_ADMIN))
                        .then(Commands.argument("position", ArgumentTypes.blockPosition()).executes(this::strike)))
                .then(Commands.literal("stop")
                        .requires(source -> has(source, RailgunKeys.PERMISSION_ADMIN))
                        .executes(this::stop))
                .build();
    }

    private static boolean has(CommandSourceStack source, String permission) {
        return source.getSender().hasPermission(permission);
    }

    private int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<Player> players = context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
        for (Player player : players) {
            player.getInventory().addItem(plugin.createRailgun()).values()
                    .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        }
        context.getSource().getSender().sendMessage(plugin.messages().tr("orbital_railgun.command.given", players.size()));
        return players.size();
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reload();
        context.getSource().getSender().sendMessage(plugin.messages().tr("orbital_railgun.command.reloaded"));
        return Command.SINGLE_SUCCESS;
    }

    private int strike(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPosition position = context.getArgument("position", BlockPositionResolver.class).resolve(context.getSource());
        World world = context.getSource().getLocation().getWorld();
        Block target = world.getBlockAt(position.blockX(), position.blockY(), position.blockZ());
        Player shooter = context.getSource().getExecutor() instanceof Player player ? player : null;
        if (!plugin.strikes().launch(shooter, target)) {
            context.getSource().getSender().sendMessage(plugin.messages().tr("orbital_railgun.strike_blocked"));
            return 0;
        }
        context.getSource().getSender().sendMessage(plugin.messages().tr("orbital_railgun.command.strike", target.getX(), target.getY(), target.getZ()));
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> context) {
        int stopped = plugin.strikes().stopAll();
        context.getSource().getSender().sendMessage(plugin.messages().tr("orbital_railgun.command.stopped", stopped));
        return stopped;
    }
}
