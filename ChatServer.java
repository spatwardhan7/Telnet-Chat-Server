import java.io.*;
import java.net.*;
import java.util.*;



/*
 * TO DO : Comment 
 *  
 */
public class ChatServer implements Runnable
{    
	private int port = 0;
	private List<Client> clients = new ArrayList<Client>();
	private List<ChatRoom> chatrooms = new ArrayList<ChatRoom>();
	 
	
	final int ALL_ROOMS    = 0;
	final int ACTIVE_ROOMS = 1;
	
	final int HELP_TYPE_GENERAL = 0;
	final int HELP_TYPE_CHAT    = 1;

	public ChatServer(int port)
	{  
		this.port = port;  
	}

	public void run()
	{
		try
		{
			ServerSocket ss = new ServerSocket(port);
			while (true)
			{
				Socket s = ss.accept();
				new Thread(new Client(s)).start();
			}
		}
		catch (Exception e)
		{  e.printStackTrace();  }
	}

	private synchronized boolean registerClient(Client client)
	{
		for (Client otherClient : clients)
		{
			if (otherClient.clientName.equalsIgnoreCase(client.clientName))
				return false;
		}
		clients.add(client);
		return true;
	}

	private void deregisterClient(Client client)
	{
		boolean wasRegistered = false;
		synchronized (this)
		{  wasRegistered = clients.remove(client);  }
		if (wasRegistered)
			broadcast(client, "--- " + client.clientName + " left ---");
	}

	private boolean registerRoom(String roomName,Client owner)
	{
		for(ChatRoom room: chatrooms)
		{
			if(roomName.equalsIgnoreCase(room.name))
				return false;
		}
		ChatRoom room = new ChatRoom(roomName,owner);
		chatrooms.add(room);
		return true;
	}
	private void deleteRoom(ChatRoom deleteRoom, Client client) throws IOException 
	{
		if(deleteRoom.participants.size() > 0)
		{
			client.write("There are active users in this room. Cannot be deleted at the moment");
		}
		else
		{
			if(client.equals(deleteRoom.getOwner()))
			{
				chatrooms.remove(deleteRoom);
				client.write("Room " + deleteRoom.getRoomName() + " has been deleted");
				deleteRoom = null;
			}
			else
			{
				if(clients.contains(deleteRoom.getOwner()))
				{
					client.write("Owner of this room is still active. Cannot be deleted at the moment");
				}
				else
				{
					chatrooms.remove(deleteRoom);
					client.write("Room " + deleteRoom.getRoomName() + " has been deleted");
					deleteRoom = null;					
				}
			}
		}
		
	}
	private ChatRoom findChatRoom(String roomName)
	{
		for(ChatRoom room:chatrooms)
		{
			if(room.name.equalsIgnoreCase(roomName))
				return room;
		}
		return null;
	}
	private void printHelp(Client client, int TYPE) throws IOException
	{
		client.write("Commands available:");
		if(TYPE == HELP_TYPE_GENERAL)
		{
			try 
			{
				client.write(" /createroom roomName     - Create new chat room");
				client.write(" /rooms                   - List all active rooms");
				client.write(" /allrooms                - List all (even inactive) rooms");
				client.write(" /join roomName           - Join chat room");
				client.write(" /deleteroom roomName     - Delete chat room");
				client.write(" /quit                    - Leave Chat Server"); 
				client.write(" /help                    - Print this help message");
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		else if(TYPE == HELP_TYPE_CHAT)
		{
			try 
			{
				client.write(" /listusers   - List all users in current chat room");
				client.write(" /getowner    - Prints the owner of this chat room");
				client.write(" /leave       - Leave this chat room");
				client.write(" /help        - Print this help message");
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
	}
	private void printRooms(Client currentClient,int printType) throws IOException
	{
		if(printType == ALL_ROOMS)
		{
			if(chatrooms.size() > 0)
			{
				currentClient.write("All rooms are:");
				for(ChatRoom room : chatrooms)
				{
					currentClient.write(" * "+room.name +" ("+room.participants.size()+")");
				}
				currentClient.write("end of list.");
			}
			else
			{
				currentClient.write("Sorry, there are no rooms. Use \"/createroom <roomName>\" to create a new room");
			}
		}
		else if(printType == ACTIVE_ROOMS)
		{
			if(chatrooms.size() > 0)
			{
				boolean activeRoomFound = false;
				boolean firstTimeFound  = true;
				for(ChatRoom room:chatrooms)
				{
					if(room.participants.size() > 0)
					{
						if(firstTimeFound)
						{
							activeRoomFound = true;
							currentClient.write("Active rooms are:");
							firstTimeFound = false;
						}
						currentClient.write(" * "+room.name +" ("+room.participants.size()+")");
					}
				}	
				if(activeRoomFound)
					currentClient.write("end of list.");
				if(!activeRoomFound)
					currentClient.write("There are no active rooms. Use /allrooms to see all existing rooms");

			}
			else
				currentClient.write("Sorry, there are no rooms. Use \"/createroom <roomName>\" to create a new room");
		}
	}

	private synchronized String getOnlineListCSV()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(clients.size()).append(" user(s) online: ");
		for (int i = 0; i < clients.size(); i++)
			sb.append((i > 0) ? ", " : "").append(clients.get(i).clientName);
		return sb.toString();
	}

	private void broadcast(Client fromClient, String msg)
	{
		// Copy client list (don't want to hold lock while doing IO)
		List<Client> clients = null;
		synchronized (this)
		{  clients = new ArrayList<Client>(this.clients);  }
		for (Client client : clients)
		{
			if (client.equals(fromClient))
				continue;
			try
			{  client.write(msg + "\r\n");  }
			catch (Exception e)
			{  }
		}
	}

	public class Client implements Runnable
	{
		private Socket socket = null;
		private Writer output = null;
		private BufferedReader input;
		private String clientName = null;

		public Client(Socket socket)
		{
			this.socket = socket;
		}
		public void run()
		{
			try
			{
				socket.setSendBufferSize(16384);
				socket.setTcpNoDelay(true);
				input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				output = new OutputStreamWriter(socket.getOutputStream());
				write("Welcome to Saurabh's chat server");
				write("Login Name?");
				String line = null;
				while ((line = input.readLine()) != null)
				{
					if (clientName == null)
					{
						line = line.trim();
						if (line.isEmpty())
						{
							write("A name is required. Please enter your name: ");
							continue;
						}
						clientName = line;
						if (!registerClient(this))
						{
							clientName = null;
							write("Sorry, name taken.");
							write("Login Name?");
							continue;
						}
						//write(getOnlineListCSV() + "\r\n");
						//broadcast(this, "+++ " + clientName + " arrived +++");
						write("Welcome "+clientName+"!");
						continue;
					}
					if(line.isEmpty())
					{
						continue;
					}
					if(line.equalsIgnoreCase("/rooms"))
					{
						printRooms(this,ACTIVE_ROOMS);
					}
					else if(line.equalsIgnoreCase("/allrooms"))
					{
						printRooms(this,ALL_ROOMS);
					}
					else if (2 == line.split(" ").length)
					{
						String []commandBuffer = new String[2];
						commandBuffer = line.split(" ");
						if(commandBuffer[0].equalsIgnoreCase("/createroom"))
						{
							if (!registerRoom(commandBuffer[1],this))
							{
								write("Sorry, ChatRoom name taken.");
							}
							else
							{
								write("Room "+commandBuffer[1]+" created");
							}
							
						}
						else if(commandBuffer[0].equalsIgnoreCase("/join"))
						{
							ChatRoom desiredRoom = findChatRoom(commandBuffer[1]);
							if(null != desiredRoom)
							{
								desiredRoom.startChat(this);
							}
							else
								write("Sorry, room " +commandBuffer[1]+" doesn't exist.");
						}
						else if(commandBuffer[0].equalsIgnoreCase("/deleteroom"))
						{
							ChatRoom deleteRoom = findChatRoom(commandBuffer[1]);
							if(null != deleteRoom)
							{
								deleteRoom(deleteRoom,this);
							}
							else
								write("Sorry, room " +commandBuffer[1]+" doesn't exist.");
						}
						else
						{
							write("Invalid command.Type \"/help\" to list all commands");
						}
					}
					else if(line.equalsIgnoreCase("/help"))
					{
						printHelp(this, HELP_TYPE_GENERAL);
					}
					else if (line.equalsIgnoreCase("/quit"))
					{	
						write("BYE");
						return;
					}
					else 
					{
						write("Invalid command.Type \"/help\" to list all commands");	
					}
					//broadcast(this, clientName + "> " + line);
				}
			}
			catch (Exception e)
			{  }
			finally
			{
				deregisterClient(this);
				output = null;
				try
				{  socket.close();  }
				catch (Exception e)
				{  }
				socket = null;
			}
		}
		public void write(String msg) throws IOException
		{
			output.write(msg+"\n");
			output.flush();
		}
		public String getClientName()
		{
			return this.clientName;
		}
		public BufferedReader getInputLine()
		{
			return this.input;
		}
		public boolean equals(Client client)
		{
			return (client != null) && (client instanceof Client) && (clientName != null) && (client.clientName != null) && clientName.equals(client.clientName);
		}
	}

	public class ChatRoom
	{
		private String name = null;
		private Client owner = null;
		private List<Client> participants = new ArrayList<Client>();
		
		final private int MESSAGE_TYPE_CHAT  = 0;
		final private int MESSAGE_TYPE_ENTER = 1;
		final private int MESSAGE_TYPE_LEAVE = 2;
		
		ChatRoom(String name, Client owner)
		{
			this.name = name;
			this.owner = owner;
		}
		void listParticipants(Client newParticipant) throws IOException
		{
			String newParticipantName = newParticipant.getClientName();
			for(Client participant: participants)
			{
				if(newParticipantName.equals(participant.getClientName()))
				{
					newParticipant.write(" * "+newParticipantName+" (** this is you)");
				}
				else
					newParticipant.write(" * "+participant.getClientName());
			}
			newParticipant.write("end of list.");
		}
		private void broadcast(Client fromClient, String msg, int TYPE)
		{
			// Copy client list (don't want to hold lock while doing IO)
			List<Client> clients = null;
			synchronized (this)
			{  clients = new ArrayList<Client>(this.participants);  }
			for (Client client : clients)
			{
				if(TYPE == MESSAGE_TYPE_CHAT)
				{
					try
					{ 
						client.write(fromClient.getClientName()+": "+msg); 
					}
					catch (Exception e)
					{  
					}
				}
				else if(TYPE == MESSAGE_TYPE_ENTER)
				{
					if(client.equals(fromClient))
						continue;
					try
					{
						client.write(" * new user joined "+this.name+": "+fromClient.getClientName());
					}
					catch(Exception e)
					{}
				}
				else if(TYPE == MESSAGE_TYPE_LEAVE)
				{
					if(client.getClientName().equalsIgnoreCase(fromClient.getClientName()))
					{
						try {
							client.write(msg + " (** this is you)");
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		
					}
					else
					{
						try
						{
							client.write(msg);
						}
						catch(Exception e)
						{}			
					}
				}
			}
		}
		
		Client getOwner()
		{
			return this.owner;
		}
		String getRoomName()
		{
			return this.name;
		}
		private void removeParticipant(Client client)
		{
			broadcast(client," * user has left chat: " +client.getClientName(), MESSAGE_TYPE_LEAVE);
			participants.remove(client);
		}
		private void printOwner(Client participant) throws IOException
		{
			if(this.owner!=null)
			{
				String printOwner = " * Owner of this chat room is: "+this.owner.getClientName();
				if(participant.getClientName().equalsIgnoreCase(this.owner.getClientName()))
				{
					printOwner +=" (** this is you)";
				}
				participant.write(printOwner);
				return;
			}
			participant.write(" * No active user owns this room");
		}

		void startChat(Client newParticipant) throws IOException
		{
			participants.add(newParticipant);
			
			broadcast(newParticipant,"",MESSAGE_TYPE_ENTER);
			
			// Print users in current chatroom 
			newParticipant.write("Entering room: "+this.name);
			listParticipants(newParticipant);
			
			BufferedReader input = newParticipant.getInputLine();
			String line = null;
			while ((line = input.readLine()) != null)
			{
				if(line.isEmpty())
				{
					continue;
				}
				if(line.equalsIgnoreCase("/leave"))
				{
					// TO DO : Send "left" message					
					removeParticipant(newParticipant);
					return;
				}
				else if(line.equalsIgnoreCase("/listusers"))
				{
					newParticipant.write("Users in this chatroom are:");
					listParticipants(newParticipant);
				}
				else if(line.equalsIgnoreCase("/help"))
				{
					printHelp(newParticipant, HELP_TYPE_CHAT);
				}	
				else if(line.equalsIgnoreCase("/getowner"))
				{
					printOwner(newParticipant);
				}
				else
					broadcast(newParticipant,line,MESSAGE_TYPE_CHAT);
				
			}	
		}
	}

	public static void main(String[] args)
	{
		int port = 9399;
		if (args.length > 0)
			port = Integer.parseInt(args[0]);
		new ChatServer(port).run();
	}
}
