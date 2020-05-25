import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Coordinator extends Thread
{
	private final int coordinatorPort; // this coordinator is listening on
	private final int loggerPort; // logger server is listening on
	private final int parts; // number of participants
	private final int timeout; // timeout in milliseconds <- used when waiting for a message from a participant to decide whether that participant has failed.
	private final String[] options; // set (no duplicates) of options

	private ServerSocket serverSocket; // the socket of this coordinator

	private HashMap<ParticipantHandler, Socket> participantSockets = new HashMap<>(); // map of the threads handling participants to the sockets they are using
	private List<Integer> participants = new ArrayList<>(); // list of the participant's ports

	private Coordinator(String[] args) throws ArgumentQuantityException
	{
		if (args.length < 5)
		{
			throw new ArgumentQuantityException(args);
		}

		this.coordinatorPort = Integer.parseInt(args[0]);
		this.loggerPort = Integer.parseInt(args[1]);
		this.parts = Integer.parseInt(args[2]);
		this.timeout = Integer.parseInt(args[3]);
		this.options = Arrays.copyOfRange(args, 4, args.length);
		System.out.println("Running with C: " + this.coordinatorPort + ", L: " + this.loggerPort + ", P: " + this.parts + ", T: "
				           + this.timeout + ", O: " + Arrays.toString(this.options));

		try
		{
			serverSocket = new ServerSocket(coordinatorPort);
			System.out.println("Coordinator > Initialised and listening on port " + coordinatorPort + ", waiting for " + parts + " participants, options: " + Arrays.toString(options));
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Wait for the required number of participants to join
	 * @throws IOException if there is a problem with the socket
	 */
	private void waitForParticipants() throws IOException //---------------------------------------------------------------- IMPLEMENT THE TIMEOUT
	{
		// Wait to connect with the number of participants specified in the args
		Socket socket;
		while(participantSockets.size() < parts)
		{
			socket = serverSocket.accept();
			socket.setSoLinger(true, 0);
			System.out.println("Coordinator > A participant has connected to the coordinator");

			// Create a thread to handle the participant and add it to the map
			ParticipantHandler thread = new ParticipantHandler(socket);
			synchronized(participantSockets) // only one thread can be interacting with 'participants' at a time
			{
				participantSockets.put(thread, socket);
			}
			thread.start();
		}
		System.out.println("Coordinator > All participants have connected to the coordinator");
	}

	/**
	 * Adds the participant to the vote pool and if the required number has been reached, send out the details
	 * @param port The port number of the participant that sent the JOIN message
	 */
	private void addParticipant(int port) throws TooManyParticipantsException
	{
		if(participants.size() < parts)
		{
			participants.add(port);
			if(participants.size() >= parts) // If there is now the required number of participants
			{
				// 2. SEND PARTICIPANT DETAILS to each participant <- message: "DETAILS [port]"
				sendDetails();
				System.out.println("Coordinator > Sending out details to participants");

				// 3. SEND REQUEST FOR VOTES to each participant <- message: "VOTE_OPTIONS [option]"
				sendOptions();
				System.out.println("Coordinator > Sending out options to participants");

				// 4. RECEIVE VOTES from participants <- message: "OUTCOME outcome [port]"
			}
		}
		else // If the required number of participants had already been reached throw an exception
		{
			throw new TooManyParticipantsException();
		}
	}

	/**
	 * Send the details of all other participants to each of the participants
	 */
	private void sendDetails()
	{
		synchronized(participantSockets)
		{
			for(ParticipantHandler thread : participantSockets.keySet())
			{
				thread.sendDetails();
			}
		}
	}

	private void sendOptions()
	{
		// Create message of all options
		StringBuilder message = new StringBuilder("VOTE_OPTIONS ");
		for (String option : options)
		{
			message.append(option + " ");
		}
		sendToAll(message.toString());
	}

	/**
	 * Send a message to each of the participants
	 * @param message The message to be sent
	 */
	private void sendToAll(String message)
	{
		synchronized(participantSockets)
		{
			for(ParticipantHandler thread : participantSockets.keySet())
			{
				thread.sendMessage(message);
			}
		}
	}

	private class ParticipantHandler extends Thread
	{
		private final Socket socket; // the socket of the participant this thread is handling
		private final BufferedReader in; // receive messages from the participant
		private final PrintWriter out;; // send messages to the participant

		private int thisPort; // the port of the participant this thread is handling

		/**
		 * Handles the connection to a participant
		 * @param socket The socket of the connection
		 * @throws IOException
		 */
		public ParticipantHandler(Socket socket) throws IOException
		{
			this.socket = socket;
			socket.setSoLinger(true, 0);

			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);
		}

		@Override
		public void run()
		{
			// 1. WAIT FOR PARTICIPANTS to join <- message: "JOIN port"
			String[] input;
			while (true)
			{
				try
				{
					input = in.readLine().split(" ");
					if(input[0].equals("JOIN"))
					{
						thisPort = Integer.parseInt(input[1]);
						addParticipant(thisPort);
					}
				}
				catch(IOException | TooManyParticipantsException e)
				{
					e.printStackTrace();
					break;
				}
			}
		}

		/**
		 * Sends a message to the participant
		 * @param message The message to be sent
		 */
		public void sendMessage(String message)
		{
			out.println(message);
		}

		/**
		 * Sends the details of all other participants to this participant
		 */
		public void sendDetails()
		{
			StringBuilder message = new StringBuilder("DETAILS ");
			for(Integer participant : participants)
			{
				if(participant != thisPort)
				{
					message.append(participant + " ");
				}
			}

			out.println(message);
		}
	}

	static class ArgumentQuantityException extends Exception
	{
		String[] args;

		/**
		 * Not enough arguments are given
		 * @param args The list of arguments
		 */
		ArgumentQuantityException(String[] args)
		{
			this.args = args;
		}

		@Override
		public String toString()
		{
			return "Insufficient number of arguments: " + Arrays.toString(args);
		}
	}

	static class TooManyParticipantsException extends Exception
	{
		/**
		 * Cannot add another participant as the specified number has already been reached
		 */
		TooManyParticipantsException(){ }
	}

	public static void main(String[] args)
	{
		try
		{
			Coordinator coordinator = new Coordinator(args);
			coordinator.waitForParticipants();
		}
		catch(ArgumentQuantityException | IOException e)
		{
			e.printStackTrace();
		}
	}
}
