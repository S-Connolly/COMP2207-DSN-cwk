import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

public class Coordinator extends Thread
{
	private final int coordinatorPort; // this coordinator is listening on
	private final int loggerPort; // logger server is listening on
	private final int parts; // number of participants
	private final int timeout; // timeout in milliseconds <- used when waiting for a message from a participant to decide whether that participant has failed.
	private final String[] options; // set (no duplicates) of options

	private ServerSocket serverSocket; // the socket of this coordinator

	private HashMap<Thread, Socket> participants = new HashMap<>(); // map of the participants to their respective sockets

	public static void main(String[] args)
	{
		try
		{
			Coordinator coordinator = new Coordinator(args);
			coordinator.waitForParticipants();

			// 2. SEND PARTICIPANT DETAILS to each participant <- message: "DETAILS [port]"

			// 3. SEND REQUEST FOR VOTES to each participant <- message: "VOTE_OPTIONS [option]"

			// 4. RECEIVE VOTES from participants <- message: "OUTCOME outcome [port]"
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

		public String toString() {
			return "Insufficient number of arguments: " + Arrays.toString(args);
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
		// 1. WAIT FOR PARTICIPANTS to join <- message: "JOIN port"
		Socket socket;
		while(participants.size() < parts)
		{
			socket = serverSocket.accept();
			socket.setSoLinger(true, 0); // <-------------------------------------------------------------------- not too sure about this
			System.out.println("Coordinator > A participant has connected to the coordinator");

			// Create a thread to handle the participant and add it to the map
			Thread thread = new ParticipantHandler(socket);
			synchronized(participants) // only one thread can be interacting with 'participants' at a time
			{
				participants.put(thread, socket);
			}
			thread.start();
		}
		System.out.println("Coordinator > All participants have connected to the coordinator");
	}

	private class ParticipantHandler extends Thread
	{
		private final Socket socket;
		private final BufferedReader in;
		private final PrintWriter out;

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
	}
}
