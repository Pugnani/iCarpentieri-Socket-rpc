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
                //aggiorniamo il Thread ogni 1s, sleep 1000ms
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }

                //lock per fare in modo che nessun altro possa interagire con la auction
                synchronized (lock) {
                    if (System.currentTimeMillis() - auctionStartTime >= 120000) {
                        //se l'asta ha superato i 2 min salviamo le informazioni di quest'ultima
                        String oldItemName = currentItem.getName();
                        String winningClient = currentItem.getHighestBidder();
                        double finalWinningPrice = currentItem.getCurrentBid();

                        //facciamo iniziare un nuovo timer per asta sul prossimo oggetto, sempre 
                        //avanti di 1 non random
                        int nextIndex = (getCurrentIndex() + 1) % auctionItems.length;
                        currentItem = auctionItems[nextIndex];
                        auctionStartTime = System.currentTimeMillis();

                        String msgPerTutti = "Ultimo oggetto \"" + oldItemName + "\" aggiudicato a " +
                                     (winningClient != null ? winningClient : "nessuno") +
                                     " per $" + finalWinningPrice + "\n" +
                                     "Nuovo oggetto in asta: \"" + currentItem.getName() + "\"";
                        Connection.broadcast(msgPerTutti); //usa broadcast per comunicare a ogni Client connesso
                        System.out.println("\n" + msgPerTutti); //Solo per il server
                    }
                }
            }
        }).start();
    }

    //semplice logica per vedere l'index dell' oggetto corrente
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
            //server inizia ad ascoltare su porta localhost
            int serverPort = 7896;
            ServerSocket listenSocket = new ServerSocket(serverPort);
            System.out.println("Server in ascolto sulla porta " + serverPort + "...");

            while (true) {
                Socket clientSocket = listenSocket.accept(); 
                //rimane bloccato qui (sopra) finche non viene creato nuovo client 

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
    private String nickNameClient = null;

    //apriamo una connection per ogni client(un Thread per client su connection)
    public Connection(Socket aClientSocket) {
        try {
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());

            //controlliamo che altri non interagiscano (aggiungono tolgono client) e aggiungiamo il nuovo client al server
            synchronized (TCPServer.clientsLock) {
                TCPServer.activeClients.add(this);
            }

            this.start();  // Avvia il thread

            // Invia benvenuto DOPO che il thread è partito
            //manda il comando /status senza che il client lo debba chiedere
            synchronized (TCPServer.lock) {
                String status = "Oggetto in asta: " + TCPServer.currentItem.getName() +
                               "\nPrezzo attuale: $" + TCPServer.currentItem.getCurrentBid() +
                               "\nIncremento minimo: $" + TCPServer.currentItem.getMinIncrement();
                out.writeUTF(status); //scrive solo al client in questione
            }

        } catch (IOException e) {
            System.out.println("Connection: " + e.getMessage());
        }
    }

    static void broadcast(String message) {
        synchronized (TCPServer.clientsLock) { //fa in modo che la lista client rimanga locked fino a fine processo
            List<Connection> clientToRemove = new ArrayList<>();
            for (Connection client : TCPServer.activeClients) {

                //scriviamo il messaggio in questione a ogni client, se uno crea un errore lo rimuoviamo dalla lista
                //lista di client attivi, potrebbe essere uscito senza fare /quit oppure crash etc

                try {
                    client.out.writeUTF(message);
                } catch (IOException e) {
                    clientToRemove.add(client);
                }
            }
            TCPServer.activeClients.removeAll(clientToRemove);
        }
    }

    @Override
    public void run() {

        //Thread che si occupa di "ascoltare" i messaggi del client in questione e gestire comandi ecc
        try {
            String messaggioClient;
            do {
                messaggioClient = in.readUTF(); //leggiamo l'input del client

                //se il client usa il comando nick lo impostiamo settando la sua variabile di classe
                if (messaggioClient.startsWith("/nick ")) {
                    this.nickNameClient = messaggioClient.substring(6).trim();
                    if (!nickNameClient.isEmpty()) {
                        out.writeUTF("Nickname impostato: " + this.nickNameClient);
                    } else { //se il client manda /nick senza un nome 
                        out.writeUTF("Nome non valido. Usa: /nick Mario");
                    }

                //se client usa comando /bid guardiamo se è valida e settiamo la bestBid     
                } else if (messaggioClient.startsWith("/bid ")) {
                    if (nickNameClient == null || nickNameClient.isEmpty()) { //serve un nick per fare la bid
                        out.writeUTF("Errore: Imposta prima il nickname con /nick <nome>");
                        continue;
                    }

                    String messaggioBid = messaggioClient.substring(5).trim();
                    try {
                        double bid = Double.parseDouble(messaggioBid);
                        
                        //evitiamo che vengano mandate /bid in contemporanea per esempio
                        synchronized (TCPServer.lock) {
                            long now = System.currentTimeMillis();
                            if (now - TCPServer.auctionStartTime >= 120000) {
                                //bid potrebbe essere arrivata mentre finiva la vecchia asta
                                out.writeUTF("Asta terminata! Nuovo oggetto: " + TCPServer.currentItem.getName());
                                continue;
                            }

                            if(bid >= 1000000000000.0) {
                                out.writeUTF("Prego offrire meno di $100-Miliardi");                   
                            }else if (TCPServer.currentItem.placeBid(nickNameClient, bid)) {
                                //se la bid è valida 
                                double newBestBid = TCPServer.currentItem.getCurrentBid();
                                out.writeUTF("Offerta accettata! $" + bid);
                                //messaggio da mandare a tutti
                                Connection.broadcast("Nuova migliore offerta: $" + newBestBid + " da " + nickNameClient);
                            } else {
                                //per fare in modo che non si possa rilanciare di 1ct c'è un minimo aumento da rispettare
                                double aumentoMinimo = TCPServer.currentItem.getCurrentBid() + TCPServer.currentItem.getMinIncrement();
                                out.writeUTF("Offerta troppo bassa! Minimo: $" + aumentoMinimo);
                            }
                        }
                    } catch (NumberFormatException e) {
                        out.writeUTF("Numero non valido. Usa: /bid 100");
                    }

                } else if (messaggioClient.equals("/status")) {
                    //tutti i dati importanti dell'asta/oggetto vengono scritti al client che li richiede
                    synchronized (TCPServer.lock) {
                        out.writeUTF("Stato asta:\n" + TCPServer.currentItem.toString());
                    }

                } else if (messaggioClient.equals("/quit")) {
                    //esce dal loop di lettura input e poi rimuove client dalla lista
                    out.writeUTF("Arrivederci!");
                    break;

                } else {
                    //per chattare normalmente, viene mandato come broadcast a tutti, solo se si ha un nick
                    if (nickNameClient != null && !nickNameClient.isEmpty()) {
                        String messaggioChat = "<" + nickNameClient + "> \"" + messaggioClient + "\"";
                        Connection.broadcast(messaggioChat);
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