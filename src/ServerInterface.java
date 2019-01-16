import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;


//Interfaccia del server
public interface ServerInterface extends Remote {
	//registro la callback da parte del client
	public void registerUser(InterfaceClient client) throws RemoteException;
	//deregistro la callback da parte del client
	public void sendMsg(String s, InterfaceClient client) throws RemoteException;
	//check se un utente è registrato o no
	public boolean isRegistered(String name, String password) throws RemoteException;
	//controllo che l'utente esista
	public boolean checkUser(String username) throws RemoteException;
}
