import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class TCPServer {

    static final List<Connection> activeClients = new ArrayList<>();
    static final Object clientsLock = new Object();

    private static final AuctionItem[] auctionItems = {
        new AuctionItem("Lamp", "Lampada da tavolo vintage in ottone", 20.0, 3),
        new AuctionItem("MacBook", "MacBook Pro 13\" 2020, M1, 8GB RAM", 800.0, 50),
        new AuctionItem("Rolex", "Rolex Submariner Date 41mm, acciaio", 5000.0, 100)
    };

    static AuctionItem currentItem;
    static long auctionStartTime;
    static final Object lock = new Object();

    public static void main(String[] args) {
        currentItem = auctionItems[0];
        auctionStartTime = System.currentTimeMillis();
        startAuctionTimer();
        startServer();
    }

    private static void startAuctionTimer() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }

                synchronized (lock) {
                    if (System.currentTimeMillis() - auctionStartTime >= 120000) {
                        String oldName = currentItem.getName();
                        String winner = currentItem.getHighestBidder();
                        double finalPrice = currentItem.getCurrentBid();

                        int nextIndex = (getCurrentIndex() + 1) % auctionItems.length;
                        currentItem = auctionItems[nextIndex];
                        auctionStartTime = System.currentTimeMillis();

                        String msg = "Ultimo oggetto \"" + oldName + "\" aggiudicato a " +
                                     (winner != null ? winner : "nessuno") +
                                     " per $" + finalPrice + "\n" +
                                     "Nuovo oggetto in asta: \"" + currentItem.getName() + "\"";
                        Connection.broadcast(msg);
                        System.out.println("\n" + msg);
                    }
                }
            }
        }).start();
    }

    private static int getCurrentIndex() {
        for (int i = 0; i < auctionItems.length; i++) {
            if (auctionItems[i].getName().equals(currentItem.getName())) {
                return i;
            }
        }
        return 0;
    }

    private static void startServer() {
        try {
            int serverPort = 7896;
            ServerSocket listenSocket = new ServerSocket(serverPort);
            System.out.println("Server in ascolto sulla porta " + serverPort + "...");

            while (true) {
                Socket clientSocket = listenSocket.accept();
                new Connection(clientSocket);
            }
        } catch (IOException e) {
            System.out.println("Listen: " + e.getMessage());
        }
    }
}

class Connection extends Thread {
    private DataInputStream in;
    private DataOutputStream out;
    private Socket clientSocket;
    private String nickName = null;

    public Connection(Socket aClientSocket) {
        try {
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());

            synchronized (TCPServer.clientsLock) {
                TCPServer.activeClients.add(this);
            }

            this.start();  // Avvia il thread

            // Invia benvenuto DOPO che il thread è partito
            synchronized (TCPServer.lock) {
                String status = "Oggetto in asta: " + TCPServer.currentItem.getName() +
                               "\nPrezzo attuale: $" + TCPServer.currentItem.getCurrentBid() +
                               "\nIncremento minimo: $" + TCPServer.currentItem.getMinIncrement();
                out.writeUTF(status);
            }

        } catch (IOException e) {
            System.out.println("Connection: " + e.getMessage());
        }
    }

    static void broadcast(String message) {
        synchronized (TCPServer.clientsLock) {
            List<Connection> toRemove = new ArrayList<>();
            for (Connection client : TCPServer.activeClients) {
                try {
                    client.out.writeUTF(message);
                } catch (IOException e) {
                    toRemove.add(client);
                }
            }
            TCPServer.activeClients.removeAll(toRemove);
        }
    }

    @Override
    public void run() {
        try {
            String data;
            do {
                data = in.readUTF();

                if (data.startsWith("/nick ")) {
                    this.nickName = data.substring(6).trim();
                    if (!nickName.isEmpty()) {
                        out.writeUTF("Nickname impostato: " + this.nickName);
                    } else {
                        out.writeUTF("Nome non valido. Usa: /nick Mario");
                    }

                } else if (data.startsWith("/bid ")) {
                    if (nickName == null || nickName.isEmpty()) {
                        out.writeUTF("Errore: Imposta prima il nickname con /nick <nome>");
                        continue;
                    }

                    String bidStr = data.substring(5).trim();
                    try {
                        double bid = Double.parseDouble(bidStr);

                        synchronized (TCPServer.lock) {
                            long now = System.currentTimeMillis();
                            if (now - TCPServer.auctionStartTime >= 120000) {
                                out.writeUTF("Asta terminata! Nuovo oggetto: " + TCPServer.currentItem.getName());
                                continue;
                            }

                            if (TCPServer.currentItem.placeBid(nickName, bid)) {
                                double newPrice = TCPServer.currentItem.getCurrentBid();
                                out.writeUTF("Offerta accettata! $" + bid);
                                Connection.broadcast("Nuova migliore offerta: $" + newPrice + " da " + nickName);
                            } else {
                                double minBid = TCPServer.currentItem.getCurrentBid() + TCPServer.currentItem.getMinIncrement();
                                out.writeUTF("Offerta troppo bassa! Minimo: $" + minBid);
                            }
                        }
                    } catch (NumberFormatException e) {
                        out.writeUTF("Numero non valido. Usa: /bid 100");
                    }

                } else if (data.equals("/status")) {
                    synchronized (TCPServer.lock) {
                        out.writeUTF("Stato asta:\n" + TCPServer.currentItem.toString());
                    }

                } else if (data.equals("/quit")) {
                    out.writeUTF("Arrivederci!");
                    break;

                } else {
                    if (nickName != null && !nickName.isEmpty()) {
                        String chatMsg = "<" + nickName + "> \"" + data + "\"";
                        Connection.broadcast(chatMsg);
                    } else {
                        out.writeUTF("Errore: imposta nickname prima di chattare");
                    }
                }

            } while (true);

        } catch (EOFException e) {
            System.out.println("Client disconnesso (EOF)");
        } catch (IOException e) {
            System.out.println("IO error: " + e.getMessage());
        } finally {
            synchronized (TCPServer.clientsLock) {
                TCPServer.activeClients.remove(this);
            }
            try { if (clientSocket != null) clientSocket.close(); }
            catch (IOException e) { /* ignore */ }
        }
    }
}