import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;

public class Tread_UDP_Client implements Runnable {
	MulticastSocket s=null; //socket multicast per la comunicazione
	 MessageReceivedUDP struct_message = null; //struttura condivisa per ricevere messaggi asincontri
	boolean stop=true; //variabile d'appoggio per leggere indefinitivamente messaggi multicast
	String ip=null;
	
	//costruttore
	public Tread_UDP_Client(String ip, MulticastSocket s, MessageReceivedUDP struct_message) {
		this.s=s;
		this.struct_message = struct_message;
		this.ip=ip;
	}
	
	//metodo principale
	public void run() {
		System.out.println("Starting listen message chat from: "+ip);
		while(stop) {
			byte buf[] = new byte[1024]; //buffer per la ricezione
			//creo un datagrampacket in ricezione
			DatagramPacket pack = new DatagramPacket(buf, buf.length);

			try {
				//aspetto nella receive che il server mandi un messaggio multicast
				s.receive(pack);
			} catch (IOException e) {
				//se viene interrotto bruscamente (fatto dall'end - edit del client)
				System.out.println("Listening chat has been stopped");
				break;
			}
			//inserisco il messaggio ricevuto
			struct_message.insertMessage(new String(pack.getData(),0,pack.getLength()));
		}
		
		
	}
	
	public void stop() { //stoppo il thread corrente
		this.stop=false;
		//riporto allo stato iniziale la struttura dati che tiene traccia dei messaggi multicast
		struct_message.deleteAllMessage(); 
	}
}
