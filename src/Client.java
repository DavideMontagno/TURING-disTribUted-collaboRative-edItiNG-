import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Client {
		
	private static Registry registry = null;
	
	private static DataOutputStream outToServer=null; //stream da client -> server
	private static BufferedReader inFromServer=null;//stream da server -> client
	private static Socket clientSocket=null; //socket verso il server
	private static String file_editing=null; //file che l'utente edita (per la chat UDP).
	//necessario per ricevere notifiche nel caso un client condivide un documento per l'editing.
	static ThreadPoolExecutor worker = (ThreadPoolExecutor) Executors.newFixedThreadPool(2); 
	static MessageReceivedUDP message=null;
	public static void main(String args[]) {
		int port=0; //porta remote_object_server
		int port2=0; //port for TCP
		int port3 = 0; //port notify shared document
		int port_udp=0;
		String ip_multicast = "";
		boolean logged=false; //check se utente loggato o no
		boolean editing=false; //check utente se sta editando o no
		MulticastSocket s=null;
		String user = ""; //utente (""=non loggato, "name"=se loggato).
		String host=""; //ip necessario alla comunicazione TCP tra client e server
		
		String ip = ""; //ip necessario per condivedere l'oggetto remoto
		long time_edit = 0;
		//Getting parametri.
		try {
			ip  = args[0];
		}catch(Exception e) {
			System.out.println("Usage default host for object remote: localhost");
			ip="localhost";
		}
		try {
			port=Integer.parseInt(args[1]);
		}catch(Exception e) {
			System.out.println("Usage: java Client ip_remote_object_server port_remote_object_server ip_TCP port_TCP ip_UDP port_notify 2");
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
			System.out.println("Usage: java Client ip_remote_object_server port_remote_object_server ip_TCP port_TCP  port_notify 4");
			System.exit(0);
		}
	
		try {
			port3=Integer.parseInt(args[4]);
		}catch(Exception e) {
			System.out.println("Usage: java Client ip_remote_object_server port_remote_object_server ip_TCP port_TCP port_notify 5");
			System.exit(0);
		}
		System.out.println("Connecting to server...");

		//Buffer per la lettura dei comandi da tastiera
		BufferedReader in = new BufferedReader (new InputStreamReader(System.in));
		String choice = "";
		
		ServerInterface server = null; //interfaccia server
		
		InterfaceClient stub = null; //interfaccia client
		
		
	
		try {
			
			registry = LocateRegistry.getRegistry(ip,port); //ottengo il servizio
		} catch (RemoteException e) {
			System.out.println("Remote exception ");
			System.exit(0);
		}
		try {
			//ottengo l'oggetto remoto
			server = (ServerInterface) registry.lookup("rmi://"+ip+":"+port+"/RegisterUSER"); 
		} catch (AccessException e) {
			System.out.println("Access Exception");
			System.exit(0);
		} catch (RemoteException e) {
			System.out.println("Remote Exception");
			System.exit(0);
		} catch (NotBoundException e) {
			System.out.println("NotBound Exception");
			System.exit(0);
		}
	
		System.out.println("Connection completed");
		boolean stop=true; //ciclo infinito (life cycle del thread client). 
		
		  
		Tread_UDP_Client thread_receiver = null;
		Tnotify_client thread_notify = new Tnotify_client(port3, host); //thread per le notifiche di shared document
		worker.execute(thread_notify); //esecuzione del thread da parte del thread worker
		while(stop) {
			
			try {
				choice = in.readLine(); //lettura comando dal client
				
			} catch (IOException e) {
				System.err.println("Ops.. qualcosa è andato storto!");
				System.exit(1);
			}
			
			String[] splitted = (choice.toLowerCase()).split(" "); //split per il parsing dei parametri.
			if(splitted.length==1 && splitted[0].equals("turing")==false) {
				System.err.println("Usage: turing --help");
			}else {
				switch(splitted[1]) { //switch dei comandi
				
				case "--help": //done it
					System.out.println("usage : turing COMMAND [ ARGS ...]\n");
					System.out.println("commands:");
					System.out.println("register < username > < password > registra l ’ utente"); 
					System.out.println("login < username > < password > effettua il login"); 
					System.out.println("logout"); 
					System.out.println("create <doc > < numsezioni > crea un documento"); 
					System.out.println("share <doc > < username > condivide il documento"); 
					System.out.println("show <doc > <sec > mostra una sezione del documento"); 
					System.out.println("show <doc > mostra l ’ intero documento"); 
					System.out.println("list mostra la lista dei documenti");
					System.out.println("edit <doc > <sec > modifica una sezione del documento"); 
					System.out.println("end - edit <doc > <sec > fine modifica della sezione del doc .");
					System.out.println("send <msg> invia un msg sulla chat");
					System.out.println("receive visualizza i msg ricevuti sulla chat");
					break;
				case "register": 	//done it
					if(logged) { //se loggato non può registrare altri client
						System.err.println("Sorry you are logger, usage first: logout");
						break;
					}
					if(splitted.length!=4) { //check dei parametri
						System.err.println("Usage: turing register < username > < password >");
						break;
					}
				
					
					try {
						//ottengo un nuovo oggetto remoto (InterfaceClient)
						stub = (InterfaceClient) new ClientInterfaceImpl(splitted[2],splitted[3]);
						//registro l'utente
						server.registerUser(stub);
					} catch (RemoteException e) {
						System.out.println("Remote Exception on registering "+e.toString());
						System.exit(0);
					}
				
					break;
				case "login": // done it - lato client
					if(logged) { //se l'utente è loggato non può eseguire un'altra login
						System.out.println("Client already logged");
					}else {
						if(splitted.length!=4) { //check dei parametri
							System.err.println("Usage: turing login < username > < password >");
							break;
						}
						setCommunication(port2, host); //metodo per la configurazione della connessione TCP verso il server
						try {
								//invio informazioni al server
							  outToServer.writeBytes("login"+" "+splitted[2]+" "+splitted[3]+" "+port3+"\n");
							 
							  	//leggo risposta da parte del server
								StringBuilder tmp =  readFromServer(inFromServer);
							  try{
								 
								//Code 200 = tutto ok.
								  if(tmp.toString().contains("200")) {
									  user = splitted[2]; //memorizzo l'username con il quale ho fatto la richiesta
									  logged=true; //segno che l'utente è loggato.
								  }
							  }catch(Exception e) {
								  
							  }
							  //stampa del messaggio ottenuta dal server
							  System.out.print("Server -> " +  tmp.toString());
							  //chiudo la comunicazione TCP poichè ho finito
							  clientSocket.close();
							  
							  
						}catch(Exception e) {
							System.err.println("Ops.. Qualcosa è andato storto!");
						}
					}
					  
					
					break;
				case "logout": //done it 
					if(editing==true) { //se sto editando non è possibile eseguire la logout
						System.err.println("You cannot logout, you are currently editing");
						break;
					}
					if(logged) { //se son loggato
						try {
							setCommunication(port2, host); //configuro la comunicazione verso il server
								//invio informazioni al server
							 outToServer.writeBytes("logout "+user+" "+port3+"\n");
							 clientSocket.shutdownOutput(); //informo il server che ho finito di mandare dati
							  System.out.print("Server -> " + readFromServer(inFromServer).toString()); //leggo la risposta
							  logged=false; //eseguo la logout
							  user=""; //reimposto lo username
								//setto a null le variabili sottostanti per il garbage collector
							  server = null;
								registry = null;
								//chiudo la connessione verso il server esplicitamente
								outToServer.close();
								inFromServer.close();
							  clientSocket.close();
							  System.exit(0);
						} catch (IOException e) {
							System.err.println("Ops.. Qualcosa è andato storto!");
							System.exit(1);
						}
					}
					else {//se tutto è andato bene termino il thread.
						System.out.println("Logout successfully"); 
						System.exit(0);
					}
				
				
					break;
				case "create": //done it
					if(logged==false) { //se non è loggato non può creare un nuovo documento
						System.out.println("User not logged");
					}
					if(editing) { //se sta editando non può creare un nuovo documento
						System.err.println("You cannot create if you are editing");
						break;
					}
					else {
						if(splitted.length!=4) { //check dei parametri
							System.err.println("Usage: turing create <doc > < numsezioni >");
							break;
						}
						try {

							setCommunication(port2, host); //configuro la connessione TCP verso il server
							//invio informazioni al server
							outToServer.writeBytes("create "+splitted[2]+" "+ splitted[3]+" "+user+"\n");
							//leggo risposta dal server
							  System.out.print("Server -> " + readFromServer(inFromServer).toString());
							  //chiusura esplicita della connessione
							  clientSocket.close();
						} catch (IOException e) {
							
						}
					}
					
					
					break;
				case "show": //done - it
					
					//variabili d'appoggio per mostrare il documento
					boolean not_found=false; 
					boolean auth=true;
					
					
					if(!logged) { //se non è loggato non può vedere documenti
						System.out.println("User not logged");
						break;
					}
					if(editing) { //se sta editando non può vedere documenti
						System.err.println("You cannot see document if you are editing");
						break;
					}
					
					else {
						if(splitted.length==4) {
							
							setCommunication(port2, host); //configuro la comunicazione TCP verso il server
							
							try {
								//invio informazioni al server
								outToServer.writeBytes("show "+splitted[2]+" "+ splitted[3]+" "+user+"\n");
								 
									 String data="";
									StringBuilder tmp = new StringBuilder();;
									while ( true) {
										//leggo risposta dal server
									 	data =   readFromServer(inFromServer).toString();
									 	
									 	//Code 301-300: codici di errore
									 	if(data.contains("301")) {
									 		auth=false;
									 		System.out.println("You have not permissione for file -> "+splitted[2] +".txt ");
									 		break;
									 	}
									 	if(data.contains("300")) {
									 		not_found=true;
									 		System.out.println("Server doesn't have file -> "+splitted[2] +".txt with section: "+splitted[3]);
									 		break;
									 	}
									 	//code 201: fine sezione
									 	if(data.contains("201")) {
									 		
									 		tmp.append("\n---------------------\n"); 
									 	}
									 	//fine lettura del documento
									 	else if(data.contains("202")) break;
									 	else { tmp.append(data);   } //aggiungo la linea del file parsata dal server
							          
							        }
									 //resetto le variabili d'appoggio
									if(auth==false) { 
										 auth=true;
										
										 break;
									 }
									 else if(not_found) {
										 not_found=false;
										 
										 break;
									 }
									//eseguo la stampa delle informazioni inviate dal server
									 if(tmp.length()>0) 
									  System.out.println("Server send file -> "+splitted[2] +".txt showing section: "+splitted[3]+'\n'+ tmp.substring(0, tmp.length()-1).toString());
									 else 
										 System.out.println("Server send file -> "+splitted[2] +".txt showing section: "+splitted[3]+'\n'+ "Empty Section");
										 
									  clientSocket.close(); //chiusura esplicita della connessione
							} catch (IOException e) {
								
								e.printStackTrace();
							}
						
							
						}
						//leggo tutto il documento
						else if(splitted.length==3) {
							setCommunication(port2, host); //configuro la connessione
							
							
							
							try {
								//invio informazioni al server
								outToServer.writeBytes("show "+splitted[2]+" "+user+"\n");
								//eseguo gli stessi passi fatti in precedenza per la lettura di una sezione
								 String data="";
									StringBuilder tmp = new StringBuilder();;
									 while ( true) {
										 	data =   readFromServer(inFromServer).toString();
										 	if(data.contains("301")) {
										 		//non ho i permessi per il file
										 		auth=false;
										 		break;
										 	}
										 	if(data.contains("300")) {
										 		//non ho trovato il file
										 		not_found = true;
										 		break;
										 	}
										 	if(data.contains("201")) {
										 		//ho finito una sessione
										 		tmp.append("\n---------------------\n"); 
										 	}
										 	//ho finito di leggere l'ultima o unica sessione
										 	else if(data.contains("202")) break;
										 	//continuo a leggere
										 	else { tmp.append(data);   }
								       }
									 if(auth==false) {
										 auth=true;
										 System.out.println("You have not permissione for file -> "+splitted[2] +".txt ");
									 }
									 else if(not_found) {
										 not_found=false;
										 System.out.println("Server doens't have file -> "+splitted[2] +".txt ");
											
									 }
									 else if(tmp.length()>0)
									  System.out.println("Server send file -> "+splitted[2] +".txt \n"+ tmp.substring(0, tmp.length()-1).toString());
									 else 
										 System.out.println("Server send file -> "+splitted[2] +".txt  \nEmpty Section");
										 
								  clientSocket.close(); //chiusura esplicita
							} catch (IOException e) {
								
								e.printStackTrace();
							}
						}//checking dei parametri (Else)
						else 	{System.err.println("Usage: turing  show <doc > <sec > or show <doc >"); break;}
					}
					break;
				case "list": //done it
					if(!logged) { //se non è loggato non può ottenere la lista di tutti i documenti
						System.err.println("You must be logged");
						break;
					}
					if(editing) { //se sta editando non può vedere la lista di tutta i documenti
						System.err.println("You cannot see list if you are editing");
						break;
					}
					setCommunication(port2, host); //configuro la comunicazione verso il server
					try {
						outToServer.writeBytes("list "+user+"\n"); //invio informazioni al server
						//leggo dal server
						StringBuilder tmp = null;
						//code 303: nessun documento è stato creato
						if((tmp = readFromServer(inFromServer)).toString().contains("303")) {
							System.out.println("No file existing"); 
						}
						else {
							//ricevuta correttamente la lista
							System.out.println("Server -> \n" + tmp.toString());
						}
						  clientSocket.close(); //chiusura esplicita della connessione
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				case "edit":
					if(!logged) { //per editare devo essere loggato
						System.err.println("You must be logged first!");
						break;
					}
					if(editing) { //se sto editando non posso editare un altro documento
							System.err.println("You are already editing");
							break;
					}
					if(splitted.length!=4) { //check dei parametri
						System.err.println("Usage: turing edit <doc > <sec >");
						break;
					}
					setCommunication(port2, host);//configuro comunicazione TCP verso il server
					try {
						//invio informazioni al server
						outToServer.writeBytes("edit "+splitted[2]+" "+splitted[3]+" "+user+"\n");
						StringBuilder tmp = readFromServer(inFromServer);
					
						//se ricevo uno dei codici sottostanti allora c'è stato un errore (inviato dal server)
						if(!tmp.toString().contains("300") && !tmp.toString().contains("301")&& !tmp.toString().contains("304") && !tmp.toString().contains("305")) {
							editing=true;
							
							//edito correttamente il file
							System.out.println("Editing current file: ../editing/"+splitted[2]+"/sez_"+splitted[3]+".txt sucessfully");
							file_editing=splitted[2]+"/sez_"+splitted[3]+".txt";
							//ricevo un indirizzo di multicast nel caso in cui possa editare il documento
							ip_multicast = tmp.toString(); 

							try {
								//crea un multicast socket ed esegue la bind alla porta 9000
								s = new MulticastSocket(9000);
							} catch (IOException e) {
								
								e.printStackTrace();
							}

						
							try {
								//entra a far parte di un gruppo multicast
								s.joinGroup(InetAddress.getByName(ip_multicast));
							} catch (UnknownHostException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
							clientSocket.close(); //chiusura esplicita della connessione
							//creo una nuova struttura per la memorizzazione dei messaggi multicast
							message = new MessageReceivedUDP(); 
							//attivo un thread che si mette in ascolto di messaggi multicast
							thread_receiver = new Tread_UDP_Client(ip_multicast+":9000",s,message);
							worker.execute(thread_receiver);
						}
						else {
							//stampa dell'errore da parte del server
							System.out.print(tmp.toString());
							clientSocket.close();
						}
						
					}catch(IOException e) {
						e.printStackTrace();
					}
					
					break;
				case "end":
					if(splitted.length!=6) { //check parametri
						System.err.println("Usage: turing end - edit <doc > <sec >");
						break;
					}
					if(!editing) { //controllo che stia effettivamente editando un documento
						System.err.println("You are not editing any documents" );
						break;
					}
					setCommunication(port2, host); //setto comunicazione TCP verso il server
					try {
						//invio informazioni al server
						outToServer.writeBytes("end "+splitted[4]+"/sez_"+splitted[5]+".txt"+" "+user+"\n");
						//leggo dal server
						String tmp = readFromServer(inFromServer).toString();
						//check dei codici di errore
						if(tmp.contains("306")) {
							System.out.println("Server -> 306 - You are not editing any files");
							clientSocket.close(); //chiusura esplita connessione
						}
						else if(tmp.contains("307")) {
							System.out.println("Server -> 307 - You are editing another section or document");
							clientSocket.close(); //chiusura esplicita connessione
						}
						else { //informato correttamente del server del fine editing
							
							
							//leggo la risposta
							System.out.println("Server -> 200 - Editing completed");
							//resetto le variabili d'appoggio e metto a null per il garbage collector
							
							//una volta che ho terminato l'edit smetto di ascoltare messaggi multicast
							thread_receiver.stop();
							thread_receiver=null; //elimino la referenza per il garbage collector
							//riporto lo stato iniziale
							editing=false;
							message=null;
							try {
								//abbondono il gruppo multicast
								s.leaveGroup(InetAddress.getByName(ip_multicast));
								ip_multicast = ""; //resetto l'ip multicast
							} catch (UnknownHostException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
							file_editing=null;
							s.close(); //chiudo definitivamente il socket multicast
							s=null;
							clientSocket.close(); //chiusura esplicita connessione TCP
						}
						
					} catch (IOException e) {
						
						e.printStackTrace();
					}
					
					
					break;
				case "share":
					if(!logged) { //SE non sono loggato non posso condividere documenti
						System.err.println("You must be logged first!");
						break;
					}
					
					if(splitted.length!=4) { //check parametri
						System.err.println("Usage: turing share name_document name_user");
						break;
					}
					setCommunication(port2, host); //setto comunicazione verso il server
					//send message
					try {
						//invio informazioni dal client -> server
						outToServer.writeBytes("share "+splitted[2]+" "+splitted[3]+" "+user+"\n");
						//leggo la rispsota
						System.out.print("Server -> " +readFromServer(inFromServer));
						clientSocket.close(); //chiusura esplicita connesione
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				case "send": //done - it
					if(!logged) { //se non sono loggato non posso inviiare messaggi
						System.err.println("You must be logged!");
						break;
					}
					
					if(editing==false) { //se non sto editando non posso inviare messaggi
						System.err.println("You cannot send if you are not editing");
						break;
					}
					if(splitted.length!=3) { //check parametri
						System.err.println("Usage: turing send <msg>");
						break;
					}
					setCommunication(port2, host); //setto comunicazione TCP verso il server
							//send message
							try {
								//invio informazioni al server 
								outToServer.writeBytes("send"+" "+user+": "+splitted[2]+" "+file_editing+"\n");
								System.out.print("Server ->" + readFromServer(inFromServer)); //leggo dal server
								clientSocket.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
					break;
				case "read": //done - it
					if(editing==false) {
						System.err.println("You cannot read if you are not editing");
						break;
					}
					if(splitted.length!=2) { //check dei parametri
						System.err.println("Usage: turing read ");
						break;
					}
					//utilizzo la struttura dati istanziata al momento di un nuovo editing
					//e ottengo i messaggi ricevuti in modo asincono
					System.out.print(message.getMessage()); 
					break;
				default:
					System.out.println("Usage: turing --help");
					break;
				}
			}
		}
			
		
	}
	//setta la comunicazione TCP verso il server
	private static void setCommunication(int port2, String host) {
		try {
			clientSocket = new Socket(host, port2);
		} catch (UnknownHostException e2) {
			
			e2.printStackTrace();
		} catch (IOException e2) {
		
			e2.printStackTrace();
		}
		try {
			//Setto le variabili necessarie per la comunicazione
			//da e verso il server
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	//leggo riga per riga dal server -> client
	private static StringBuilder readFromServer(BufferedReader inFromServer) throws IOException {
		
			StringBuilder tmp = new StringBuilder();
		 while ( (tmp.append( inFromServer.readLine())) != null ) {//leggo riga per riga
		      	
		        if(inFromServer.ready()==false) break; //se il buffer è vuoto ho finito di leggere
		    }
		return tmp.append('\n');
	}

	

}
