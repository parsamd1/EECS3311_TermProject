package avsa.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaudeClientTest {

    @Test
    void linksOnAFetchedPageAreFoundWhetherAbsoluteOrRelative() {
        String page = "Results: [2022 Mazda CX-5 GS](/offers/mazda-cx-5-gs-1234) $28,900 "
                + "<a href=\"/offers/mazda-cx-5-gt-5678\">GT</a> see https://www.kijiji.ca/v-cars/abc and (https://x.test/y).";
        Set<String> links = ClaudeClient.linksIn("https://www.autotrader.ca/cars/mazda/cx-5/on/toronto/", page);
        assertTrue(links.contains("https://www.autotrader.ca/offers/mazda-cx-5-gs-1234"));
        assertTrue(links.contains("https://www.autotrader.ca/offers/mazda-cx-5-gt-5678"));
        assertTrue(links.contains("https://www.kijiji.ca/v-cars/abc"));
        assertTrue(links.contains("https://x.test/y"));
    }
}
