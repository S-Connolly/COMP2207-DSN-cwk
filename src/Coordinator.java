import java.io.IOException;
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

	private ServerSocket serverSocket;

	private HashMap<Thread, Socket> participants = new HashMap<>();

	public static void main(String[] args)
	{
		try
		{
			Coordinator coordinator = new Coordinator(args);

			// 1. WAIT FOR PARTICIPANTS to join <- message: "JOIN port"
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

	/**
	 * Not enough arguments are given
	 */
	static class ArgumentQuantityException extends Exception
	{
		String[] args;

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
			System.out.println("Coordinator: Initialised listening on " + coordinatorPort + ", expecting " + parts + " participants, options: " + options.toString());
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		System.out.println("Server socket initialised");
	}

	/**
	 * Wait for the required number of participants to join
	 */
	private void waitForParticipants() throws IOException
	{
		Socket socket;
		while(participants.size() < parts)
		{
			socket = serverSocket.accept();
			socket.setSoLinger(true, timeout); // <-------------------------------------------------------------------- not too sure about this

			// CREATE THE THREAD TO HANDLE THE PARTICIPANT + ADD IT TO THE MAP
		}
	}
}
