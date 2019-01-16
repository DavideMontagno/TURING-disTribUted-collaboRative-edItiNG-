import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;


//questo thread notifica ad un particolare username tutti i documenti "shared" da parte degli utenti
//al momento della login (quindi tutti le notifiche offline)
public class TloginNotify implements Runnable{

	OfflineMessage send_notify =null; //struttura dati che contiene tutti i messaggi d'invito al gruppo
	UserOnline users=null; //struttura dati degli utenti online
	String username=null; //username uttente corrente
	public TloginNotify(OfflineMessage send_notify, UserOnline users, String username) {
		this.send_notify = send_notify; 
		this.users=users;
		this.username=username;
	}

	@Override
	public void run() {
		String address = users.getAddress(username); //ottengo dagli utenti online l'indirizzo e la portra
		String[] communication = address.split(":");
		Socket clientSocket=null;
		try {
			//creo una socket verso tale indirizzo e porta
			clientSocket = new Socket(communication[0], Integer.parseInt(communication[1]));
		} catch (UnknownHostException e2) {
			
			e2.printStackTrace();
		} catch (IOException e2) {
		
			e2.printStackTrace();
		}
		DataOutputStream outToClient = null;
		try {
			//configuro gli stream ed invio i dati relativi ad un username 
			outToClient= new DataOutputStream(clientSocket.getOutputStream());
			outToClient.writeBytes(send_notify.getMessage(username)+"\n");
			clientSocket.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
	}
	
}
