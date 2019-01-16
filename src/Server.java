
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Server {
	
	

	private static Registry registry = null; //oggetto remoto server 
	static Charset charset = Charset.forName("ASCII"); //decoder caratteri letti
	static UserEditing user_editing = new UserEditing(); //utenti che stanno editando
	//ThreadPool ausiliario per gestire il server in multithread
	static ThreadPoolExecutor worker = (ThreadPoolExecutor) Executors.newFixedThreadPool(10); 
	//messaggi da inviare in multicast udp
	//utenti attualmente online
	static UserOnline user_online = new UserOnline();
	//inviare notifiche offline
	static OfflineMessage notify_share = new OfflineMessage();
	
	static Group_Multicast_UDP groups = new Group_Multicast_UDP();
	
	@SuppressWarnings("resource")
	public static void main(String[] args) {
		 
	
		
	    
		ServerSocket welcomeSocket = null; //socket che accetta le richieste da parte del server 
		
		System.out.println("Starting server...");
		int port=0; //porta remote_object_server
		int port2=0; //port for TCP
		String host=""; //ip TCP
		String ip = ""; //ip oggetto remoto
		try {
			ip  = args[0];
		}catch(Exception e) {
			System.out.println("Usage default host for object remote: localhost");
			ip="localhost";
		}
		try {
			port=Integer.parseInt(args[1]);
		}catch(Exception e) {
			System.out.println("Usage: java Client ip_remote_object_server port_remote_object_server ip_TCP port_TCP ip_UDP port_UDP port_notify 2");
			System.exit(0);
		}
		try {
			host=args[2];
		}catch(Exception e) {
			System.out.println("Usage default host for comunnication TCP: localhost");
			host="localhost";
		}
		try {
			port2=Integer.parseInt(args[3]);
		}catch(Exception e) {
			System.out.println("Usage: java Client ip_remote_object_server port_remote_object_server ip_TCP port_TCP ip_UDP port_UDP port_notify 4");
			System.exit(0);
		}
		  InetAddress addr = null;
			try {
				addr = InetAddress.getByName(host); //ottengo una InetAddress dell'"host"
			} catch (UnknownHostException e2) {
				e2.printStackTrace();
			}
		try {
			//creo un server alla porta=port2, con backlog 50, all'indirizzo ottenuto in precedenza
			welcomeSocket = new ServerSocket(port2,50,addr);
		
		} catch (IOException e1) {
			
			e1.printStackTrace();
		}
		
		
		
		
		System.out.println("Setting server...");
		//send message udp socket
		DatagramSocket serverSocket_UDP = null;  //socket per il multicast UDP
		InetSocketAddress address_UDP = null; //indirizzo per il multicast udp
		try {
			serverSocket_UDP = new DatagramSocket(null); //inizialmente non faccio la bind
		} catch (SocketException e1) {
		
			e1.printStackTrace();
		}
		//se sto lavorando il localhost
		if(host.equals("localhost") || host.equals("127.0.0.1")) {
			try {
				//ottengo un SocketAddress per il multicast UDP
				address_UDP = new InetSocketAddress(InetAddress.getLocalHost(), 5888);
			} catch (UnknownHostException e) {
				
				e.printStackTrace();
			}
		}else//altrimenti lo associo ottenendo una InetSocketAddress ed eseguo la bind
			
			address_UDP = new InetSocketAddress(host, 5888);
	    try {
			serverSocket_UDP.bind(address_UDP);
		} catch (SocketException e1) {
			
			e1.printStackTrace();
		}
	    
		//istanza dell'interfaccia server da esportare come oggetto remoto
		ServerInterfaceImpl server = new ServerInterfaceImpl(); 
		try {
			
		
			
			System.setProperty("java.rmi.server.hostname", ip);
			//esporto l'interfaccia server come oggetto remoto
			ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(server, 0);
			//creo un servizio che accetta richiesta sulla porta=port
			registry = LocateRegistry.createRegistry(port);
			//ottengo un riferimento all'oggetto remoto sulla porta=port
			//registry = LocateRegistry.getRegistry(ip, port);
			registry.rebind("rmi://"+ip+":"+port+"/RegisterUSER", stub);
					
				
			
		} catch (RemoteException e) {
			System.out.println("Remote exception");
			System.exit(0);
		} 
	
		System.out.println("Setting completed");
		System.out.println("Service RMI -> "+ip+":"+port);
		System.out.println("Listening connection TCP -> "+host+":"+port2);
		Socket connectionSocket=null; //socket per comunciazioni TCP col client 
		
		BufferedReader inFromClient=null; //lettura dal client -> server 
		DataOutputStream outToClient = null; //scrittura dal server -> client
		while(true) {
		
		
			try {
				connectionSocket=null;
				//accetto una nuova connessione
				connectionSocket = welcomeSocket.accept();
				//configuro gli stream subito dopo l'accept cosicchè son sicuro di utilizzarli nel modo corretto
				inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
				outToClient = new DataOutputStream(connectionSocket.getOutputStream());
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		
			try {
				 System.out.println("Accepted new request and getting data from client "+connectionSocket.getInetAddress().getHostAddress());
				
				//leggo dal client -> server
				String data="";
				StringBuilder tmp = new StringBuilder();;
		        while ( (data = inFromClient.readLine()) != null ) {
		            tmp.append(data);
		            if(inFromClient.ready() == false) break;
		        }
		    	
		   
		        data = tmp.toString();
		       String[] split = data.split(" ");
				//switch dei comandi da eseguire (lato server)
		      	//per quasi tutti i comandi esiste lo stesso thread (anch'esso ha uno switch al suo interno)
				switch(split[0]) {
				//per ogni comando starto un nuovo thread (rendendo così il server multithread)
					case "login":
							Tworker_server thread_login = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 0,user_online,user_editing,notify_share,groups);
							worker.execute(thread_login);
							
						break;
					case "logout":
						Tworker_server thread_logout = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 1,user_online,user_editing,notify_share,groups);
						worker.execute(thread_logout);
						System.out.println("Client "+connectionSocket.getInetAddress().getHostAddress()+" off");
						
						
						break;
					case "create":
						Tworker_server thread_create = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 2,user_online,user_editing,notify_share,groups);
						worker.execute(thread_create);
						
						break;
					case "show":
						Tworker_server thread_show = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 3,user_online,user_editing,notify_share,groups);
						worker.execute(thread_show);
						
						break;
					case "list":
						Tworker_server thread_list = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 4,user_online,user_editing,notify_share,groups);
						worker.execute(thread_list);
						break;
					case "edit":
						Tworker_server thread_edit = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 5,user_online,user_editing,notify_share,groups);
						worker.execute(thread_edit);
						break;
					case "end":
						Tworker_server thread_end_edit = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 6,user_online,user_editing,notify_share,groups);
						worker.execute(thread_end_edit);
						break;
					
					case "send":
						Tworker_server thread_send = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split, 7,user_online,user_editing,notify_share,groups);
						worker.execute(thread_send);
						break;
					
					case "share":
						Tworker_server thread_share = new Tworker_server(inFromClient, outToClient, connectionSocket, server, split,8 ,user_online,null,notify_share,groups);
						worker.execute(thread_share);
						break;
				}
				   
			} catch (IOException e) {
				e.printStackTrace();
			}
			   
		}
		
	}
	
	
}
