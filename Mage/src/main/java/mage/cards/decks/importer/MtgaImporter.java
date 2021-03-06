package mage.cards.decks.importer;

import com.google.common.collect.ImmutableMap;
import mage.cards.decks.CardNameUtil;
import mage.cards.decks.DeckCardInfo;
import mage.cards.decks.DeckCardLists;
import mage.cards.repository.CardCriteria;
import mage.cards.repository.CardInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mage.cards.decks.CardNameUtil.CARD_NAME_PATTERN;

public class MtgaImporter extends PlainTextDeckImporter {

    private static final Map<String, String> SET_REMAPPING = ImmutableMap.of("DAR", "DOM");
    private static final Pattern MTGA_PATTERN = Pattern.compile(
            "(\\p{Digit}+)" +
            "\\p{javaWhitespace}+" +
            "(" + CARD_NAME_PATTERN.pattern() + ")" +
            "\\p{javaWhitespace}+" +
            "\\((\\p{Alnum}+)\\)" +
            "\\p{javaWhitespace}+" +
            "(\\p{Digit}+)");

    private final CardLookup lookup = getCardLookup();
    private boolean sideboard = false;

    @Override
    protected void readLine(String line, DeckCardLists deckList) {
        if (line.trim().equals("")) {
            sideboard = true;
            return;
        }

        Matcher m = MTGA_PATTERN.matcher(CardNameUtil.normalizeCardName(line));
        if (m.matches()) {
            int count = Integer.parseInt(m.group(1));
            String name = m.group(2);
            String set = SET_REMAPPING.getOrDefault(m.group(3), m.group(3));
            String cardNumber = m.group(4);
            final List<DeckCardInfo> zone = sideboard ? deckList.getSideboard() : deckList.getCards();
            Optional<CardInfo> found = lookup.lookupCardInfo(name, set, cardNumber);
            if (!found.isPresent()) {
                sbMessage.append("Cound not find card for '").append(line).append("'\n");
            } else {
                found.ifPresent(card -> zone.addAll(Collections.nCopies(count,
                        new DeckCardInfo(card.getName(), card.getCardNumber(), card.getSetCode()))));
            }
        } else {
            sbMessage.append("Error reading '").append(line).append("'\n");
        }
    }

}
