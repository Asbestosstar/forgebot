package nl.finnt730.listeners;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GnomeBotDevListener extends ListenerAdapter {
	private static final Logger logger = LoggerFactory.getLogger("nl.finnt730.linkconverter");
	private static final String GNOMEBOT_DOMAIN = "gnomebot.dev";
	private static final String MCLOGS_PATH = "/paste/mclogs/";
	private static final String SHIELD_EMOJI = "🛡️";
	private static final Emoji SHIELD_EMOJI_OBJ = Emoji.fromUnicode(SHIELD_EMOJI);

	private static final Pattern GNOMEBOT_PATTERN = Pattern.compile(
			"https?://" + Pattern.quote(GNOMEBOT_DOMAIN) + Pattern.quote(MCLOGS_PATH) + "([a-zA-Z0-9]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern LOGS_UPLOADED_PATTERN = Pattern
			.compile("The logs have been uploaded to `" + Pattern.quote(GNOMEBOT_DOMAIN) + "`");

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		try {
			if (event.getAuthor().isBot()) {
				return;
			}

			Message message = event.getMessage();
			String content = message.getContentRaw();

			if (content.contains(GNOMEBOT_DOMAIN)) {
				logger.debug("Found gnomebot.dev link in message from user {}", event.getAuthor().getName());
				message.addReaction(SHIELD_EMOJI_OBJ).queue();
			}
		} catch (Exception e) {
			logger.error("Error processing message for link conversion setup", e);
		}
	}

	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		try {
			if (event.getUser().isBot()) {
				return;
			}

			if (SHIELD_EMOJI.equals(event.getReaction().getEmoji().getName())) {
				logger.info("Link conversion reaction triggered by user {} on message {}", event.getUser().getName(),
						event.getMessageId());

				event.getChannel().retrieveMessageById(event.getMessageId()).queue(message -> {
					// Clear all reactions first to prevent duplicate processing
					message.clearReactions().queue(v -> {
						try {
							String content = message.getContentRaw();
							final String convertedContent = LOGS_UPLOADED_PATTERN.matcher(convertGnomebotLinks(content))
									.replaceAll("The logs have been uploaded to `mclo.gs`");

							if (!convertedContent.equals(content)) {
								message.reply(convertedContent).mentionRepliedUser(false).queue();
							}
						} catch (Exception e) {
							logger.error("Error processing link conversion reaction", e);
						}
					});
				});
			}
		} catch (Exception e) {
			logger.error("Error handling link conversion reaction", e);
		}
	}

	private String convertGnomebotLinks(String content) {
		Matcher matcher = GNOMEBOT_PATTERN.matcher(content);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String originalLink = matcher.group(0);
			String linkId = matcher.group(1);
			String mcloLink = "https://mclo.gs/" + linkId;

			logger.debug("Converting link: {} -> {}", originalLink, mcloLink);
			matcher.appendReplacement(result, mcloLink);
		}
		matcher.appendTail(result);

		return result.toString();
	}
}