import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//struttura dati per ricevere i messaggi in modo asincrono
public class MessageReceivedUDP {
	StringBuilder received = null; //chat multicast di un gruppo
	Lock lock; //mutua esclusione
	
	//costruttore
	public MessageReceivedUDP() {
		received= new StringBuilder();
		lock = new ReentrantLock();
	}
	//inserisce il messaggio passato come stringa (messaggio multicast ricevuto in Tread_UDP_Client)
	public void insertMessage(String message) {
		lock.lock();
		received.append(message+"\n");
		lock.unlock();
	}
	//ottengo tutta la chat di un gruppo
	public String getMessage() {
		String to_return="";
		lock.lock();
		if(received.toString().equals("")) { //se non ho ricevuto nulla lo avviso
			to_return="No message\n";
		}
		else { //altrimenti restituisco la chat
			to_return=received.toString();
		}
		lock.unlock();
		return to_return;
	}
	//ripristino lo stato iniziale dell'istanza della classe
	public void deleteAllMessage() {
		lock.lock();
		received.delete(0, received.length());
		lock.unlock();
		
	}
	
}
