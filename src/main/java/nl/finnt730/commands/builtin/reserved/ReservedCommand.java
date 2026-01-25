package nl.finnt730.commands.builtin.reserved;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import nl.finnt730.Global;
import nl.finnt730.commands.Command;

public abstract class ReservedCommand extends Command {
    public ReservedCommand() {
    }

    @Override
    public final boolean canInvoke(Guild guild, Member invoker) {
        return invoker.getRoles().stream().anyMatch((role) -> role.isHoisted() || Global.isManager(role) || invoker.isOwner());
    }
}
