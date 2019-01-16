import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OfflineMessage {
	//notifiche di invito di un utente all'editing di un nuovo documento
	static HashMap <String, String> offline_message = null;
	Lock lock; //mutua esclusione
	
	//costruttore
	public OfflineMessage() {
		this.offline_message = new HashMap<String,String>();
		this.lock = new ReentrantLock();
	}
	
	//inserisco l'username e la password
	public void put(String username, String doc) {
		
		lock.lock(); //accedo in mutua esclusione 
		//aggiungo il nome del documento concatenandolo a quelli precedenti
		if(offline_message.get(username)==null) {
			offline_message.put(username,doc); 
		}
		if(!offline_message.get(username).contains(doc.toString()))
			offline_message.put(username, offline_message.get(username)+","+doc); 
		lock.unlock();
	}
	//ottengo tutti i messaggi offline (verifica del corretto funzionamento da parte del server)
	public String toString() {
		String to_return;
		lock.lock();
		to_return = offline_message.toString();
		lock.unlock();
		return to_return;
	}
	
	//ottengo le notifiche offline per un dato utente
	public String getMessage(String username) {
		String to_return;
		lock.lock();
		if(offline_message.get(username)==null) //nessuna notifica
			to_return="You have not notify";
		else { //ottengo la lista dei documenti da editare (notifica offline) e rimuovo l'utente
			to_return="You have been invited to edit: "+offline_message.get(username);
			offline_message.remove(username);
		}
			
		lock.unlock();
		return to_return;
	}
}
