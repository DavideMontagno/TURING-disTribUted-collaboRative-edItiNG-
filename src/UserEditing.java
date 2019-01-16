import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UserEditing {
	static HashMap <String, String> user_editing; //struttura dati per tenere traccia di chi sta editanto
	Lock lock_structure; //necessaria per la mutua esclusione
	
	
	//costruttore
	public UserEditing() {
		
		this.user_editing = new HashMap<String,String>();
		lock_structure = new ReentrantLock();
	}
	
	//un nuovo utente sta editando la sezione del documento
	public int editDocument(String username, String section) {
		
		lock_structure.lock();
		if(user_editing.containsKey(username)) { //se già esiste allora l'utente sta già editando
			lock_structure.unlock();
			return 304;
		}
		else {
			if(user_editing.containsValue(section)) { //se la sezione esiste allora qualcuno la sta modificando già
				lock_structure.unlock();
				return 305;
			}
			else { //l'utente può modificare la sezione che ha scelto
				lock_structure.unlock();
				user_editing.put(username, section);
				System.out.println("Users editing: "+user_editing.toString());
				return 202;
			}
		}
	}
	
	
	//end editing del documento
	public int removedDocument(String username, String section) {
		lock_structure.lock();
		if(user_editing.containsKey(username)) {
			if(user_editing.get(username).equals(section)) {
				user_editing.remove(username);
				lock_structure.unlock();
				return 203; //rimozione avvenuta con successo 
			}
				
			else {
				lock_structure.unlock();
				return 307; //sta editando una sezione diversa da quella richiesta
			}
		}
		else {
			lock_structure.unlock();
			return 306; //non sta editando alcun documento
		}
	}
	
	public boolean checkDocumentEditing(String document) {
		lock_structure.lock();
		for (Map.Entry<String, String> entry : user_editing.entrySet())
		{	
			String[] data = entry.getValue().split("/");
			System.out.println("Nome: "+data[1]);
			if(data[0].equals(document)) {
				lock_structure.unlock();
				return true;
			}
		}
		lock_structure.unlock();
		return false;
	}
	
}
