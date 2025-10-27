public class AuctionItem {
    private final String name;          // nome dell'oggetto
    private final String description;   // descrizione
    private final double startingPrice; // prezzo di partenza
    private final int minIncrement;     // incremento minimo
    private double currentBid;          // offerta attuale
    private String highestBidder;       // nome dell'offerente con l'offerta più alta

    //  Costruttore
    public AuctionItem(String name, String description, double startingPrice, int minIncrement) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.minIncrement = minIncrement;
        this.currentBid = startingPrice;
        this.highestBidder = null;
    }

    // Getter
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public int getMinIncrement() {
        return minIncrement;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public String getHighestBidder() {
        return highestBidder;
    }

    // Metodo per fare un'offerta
    public boolean placeBid(String bidderName, double bidAmount) {
        double minAllowedBid = currentBid + minIncrement;
        if (bidAmount >= minAllowedBid) {
            currentBid = bidAmount;
            highestBidder = bidderName;
            return true; // offerta accettata
        } else {
            System.out.println("Offerta troppo bassa! L'offerta minima accettabile è " + minAllowedBid);
            return false; // offerta non valida
        }
    }

    // Metodo per mostrare le info
    @Override
    public String toString() {
        return "Oggetto: " + name +
                "\nDescrizione: " + description +
                "\nPrezzo di partenza: " + startingPrice +
                "\nIncremento minimo: " + minIncrement +
                "\nPrezzo attuale: " + currentBid +
                (highestBidder != null ? " (offerto da " + highestBidder + ")" : "");
    }
}
