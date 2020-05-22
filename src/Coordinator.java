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

	private HashMap<Thread, Socket> participantSockets = new HashMap<>(); // map of the threads handling participants to the sockets they are using
	private List<Integer> participants = new ArrayList<>();

	public static void main(String[] args)
	{
		try
		{
			Coordinator coordinator = new Coordinator(args);
			coordinator.waitForParticipants();
		}
		catch(ArgumentQuantityException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

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
	private void waitForParticipants() throws IOException
	{
		// Wait to connect with the number of participants specified in the args
		Socket socket;
		while(participantSockets.size() < parts)
		{
			socket = serverSocket.accept();
			socket.setSoLinger(true, 0);
			System.out.println("Coordinator > A participant has connected to the coordinator");

			// Create a thread to handle the participant and add it to the map
			Thread thread = new ParticipantHandler(socket);
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
			if(participants.size() == parts) // If there is now the required number of participants
			{
				// 2. SEND PARTICIPANT DETAILS to each participant <- message: "DETAILS [port]"

				// 3. SEND REQUEST FOR VOTES to each participant <- message: "VOTE_OPTIONS [option]"

				// 4. RECEIVE VOTES from participants <- message: "OUTCOME outcome [port]"
			}
		}
		else // If the required number of participants had already been reached throw an exception
		{
			throw new TooManyParticipantsException();
		}
	}

	private class ParticipantHandler extends Thread
	{
		private final Socket socket; // the socket of the participant this thread is handling
		private final BufferedReader in; // receive messages from the participant
		private final PrintWriter out;; // send messages to the participant

		private int participantPort; // the port of the participant this thread is handling

		/**
		 * Handles the connection to a participant
		 * @param socket
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
						participantPort = Integer.parseInt(input[1]);
						addParticipant(participantPort);
					}
				}
				catch(IOException e)
				{
					e.printStackTrace();
					break;
				}
				catch(TooManyParticipantsException e)
				{
					e.printStackTrace();
					break;
				}
			}
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
}
