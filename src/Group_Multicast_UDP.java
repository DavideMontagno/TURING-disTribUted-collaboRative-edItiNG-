import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//classe di supporto che tiene traccia del nome del gruppo e del relativo indirizzo multicast
public class Group_Multicast_UDP {
	//struttura dati che consente di realizzare il binding (nome_gruppo,ip)
	HashMap <String,String> multicast_document = null; 
	Lock lock; //mutua esclusione
	int second_part=0; //necessaria per assegnare un indirizzo multicast ad un gruppo senza conflitti
	
	//costruttore
	public Group_Multicast_UDP() {
		multicast_document = new HashMap<String,String>();
		lock = new ReentrantLock();
		
	}
	
	//crea un nuovo gruppo
	public String createNewGroup(String document) {
		String to_return="";
		lock.lock();
		if(multicast_document.containsKey(document)) { //se già esiste
			to_return = multicast_document.get(document); //restituisco il suo ip multicast
			lock.unlock();
			return to_return;
		}
		else {
			 //altrimenti genero un nuovo indirizzo ip multicast
			if(second_part==255) {
				to_return="224.0.1.255";
				multicast_document.put(document, to_return);
				
				second_part=0;
			}else {
				to_return="224.0.1."+second_part;
				multicast_document.put(document, to_return);
				second_part++;
			}
			lock.unlock();
			System.out.println("Created new multicast group at address: "+to_return);
			return to_return; // restituisco il nuovo indirizzo multicast associato ad un gruppo
		}
	}
	
	
	public void removeGroup(String document) {
		String ip="";
		lock.lock();
		ip = multicast_document.get(document);
			multicast_document.remove(document);
		lock.unlock();

		System.out.println("Cancelled multicast group at address: "+ip);
	}
	
	
	public String getAddress(String document) {
		String to_return="";
		lock.lock();
			to_return = multicast_document.get(document);
		lock.unlock();
		return to_return;
	}
	
	
	
}
