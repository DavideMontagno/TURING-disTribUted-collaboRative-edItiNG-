import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;


//questo thread viene attivato al momento della login per ricevere tutte le notifiche offline sull'editing di un documento
public class Tnotify_client implements Runnable{
	
	private static Socket clientSocket;
	private static BufferedReader inFromServer;
	private ServerSocket notifySocket = null;

	
	
	//costruttore
	public Tnotify_client(int port, String address) {
		try {
			//viene creata una socket per poter ricevere le notifiche
			this.notifySocket = new ServerSocket(port,50,InetAddress.getByName(address));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	
	public void run() {
		System.out.println("Listening notify on: "+notifySocket.getInetAddress().getHostAddress()+":"+notifySocket.getLocalPort());
		while(true) {
			try {
				//ogni qual volta il server contatta la notify socket
				clientSocket = notifySocket .accept();
				//configuro il thread per la lettura
				inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			
			} catch (IOException e) {
				e.printStackTrace();
			}
			//leggo i dati dal server -> thread_client
			String data="";
			StringBuilder tmp = new StringBuilder();;
	        try {
				while ( (data = inFromServer.readLine()) != null ) {
				    tmp.append(data);
				    if(inFromServer.ready() == false) break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
	       //una volta letti li ricevo
	       System.out.println("Server -> "+tmp.toString());
	       try {
			clientSocket.close();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		}
	}
	
}
