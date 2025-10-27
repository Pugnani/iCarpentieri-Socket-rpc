

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

public class AuctionItemTest {

    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(12345);
    }

    /**
     * Genera un oggetto AuctionItem casuale con parametri realistici.
     */
    private AuctionItem randomItem() {
        String name = "Item-" + random.nextInt(1000);
        String desc = "Descrizione per " + name;
        double startingPrice = 10 + random.nextDouble() * 90;  // tra 10 e 100
        int minIncrement = 1 + random.nextInt(50);             // tra 1 e 50
        return new AuctionItem(name, desc, startingPrice, minIncrement);
    }

    // TEST DI STATO INIZIALE

    @RepeatedTest(5)
    void testInitialStateRandom() {
        AuctionItem item = randomItem();
        assertEquals(item.getStartingPrice(), item.getCurrentBid(),
                "currentBid deve essere uguale a startingPrice all'inizio");
        assertNull(item.getHighestBidder(),
                "highestBidder deve essere null all'inizio");
    }

    // TEST OFFERTE VALIDE

    @Test
    void testPlaceBidExactlyMinIncrement() {
        AuctionItem item = new AuctionItem("Lampada", "Antica lampada in ottone", 100.0, 10);
        double bid = 110.0; // esattamente 100 + 10
        boolean accepted = item.placeBid("Mario", bid);

        assertTrue(accepted, "Offerta uguale a currentBid + minIncrement deve essere accettata");
        assertEquals(bid, item.getCurrentBid(), "currentBid deve aggiornarsi");
        assertEquals("Mario", item.getHighestBidder(), "highestBidder deve aggiornarsi");
    }

    @Test
    void testPlaceBidTooLow() {
        AuctionItem item = new AuctionItem("Vaso", "Vaso cinese", 200.0, 20);
        double bid = 215.0; // min sarebbe 220
        boolean accepted = item.placeBid("Luca", bid);

        assertFalse(accepted, "Offerta sotto il minimo deve essere rifiutata");
        assertEquals(200.0, item.getCurrentBid(), "currentBid non deve cambiare");
        assertNull(item.getHighestBidder(), "highestBidder deve restare null");
    }

    @Test
    void testMultipleValidBids() {
        AuctionItem item = new AuctionItem("Statua", "Statua romana", 300.0, 50);
        assertTrue(item.placeBid("Alice", 360.0));
        assertTrue(item.placeBid("Bob", 420.0));
        assertTrue(item.placeBid("Carla", 480.0));

        assertEquals(480.0, item.getCurrentBid());
        assertEquals("Carla", item.getHighestBidder());
    }

    // TEST OFFERTE CASUALI

    @RepeatedTest(5)
    void testMultipleRandomBids() {
        AuctionItem item = randomItem();
        for (int i = 0; i < 3; i++) {
            double current = item.getCurrentBid();
            int minInc = item.getMinIncrement();
            double bidAmount = current + minInc + random.nextDouble() * 50;
            String bidder = "User" + random.nextInt(100);

            boolean accepted = item.placeBid(bidder, bidAmount);
            assertTrue(accepted, "Offerta casuale valida deve essere accettata");
            assertEquals(bidAmount, item.getCurrentBid());
            assertEquals(bidder, item.getHighestBidder());
        }
    }

    //  TEST TO STRING

    @Test
    void testToStringContainsKeyInfo() {
        AuctionItem item = new AuctionItem("Quadro", "Dipinto moderno", 150.0, 10);
        String output = item.toString();

        assertTrue(output.contains("Quadro"));
        assertTrue(output.contains("Dipinto moderno"));
        assertTrue(output.contains("150.0"));
        assertTrue(output.contains("Incremento minimo"));
    }

    // TEST CASI LIMITE

    @Test
    void testZeroStartingPriceAndIncrement() {
        AuctionItem item = new AuctionItem("Penna", "Penna economica", 0.0, 0);
        boolean accepted = item.placeBid("Paolo", 0.0); // 0 >= 0 + 0
        assertTrue(accepted, "Offerta di 0 su prezzo 0 con incremento 0 deve essere accettata");
        assertEquals(0.0, item.getCurrentBid());
    }

    @Test
    void testBidJustBelowMinimum() {
        AuctionItem item = new AuctionItem("Libro", "Libro antico", 50.0, 5);
        boolean accepted = item.placeBid("Giulia", 54.99); // appena sotto 55
        assertFalse(accepted, "Offerta appena sotto il minimo deve essere rifiutata");
    }

    @Test
    void testBidAfterAcceptanceStillHigherRequired() {
        AuctionItem item = new AuctionItem("Moneta", "Rara moneta d'oro", 500.0, 25);
        assertTrue(item.placeBid("Marco", 525.0));
        boolean accepted = item.placeBid("Elena", 530.0); // troppo basso
        assertFalse(accepted, "Offerta sotto il nuovo minimo deve essere rifiutata");
        assertEquals("Marco", item.getHighestBidder(), "highestBidder deve restare Marco");
    }
}
