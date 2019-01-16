
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

//implementazione interfaccia server 
@SuppressWarnings("serial")
public class ServerInterfaceImpl extends RemoteObject implements ServerInterface{

	private HashMap<String,String> clients;  //clienti registrati 
	private Charset charset = Charset.forName("ASCII"); //decoder caratteri letti
	//costruttore
	public ServerInterfaceImpl() {
		clients = new HashMap<String,String>();
		
		
		//acceddo alla cartella user (se esiste - altrimenti la creo)
		Path dirPathObj = Paths.get("../users");
		boolean dirExists = Files.exists(dirPathObj);
		if(dirExists) {
			//se esisto carico tutti i dati dal file nella struttura dati clients
			int red=0;
			String[] data=null;
			FileChannel inChannel=null;
			try {
				inChannel = FileChannel.open(Paths.get("../users/registered.txt"),StandardOpenOption.READ);
			} catch (IOException e) {
			
				e.printStackTrace();
			}
			ByteBuffer bytebuffer = ByteBuffer.allocateDirect(1); //buffer in lettura
			StringBuilder now = new StringBuilder();
			
			while(true) {
				
				//leggo carattere per carattere 			
				try {
					red = inChannel.read(bytebuffer);
				} catch (IOException e) {
					

					e.printStackTrace();
				}
				if(red==-1) {
					
					break;
				}
				else {
					
					bytebuffer.flip();
					now.append(charset.decode(bytebuffer));
					if( now.charAt(now.length()-1) == '\n') {
						CharSequence tmp =  now.subSequence(0, now.length()-1);
						data = tmp.toString().split(" ");
						clients.put(data[0], data[data.length-1]);
						now.delete(0, now.length()); //resetto la stringbuilder
						
					}
					bytebuffer.clear();//resetto il buffer
				}
			}
			System.out.println("Users already registered: "+clients.size());
			
			//qui ho finito di caricare tutti i dati dal file registered.txt -> server
		}
		else { //se la cartella non esiste la creo
			try {
				Files.createDirectories(dirPathObj);
				FileOutputStream fOut = new FileOutputStream("../users/registered.txt");
				fOut.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			
		}
	}
	//metodo per il client affinchè possa chiedere la registrazione al servizio
	public synchronized void registerUser(InterfaceClient client) throws RemoteException {
		System.out.println("Registering user: "+ client.getName()+" ...");
		//se il client non è presente nella struttura dati
		if(!clients.containsKey(client.getName())) {
			FileChannel inChannel=null;
			try {
				//ottengo un riferimento al file
				inChannel = FileChannel.open(Paths.get("../users/registered.txt"),StandardOpenOption.APPEND);
			} catch (IOException e) {
			
				e.printStackTrace();
			}
			//aggiunto il nuovo utente con relativa password
			ByteBuffer bytebuffer = ByteBuffer.wrap((client.getName()+" "+client.getPassword()+'\n').getBytes()); //buffer in lettura
			try {
				inChannel.write(bytebuffer);
				inChannel.close();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
			//aggiungo il client anche nella struttura dati
			clients.put(client.getName(), client.getPassword());
			sendMsg("Registered successfully", client); //notifico il client dell'avvenuta registrazione
			System.out.println("Registering user: "+ client.getName()+ " completed" );
			System.out.println("Users already registered: "+clients.size());
			
		}else {
			sendMsg("User already registered", client); //notifico il client che l'username è già in uso
			System.out.println("Registering user: "+ client.getName()+ " not completed" );
		}
		
	}
	@Override
	public synchronized void sendMsg(String s, InterfaceClient client) throws RemoteException {
		client.sendMessage(s);
		//uso l'interfaccia del client per inviare il messaggio
	}
	
	//eseguo un check per vedere se l'utente esiste tra i client registrati (per la login)
	public  synchronized boolean  isRegistered(String name, String password) throws RemoteException {
	
		Set<Entry<String, String>> set = clients.entrySet();
		Iterator<Entry<String, String>> iterator = set.iterator();
		Entry<String, String> tmp = null;
		if(!iterator.hasNext()) return false;
		while(true) {
			if(iterator.hasNext()) {
				tmp = iterator.next();
				if(tmp.getKey().equals(name) && tmp.getValue().equals(password)) return true;
			}
			else return false;
			
			
		}

	}
	//eseguo un check per vedere se l'utente esiste tra i client registrati
	public boolean checkUser(String username) throws RemoteException {
		Set<Entry<String, String>> set = clients.entrySet();
		Iterator<Entry<String, String>> iterator = set.iterator();
		Entry<String, String> tmp = null;
		if(!iterator.hasNext()) return false;
		while(true) {
			if(iterator.hasNext()) {
				tmp = iterator.next();
				if(tmp.getKey().equals(username)) return true;
			}
			else return false;
			
			
		}
	}
	
	


	
	

}
