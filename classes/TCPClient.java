import java.net.*;
import java.io.*;
import  java.util.*;

public class TCPClient { 

    public static void main (String args[]) { 
	// arguments supply message and hostname of destination
	Socket s = null; 

	try{ 
	    int serverPort = 7896;
	    s = new Socket(args[1], serverPort);
        String data= "a";
        DataInputStream in = new DataInputStream( s.getInputStream());
        DataOutputStream out = new DataOutputStream( s.getOutputStream());
        Scanner keyboard = new Scanner(System.in);
        String message = "";
        do {
            System.out.print("Inserisci messaggio (/quit per uscire): ");
            message = keyboard.nextLine();
            out.writeUTF(message);
            data = in.readUTF();
            System.out.println("Ricevuto: " + data);
        } while(!(message.equals("/quit")));
	} catch (UnknownHostException e){ 
	    System.out.println("Sock: "+e.getMessage());
	} catch (EOFException e){System.out.println("EOF: "+e.getMessage()); 
	} catch (IOException e){System.out.println("IO: "+e.getMessage());
	} finally {if(s!=null)
		try {s.close();
		} catch (IOException e) {/*close failed*/}}
    }
}