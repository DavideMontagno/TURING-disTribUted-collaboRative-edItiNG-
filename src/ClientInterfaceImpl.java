import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;


//interfaccia del client
public class ClientInterfaceImpl  extends RemoteObject implements InterfaceClient{

	int i=0; //numero di messaggi ricevuti
	String username; //username utente
	String password; //password utente
	
	//costruttore
	public ClientInterfaceImpl(String string, String string2) {
		this.username=string;
		this.password=string2;
		try {
			//nella classe client veniva generato uno stub da passare alla register del server
			//dunque esporto l'oggetto remoto
			UnicastRemoteObject.exportObject(this,0); 
			
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	//ricezione dei dati da parte del server
	public  synchronized void sendMessage(String s) throws RemoteException {
		System.out.println("Server -> "+s);
		
	}
	//ottengo il nome
	public  synchronized String getName() {
		return ""+username;
	}
	//ottengo la password
	public  synchronized String getPassword() {
		return ""+password;
	}
	//verifica username password
	public  synchronized boolean checkPermission(String username, String password) {
		return (this.username.equals(username) && this.password.equals(password));
	}
	
}
