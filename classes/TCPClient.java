// TCPClient.java
import java.net.*;
import java.io.*;
import java.util.*;

public class TCPClient {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java TCPClient <hostname>");
            return;
        }

        String host = args[0];
        int port = 7896;
        Socket socket = null;

        try {
            socket = new Socket(host, port);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Thread lettore: riceve TUTTO in tempo reale
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = in.readUTF();
                        System.out.println("\n" + msg);
                        System.out.print("> ");
                    }
                } catch (EOFException e) {
                    System.out.println("\n[Server disconnesso]");
                } catch (IOException e) {
                    System.out.println("\n[Errore lettura]");
                } finally {
                    System.exit(0);
                }
            }).start();

            // Loop input
            Scanner scanner = new Scanner(System.in);
            String input;
            while (true) {
                System.out.print("> ");
                input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                out.writeUTF(input);

                if (input.equalsIgnoreCase("/quit")) {
                    break;
                }
            }

        } catch (UnknownHostException e) {
            System.out.println("Host sconosciuto: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Errore I/O: " + e.getMessage());
        } finally {
            try { if (socket != null) socket.close(); }
            catch (IOException e) { System.out.println("Errore chiusura"); }
        }
    }
}