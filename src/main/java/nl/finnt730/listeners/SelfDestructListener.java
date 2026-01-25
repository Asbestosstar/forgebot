package nl.finnt730.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import nl.finnt730.Global;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelfDestructListener extends ListenerAdapter {
	private static final Logger logger = LoggerFactory.getLogger(SelfDestructListener.class);
	private static final String TRASH_EMOJI = "🗑️";
	private static final Emoji TRASH_EMOJI_OBJ = Emoji.fromUnicode(TRASH_EMOJI);

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		try {
			// Only process messages from the bot itself
			if (!event.getAuthor().isBot() || !event.getJDA().getSelfUser().equals(event.getAuthor())) {
				return;
			}

			Message message = event.getMessage();
			logger.debug("Adding self-destruct reaction to message {}", message.getId());
			message.addReaction(TRASH_EMOJI_OBJ).queue();
		} catch (Exception e) {
			logger.error("Error adding self-destruct reaction", e);
		}
	}

	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		try {
			// Ignore reactions from bots
			if (event.getUser().isBot()) {
				return;
			}

			// Only process trash bin reactions
			if (!TRASH_EMOJI.equals(event.getReaction().getEmoji().getName())) {
				return;
			}

			// Check if the reacted message is from our bot
			Message message = event.getChannel().retrieveMessageById(event.getMessageId()).complete();
			if (!event.getJDA().getSelfUser().equals(message.getAuthor())) {
				return;
			}

			// Check if the reactor has permission to delete
			Member reactor = event.getMember();
			if (reactor == null)
				return;

			boolean hasPermission = reactor.getRoles().stream()
					.anyMatch(role -> role.isHoisted() || Global.isManager(role) || reactor.isOwner());

			if (hasPermission) {
				logger.info("Deleting message {} by request of privileged user {}", message.getId(),
						event.getUser().getName());
				message.delete().queue();
			}
		} catch (Exception e) {
			logger.error("Error processing self-destruct reaction", e);
		}
	}
}