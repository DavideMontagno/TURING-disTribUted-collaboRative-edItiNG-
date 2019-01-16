import java.rmi.Remote;
import java.rmi.RemoteException;


//Interfaccia client
public interface InterfaceClient extends Remote {
	//chiamata dal Server per notificare un messaggio
	public void sendMessage(String s) throws RemoteException;
	//ottenere lo username dal Client per il server
	public String getName() throws RemoteException;
	public String getPassword() throws RemoteException;
	//controllo dei parametri per la fase di login
	public boolean checkPermission(String username, String password) throws RemoteException;

}
