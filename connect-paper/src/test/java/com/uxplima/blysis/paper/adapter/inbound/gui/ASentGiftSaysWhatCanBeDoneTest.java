package com.uxplima.blysis.paper.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.uxplima.blysis.domain.account.SentGift;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A sent gift's tile closes with what the sender can still do with it.
 *
 * <p>Found live: Steve's gift to a Bedrock player was accepted, and its tile read "State: accepted" over "Click
 * to take it back". The click was refused, rightly. The tile now picks its closing sentence by the gift's state.
 */
final class ASentGiftSaysWhatCanBeDoneTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @DisplayName("a gift still waiting offers to be taken back, an accepted one says it is theirs")
    void theClosingSentenceFollowsTheGift() {
        assertThat(ChestMenus.sentState(new SentGift("g1", "Ten diamonds", null, 1, "Alex", true)))
                .isEqualTo("waiting");
        assertThat(ChestMenus.sentState(new SentGift("g1", "Ten diamonds", null, 1, "Alex", false)))
                .isEqualTo("accepted");
    }

    @Test
    @DisplayName("the window asks for a sentence by state, and every language has one for each state")
    void everyStateHasItsSentence() throws IOException {
        assertThat(Files.readString(RESOURCES.resolve("menus/sent.conf")))
                .contains("action:@menu.sent.gift.actions.%" + ChestMenus.SENT_STATE + "%");
        for (String language : new String[] {"en", "tr"}) {
            Map<String, String> lines = MessageCatalogLoader.flatten(HoconConfigurationLoader.builder()
                    .path(RESOURCES.resolve("messages/messages_" + language + ".conf"))
                    .build()
                    .load());
            assertThat(lines)
                    .as(language)
                    .containsKeys("menu.sent.gift.actions.waiting", "menu.sent.gift.actions.accepted");
            assertThat(lines.get("menu.sent.gift.actions.accepted"))
                    .as(language)
                    .doesNotContain("<cta>");
        }
    }
}
