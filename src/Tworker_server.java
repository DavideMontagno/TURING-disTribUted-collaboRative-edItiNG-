import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Tworker_server implements Runnable{

	static Charset charset = Charset.forName("ASCII"); //decoder caratteri letti
	BufferedReader inFromClient=null; //stream dal client -> server
	DataOutputStream outToClient=null; //stream dal server -> client
	Socket connectionSocket=null; //socket ottenutta dall'accept nel server 
	ServerInterfaceImpl server=null; //interfaccia server (per verificare la registrazione)
	String[] split = null; //parametri passati al server 
	//Strutture dati ausiliria per poter eseguire i comandi richiesti
	UserOnline user_online=null;
	UserEditing user_editing = null;
	OfflineMessage send_notify = null;
	Group_Multicast_UDP groups = null;
	//worker ausiliario per la creazione di alcuni thread
	static ThreadPoolExecutor worker = null;
	int op=-1; //operazione da svolgere
	
	//costruttore
	public Tworker_server(BufferedReader inFromClient, DataOutputStream outToClient, Socket connectionSocket, ServerInterfaceImpl server, String[] split, int op, 
			UserOnline user_online, UserEditing user_editing,  OfflineMessage send_notify,	Group_Multicast_UDP groups) {
		this.inFromClient=inFromClient;
		this.outToClient=outToClient;
		this.connectionSocket=connectionSocket;
		this.server=server;
		this.split = split;
		this.user_online=user_online;
		this.op=op;
		this.user_editing=user_editing;
		this.send_notify = send_notify;
		this.groups = groups;
		worker =  (ThreadPoolExecutor) Executors.newFixedThreadPool(1); 
	}
	
	//metodo principale
	public void run() {
		switch(op) {
			case 0: //login
			try {
				
				if(server.isRegistered(split[1],split[2])) { //controllo che sia registrato
					boolean value = user_online.get(split[1]); //controllo che già sia loggato
					if(value) {
						System.out.println("Error login client: "+split[1]+" not correctly");
						outToClient.writeBytes("Client already logged\n");
					}else { //loggato correttamente
						System.out.println("Login client : "+split[1]+" correctly");
						//non uso l'indirizzo di porta assegnato poichè per ogni client
						//esiste un thread che ascolta sull'indirizzo ip del client alla porta specificata
						user_online.put(split[1], connectionSocket.getInetAddress().getHostAddress()+":"+split[3]);
						System.out.println("User online {username=ip}: "+user_online.toString());
						outToClient.writeBytes("200 OK - Login successfully\n");
						//starto il thread per eventuale notifiche da parte di altri client
						//che hanno invitato questo utente ad editare altri documenti
						TloginNotify t_notify = new TloginNotify(send_notify,user_online,split[1]);
						worker.execute(t_notify);
					}
					connectionSocket.close();
				}else { //l'utente non è registrato
					System.out.println("Error login client: "+split[1]+" not correctly");
					outToClient.writeBytes("Error login - User not recognized\n");
					connectionSocket.close();
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
				
				break;
			case 1: //logout
		
					try {
					
						System.out.println("Trying to logout: "+split[1]); 
						if(user_online.getSize()>0 ) //se c'è qualcuno online allora tento di rimuoverlo
							user_online.remove(split[1], connectionSocket.getInetAddress().getHostAddress()+":"+split[2]);
						System.out.println("User online {username=ip}: "+user_online.toString());
						outToClient.writeBytes("logout successfully\n");
						outToClient.flush();
						outToClient.close();
						inFromClient.close();
						connectionSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
						
				break;
			case 2: //creazione di un nuovo documento
				//se la cartella files non esiste viene creata
				if(!Files.exists(Paths.get("../files"))){
					System.out.println("Creating root directory for files");
					try {
						Files.createDirectories(Paths.get("../files"));
					} catch (IOException e) {
						
						e.printStackTrace();
					}
				}
				//check se la cartella esista
				Path dirPathObj = Paths.get("../files/"+split[1]);
				boolean dirExists = Files.exists(dirPathObj);
				if(dirExists) { //se esiste allora notifico al client che il documento esiste già
					try {
						outToClient.writeBytes("Document already exists - try with another name\n");
						connectionSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}else { //altrimenti creo una nuova cartella con un file "master.txt" contenente il nome del creatore
					try {
						Files.createDirectories(dirPathObj);
						FileOutputStream fOut = new FileOutputStream("../files/"+split[1]+"/master.txt");
						fOut.close();
						Path file_master = Paths.get("../files/"+split[1]+"/master.txt");
						
						Files.write(file_master, (split[3]+"\n").getBytes());
						fOut = new FileOutputStream("../files/"+split[1]+"/contributor.txt");
						fOut.close();
						//creo tutte le sezioni
						for(int i=0;i<Integer.parseInt(split[2]);i++) {
							fOut = new FileOutputStream("../files/"+split[1]+"/sez_"+i+".txt");
							fOut.close();
						}
						//notifico il client che ha creato un nuovo documento
						outToClient.writeBytes("Document created successfully");
						connectionSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}
				break;
			case 3:  //show
				Path path = Paths.get("../files/"+split[1]);
				//controllo che il path esista
				if(Files.exists(path, new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})) {
					//ottengo il path del file che contiene il nome del creatore
					path =  Paths.get("../files/"+split[1]+"/master.txt");
					
					FileChannel inChannel = null;
					try {
						//ottengo il canale al path
						inChannel = FileChannel.open(path,StandardOpenOption.READ);
					} catch (IOException e) {
						
						e.printStackTrace();
					}
					int red=0;
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
							
						}
						else {
							
							bytebuffer.flip();
							now.append(charset.decode(bytebuffer));
							if( now.charAt(now.length()-1) == '\n') {
								//leggo una sola sezione
								if(split.length==4) {
									if(split[3].equals(now.substring(0, now.length()-1))) { //controllo che sia il master
										//se fosse il master allora invio la sezione
										send_file(Paths.get("../files/"+split[1]+"/sez_"+split[2]+".txt"),outToClient);
										
										try {
											connectionSocket.close();
										} catch (IOException e) {
										
											e.printStackTrace();
										}
										break;
									}
									else { //altrimenti controllo che sia un collaboratore 
										if(check_user(Paths.get("../files/"+split[1]+"/contributor.txt"),split[3])==true) {
											//invio la sezione al collaboratore
											send_file(Paths.get("../files/"+split[1]+"/sez_"+split[2]+".txt"),outToClient);
											
											try {
											connectionSocket.close();
										} catch (IOException e) {
										
											e.printStackTrace();
										}
											break;
										}else { //altrimenti l'utente non ha i permessi per ricevere il file
											try {
												outToClient.writeBytes("301 - You not have the permission for file "+split[1]+"\n");
												System.out.println("File: "+split[1]+"/sez_"+split[2]+" not sent");
												connectionSocket.close();
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
											
											break;
										}
										
									}
								} //leggo tutto il documento
								else if(split.length==3) {
									if(split[2].equals(now.substring(0, now.length()-1))) {
										//se l'utente è il master allora invio il file
										send_allfile(Paths.get("../files/"+split[1]),outToClient);
										
										try {
											connectionSocket.close();
										} catch (IOException e) {
											e.printStackTrace();
										}
										break;
									}
									else {
										
										//altrimenti controllo che sia un contributor e tento di inviare il file
										if(check_user(Paths.get("../files/"+split[1]+"/contributor.txt"),split[2])==true) {
											send_allfile(Paths.get("../files/"+split[1]),outToClient);
											
											try {
												connectionSocket.close();
											} catch (IOException e) {
												e.printStackTrace();
											}
											break;
										}else {
											//l'utente non è ne un master nè un collaboratore
											try {
												outToClient.writeBytes("301 - You not have the permission for file "+split[1]+"\n");
											} catch (IOException e) {
												e.printStackTrace();
											}
											System.out.println("File: "+split[1]+" not sent");
											try {
												connectionSocket.close();
											} catch (IOException e) {
												e.printStackTrace();
											}
											break;
										}
									}
								}
								now.delete(0, now.length()); //resetto la stringbuilder
							}
							bytebuffer.clear();//resetto il buffer
						}
					}
				}else { //se il path all'inizio di questo "case" non esiste allora viene notificato al client
					
						System.out.println("File "+split[1]+" not exist");
					try {
						outToClient.writeBytes("300 - Document doesn't exist\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						connectionSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break; 
			case 4: //list
				
				String user = split[1];
				Path directory_file = Paths.get("../files");
				Path actual_document = null;
				StringBuilder to_send = new StringBuilder();
				if(Files.exists(directory_file)){ //se la directory file esiste
					Iterator<Path> iterator=null; //uso un iteratore per scorrere tutti i documenti
					try {
						iterator = Files.list(directory_file).iterator();
					} catch (IOException e) {
						e.printStackTrace();
					}
					//se non vi è nessun documento restituisco un errore al client
					if(iterator.hasNext()==false) {
						try {
							outToClient.writeBytes("303");
							
						} catch (IOException e) {
							
							e.printStackTrace();
						}
					}
					int i=1;
					//altrimenti costruisco le informazioni da inviare al client (leggendo i path "master" e "contributor")
					//per ciascun file
					while(true) {
						if(iterator.hasNext()) {
							actual_document = iterator.next();
							to_send.append(i+") "+actual_document.getFileName()+": \n");
							Path tmp= Paths.get(actual_document.toString()+"/master.txt");
							i++;
							//read_master
							try(BufferedReader reader = Files.newBufferedReader(tmp, Charset.forName("UTF-8"))){
									      String currentLine = null;
									      while((currentLine = reader.readLine()) != null){//while there is content on the current line
									    	  to_send.append("__Creator: "+currentLine+"\n");
									      }
							}catch(IOException ex){
							ex.printStackTrace(); 
							}
							//read_collaboratori
							tmp = Paths.get(actual_document.toString()+"/contributor.txt");
							boolean first_time=true;
							boolean someone=false;
							
							try(BufferedReader reader = Files.newBufferedReader(tmp, Charset.forName("UTF-8"))){
							      String currentLine = null;
							     
							      while((currentLine = reader.readLine()) != null){
							    	
							    	  someone=true;
							    	  if(first_time==true) {
							    		  
								    	  to_send.append("__Contributor: "+currentLine+",");
								    	  first_time=false;
								    	
							    	  }
							    	  else {
							    		  to_send.append(currentLine+",");
							    	  }
							    	
							      }
							      if(someone==false) {
							    	  to_send.append("__Contributor: no one");
							    	  
							      }else {
							    	  someone=false;
							      }
							      first_time=true;
							}catch(IOException ex){
							ex.printStackTrace(); 
							}
							to_send.append("\n");
						
						}
						else break;
					}
					try {
						outToClient.writeBytes(to_send.toString());
						System.out.println("Sended list at client "+connectionSocket.getInetAddress().getHostAddress());
					} catch (IOException e) {
						
						e.printStackTrace();
					}
				}
				else { //se la directory file non esiste
					try {
						outToClient.writeBytes("303 - Directory files not present");
						System.out.println("List not sent at client "+connectionSocket.getInetAddress().getHostAddress());
					} catch (IOException e) {
						
						e.printStackTrace();
					}
				}
				
				try {
					connectionSocket.close();
				} catch (IOException e) {
					
					e.printStackTrace();
				}
				break;
				
			case 5://case edit
				//controllo i permessi
				int check_permission=checkPermission(split[3],split[1],split[2]);
				if(check_permission==300 ) { //file inesistente
					try {
						outToClient.writeBytes("300 - Document doesn't exist\n");
						System.out.println("Document doesn't exist - exit");
						connectionSocket.close();
					} catch (IOException e) {
						
						e.printStackTrace();
					}
				}
				else if(check_permission==301) { //l'utente non ha i permessi
					try {
						outToClient.writeBytes("301 - You have not the permission\n");
						System.out.println("Client not have permission - exit");
						connectionSocket.close();
					} catch (IOException e) {
						
						e.printStackTrace();
					}
				}
				
				else {
					//tento di aggiungere l'utente alla struttura dati che contiene
					//il mapping (utente, sezione che sta editando)
					int value = user_editing.editDocument(split[3], split[1]+"/sez_"+split[2]+".txt");
					if(value==202) {
						//può editare correttamente
						System.out.println("Client: "+connectionSocket.getInetAddress().getHostAddress()+" starting modify: "+split[1]+"/sez_"+split[2]+".txt");
			
						
						if(!Files.exists(Paths.get("../"+split[1]))){
							
							try {
								Files.createDirectories(Paths.get("../editing/"+split[1]));
							} catch (IOException e) {
								
								e.printStackTrace();
							}
						}
						
						//creo una copia del file da editare
						String destination="../editing/"+split[1]+"/sez_"+split[2]+".txt";
						String source="../files/"+split[1]+"/sez_"+split[2]+".txt";
						try (FileInputStream inStream = new FileInputStream(source); FileOutputStream outStream = new FileOutputStream(destination))
					    {
					        final FileChannel inChannel = inStream.getChannel();
					        final FileChannel outChannel = outStream.getChannel();
					        final long size = inChannel.size();
					        long position = 0;
					        while (position < size)
					        {
					            position += inChannel.transferTo(position, 1024L * 1024L, outChannel);
					        }
					        //notifo l'ip multicast al client in modo da poter ricevere messaggi multicast
					        outToClient.writeBytes(groups.createNewGroup(split[1])+"\n");
							System.out.println("Client is starting to edit..");
							connectionSocket.close();
							break;
					    } 
						catch (IOException e) {
						
							e.printStackTrace();
						}
						
							
					}
					else if(value==304){ //il client sta già editando
						System.err.println("Client is editing already");
						try {
							outToClient.writeBytes("304 - You are already editing\n");

							System.out.println("Client is already editing - exit");
							connectionSocket.close();
						} catch (IOException e) {
							
							e.printStackTrace();
						}
					}
					else if(value==305) { //sezione già scelta
						System.err.println("Section already choosed");
						try {
							outToClient.writeBytes("305 - Section selected is already editing\n");
							connectionSocket.close();
							} catch (IOException e) {
							
							e.printStackTrace();
						}
					}
					
				}
				break;
			case 6: //end - edit
				System.out.println("Client "+split[2]+" is ending to edit file: "+split[1]);
				try {
					//tento di rimuovere il documento dal mapping (user , sezione che sta editando)
					
					int value = user_editing.removedDocument(split[2], split[1]);
					
					if(value==306) { //non sta editando alcun documento
						outToClient.writeBytes("306\n");
						connectionSocket.close();
					}
					else if(value==307) { //sta editando una sezione diversa da quella richiesta
						outToClient.writeBytes("307\n");
						connectionSocket.close();
					}
					else { //Code 203: può terminare l'editing
						String source="../editing/"+split[1];
						String destination="../files/"+split[1];
						//copio il file nella directory dei file (non in editing)
						try (FileInputStream inStream = new FileInputStream(source); FileOutputStream outStream = new FileOutputStream(destination))
					    {
					        final FileChannel inChannel = inStream.getChannel();
					        final FileChannel outChannel = outStream.getChannel();
					        final long size = inChannel.size();
					        long position = 0;
					        while (position < size)
					        {
					            position += inChannel.transferTo(position, 1024L * 1024L, outChannel);
					        }

					        inChannel.close();
					        inStream.close();
					        Files.delete(Paths.get(source)); //rimuovo la sezione dai file nella directory editing
					        String[] data = split[1].split("/");
					        //controllo che l'utente sia l'ultimo ad editare
					        if(user_editing.checkDocumentEditing(data[0])==false) {
					        	groups.removeGroup(data[0]); //in tal caso cancello il gruppo multicast
					        }
					        outToClient.writeBytes("200\n");

							System.out.println("Client editing completed");
							connectionSocket.close();
							break;
					    }
					}
				} catch (IOException e) {
					
					e.printStackTrace();
				}
				
				break;
				//invio tutti i messaggi in multicast a tutti gli utenti registrati sotto un documento
			case 7: //send
				
				//ottengo l'ip dal nome del documento
				String ip_to_send = groups.getAddress(split[3]); 
				MulticastSocket s=null; 
				try {
					s = new MulticastSocket(); //creo un nuovo socket multicast
				} catch (IOException e3) {
					e3.printStackTrace();
				}
				//creo un byte buffer con il messaggio da inviare
				byte buf[] = (split[1]+" "+split[2]).getBytes(); 
				// Creo un DatagramPacket 
				DatagramPacket pack = null;
				try {
					pack = new DatagramPacket(buf, buf.length,
										 InetAddress.getByName(ip_to_send), 9000);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
				try {
					//Invio il pacchetto creato in precedenza con il messaggio da inviare
					s.send(pack);
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				//chiudiamo la socket multicast poichè non abbiamo nulla da inviare
				s.close();
				try {
					outToClient.writeBytes("200 - Request completed\n"); //informo il client che è andato tutto ok
					outToClient.close();
				} catch (IOException e) {
					
					e.printStackTrace();
				}
				break;
			case 8: //share
				try {
					if(server.checkUser(split[2])) { //controllo che l'utente sia online
						boolean value = user_online.get(split[2]);
						if(value) {
							
							try {
								//controllo che l'utente che ha fatto richiesta sia il master
								System.out.println("Checking permission for: "+split[3]);
								boolean check=check_user(Paths.get("../files/"+split[1]+"/master.txt"),split[3]);
								if(check==false ) { //se non lo è non ha i permessi per invitare ad editare
									//un nuovo documento
									try {
										outToClient.writeBytes("You have not the permission\n");
										connectionSocket.close();
										break;
									} catch (IOException e) {
										
										e.printStackTrace();
									}
								}else {
									//altrimenti aggiungo il nome tra i contributor e notifico 
									//immediatamente il client che può editare un nuovo documento
									 Files.write(Paths.get("../files/"+split[1]+"/contributor.txt"), (split[2]+"\n").getBytes(), StandardOpenOption.APPEND);
									//creo un socket per la connessione dal server -> client
									 String hostAddress = user_online.getAddress(split[2]);
									String[] communication = hostAddress.split(":");
									Socket clientSocket= null;
									try {
										clientSocket = new Socket(communication[0], Integer.parseInt(communication[1]));
									} catch (UnknownHostException e2) {
										
										e2.printStackTrace();
									} catch (IOException e2) {
									
										e2.printStackTrace();
									}
									//notifico all'utente che ha eseguito la "shared" che è andata a buon fine
									outToClient.writeBytes("Shared completed\n");
									connectionSocket.close();
									DataOutputStream outToClient = null;
									//notifico il client che può editare un nuovo documento
									outToClient= new DataOutputStream(clientSocket.getOutputStream());
									outToClient.writeBytes("You have been invited by: "+split[3]+" to edit: "+split[1]+"\n");
									clientSocket.close();
									
								}
							} catch (IOException e) {
								
								e.printStackTrace();
							}
						}else { //utente non online
							//salvo la notifica per inviarla successivamente al client quando effettuerà la login
							 	Files.write(Paths.get("../files/"+split[1]+"/contributor.txt"), (split[2]+"\n").getBytes(), StandardOpenOption.APPEND);
								outToClient.writeBytes("Shared completed - Notify will send when user will be online.\n");
								connectionSocket.close();
								send_notify.put(split[2],split[1]);
								System.out.println("Notify to send: "+send_notify.toString());
						}
						
					}else { //se l'utente non esiste non può essere invitato a modificare un documento
						outToClient.writeBytes("302 - You cannot invite - user not recognized\n");
						outToClient.close();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
		}
		
	}
	
	
	//controllo che l'utente (username) sia contenuto nel file specificato dal Path (path).
	private boolean check_user(Path path, String username) {
		FileChannel inChannel = null; 
		try {
			//ottengo un canale al file
			inChannel = FileChannel.open(path,StandardOpenOption.READ);
		} catch (IOException e) {
			return false;
			
		}
		//bytebuffer per la lettura
		ByteBuffer bytebuffer = ByteBuffer.allocateDirect(1); //buffer in lettura
		StringBuilder now = new StringBuilder(); //costruisco una stringbuilder
		int red=0; //numero di caratteri letti
		while(true) {
			
					
			try {
				red = inChannel.read(bytebuffer); //leggo
			} catch (IOException e) {
				

				e.printStackTrace();
			}
			if(red==-1) { //se ho terminato di leggere allora  non ho trovato l'utente
				return false;
			}
			else {
				
				bytebuffer.flip(); //sistemo i puntatori del bytebuffer
				now.append(charset.decode(bytebuffer)); //appendo ciò che ho letto alla stringbuilder
				//se ho letto una riga
				if( now.charAt(now.length()-1) == '\n') {
					//verifico l'username con tale riga
					if(username.equals(now.substring(0, now.length()-1))) return true;
					now.delete(0, now.length()); //resetto la stringbuilder
				}
				bytebuffer.clear();//resetto il buffer
			}
		}
	}
	//controllo i permessi dell'utente sia nel file "master.txt" sia nei contributor.txt del file
	private int checkPermission(String username, String file,String section) {
	
		Path path = Paths.get("../files/"+file+"/sez_"+section+".txt");
		System.out.println("Check permission for file: "+path.toString());
		if(Files.exists(path)==false) { //se la sezione non esiste
			System.out.println("Section file: "+path.toString()+ " not found");
			return 300;
		}else {
			//check dei permessi nel file master.txt
			path = Paths.get("../files/"+file+"/master.txt");
			System.out.println("Check permission in file: "+path.toString());
			boolean value = check_user(path, username);
			if(value==false) { //check dei permessi nel file contributor.txt
				path = Paths.get("../files/"+file+"/contributor.txt");
				System.out.println("Check permission in file: "+path.toString());
				if(check_user(path, username)==false) {
					return 301; //non ha i permessi
				}
				else {
					return 0; //ha i permessi
				}
			}
			else //ha i permessi
				return 0; 
		}
		
	}
	
	
	
private static void send_allfile(Path path, DataOutputStream outToClient) {
		
		ByteBuffer bytebuffer = ByteBuffer.allocateDirect(1); //buffer in lettura
		StringBuilder now = new StringBuilder();
		FileChannel inChannel=null;
			//check se esiste la directory con il nome del file
			if(Files.exists(path)) {
				System.out.println(path);
				try {
					for(int i=2;i<Files.list(path).count();i++) {
						outToClient.writeBytes("Section: "+(i-2)+"\n");
						outToClient.flush();
						
						Path path2 = Paths.get(path+"/sez_"+(i-2)+".txt");
						inChannel = FileChannel.open(path2,StandardOpenOption.READ);
						get_sendfile(path, outToClient, bytebuffer, now, inChannel,i,true);
					}
				} catch (IOException e) {
					
					e.printStackTrace();
				}
			}
			else {
				try {
					System.out.println("File not exist");
					outToClient.writeBytes("300 - Cannot read file\n");
					outToClient.flush();
					return;
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			
			
			
		
			
		
	}

	private static void get_sendfile(Path path, DataOutputStream outToClient, ByteBuffer bytebuffer, StringBuilder now,
			FileChannel inChannel, int i, boolean condition) {
		int red=0;
		
		while(true) {		
			try {
				
				red = inChannel.read(bytebuffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(red==-1) {
				try {
					if(condition==false) { //se inivio una sola sezione
						outToClient.writeBytes("202\n");
						System.out.println("Sezione inviata correttamente");
						return;
					}//controllo che sia l'ultima sezione del documento da inviare
					else if(i==(Files.list(path).count()-1)) { 
						outToClient.writeBytes("202\n");
						System.out.println("File inviato correttamente");
						return;
					}else {
						//sezione terminata
						outToClient.writeBytes("201\n");
						outToClient.flush();
						return;
					}
					
				} catch (IOException e) {
					
					e.printStackTrace();
				}
			}
			else {
				bytebuffer.flip();
				now.append(charset.decode(bytebuffer));
				if( now.charAt(now.length()-1) == '\n') {
					try {
					
						outToClient.writeBytes(now.toString());
						
						outToClient.flush();
					} catch (IOException e) {
						
						e.printStackTrace();
					}
					
					now.delete(0, now.length()); //resetto la stringbuilder
				}
			
				bytebuffer.clear();//resetto il buffer
			}
		}
	}

	private static void send_file(Path path, DataOutputStream outToClient) {
		
		ByteBuffer bytebuffer = ByteBuffer.allocateDirect(1); //buffer in lettura
		StringBuilder now = new StringBuilder();
		FileChannel inChannel=null;
		
			if(Files.exists(path)) {
				try {
					inChannel = FileChannel.open(path,StandardOpenOption.READ);
					System.out.println("I'm getting: "+path+ " to send at Client");
					get_sendfile(path, outToClient, bytebuffer, now, inChannel,0,false);
				} catch (IOException e) {
					
					e.printStackTrace();
				}
			}
			else {
				try {
					System.out.println("File not exists");
					outToClient.writeBytes("300 - File not exists\n");
					outToClient.flush();
					return;
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			
			
			
		
	}
	

}
