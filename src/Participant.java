import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;

public class Participant extends Thread
{
	private final int coordinatorPort; // coordinator is listening on
	private final int loggerPort; // logger server is listening on
	private final int participantPort; // this participant is listening on
	private final int timeout; // timeout in milliseconds <- used when waiting for a message from another process to decide whether that process has failed.

	private PrintWriter out;
	private BufferedReader in;

	public static void main(String[] args)
	{
		try
		{
			Participant participant = new Participant(args);

			// 1. REGISTER WITH COORDINATOR by sending message "JOIN pport" to cport
			participant.registerWithCoordinator();

			// 2. LISTEN FOR DETAILS of other participants on cport <- message: "DETAILS [ports]"
			//    add all of the participants to the database
			participant.listenForDetails();

			// 3. GET VOTE OPTIONS from coordinator on cport <- message: "VOTE_OPTIONS [option]"
			//	  then decide own vote from options (randomly)
			participant.listenForVoteOptions();

			// 4. EXECUTE A NUMBER OF ROUNDS by exchanging messages directly with the other participants (TCP)
			//    first round    <- send vote to all other participants <- message: "VOTE pport vote"
			//    second onwards <- add any new info received before starting the round to the records
			//                      send out this new info to each of the participants
			//                      if the records are complete then continue to next step, otherwise start new round
			// 5. DECIDE ON OUTCOME using majority <- draw = first option according to ascendant lexicographic order of tied options
			// 6. INFORM COORDINATOR of outcome on cport <- "OUTCOME outcome [port]"
			//    where outcome is the decided winning vote and the list is all the participants who took part
		}
		catch(ArgumentQuantityException e)
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

	private Participant(String[] args) throws ArgumentQuantityException
	{
		if (args.length < 4)
		{
			throw new ArgumentQuantityException(args);
		}

		this.coordinatorPort = Integer.parseInt(args[0]);
		this.loggerPort = Integer.parseInt(args[1]);
		this.participantPort = Integer.parseInt(args[2]);
		this.timeout = Integer.parseInt(args[3]);
		System.out.println("Running with C: " + this.coordinatorPort + ", L: " + this.loggerPort + ", P: " + this.participantPort + ", T: " + this.timeout);

		establishCoordinatorIO();
		System.out.println("Coordinator connection established");

	}

	/**
	 * Establishes an IO connection with the coordinator
	 * take input -> in, send output -> out
	 */
	private void establishCoordinatorIO()
	{
		try
		{
			Socket socket = new Socket("localhost", coordinatorPort);
			socket.setSoLinger(true, timeout); // <-------------------------------------------------------------------- not too sure about this
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			System.out.println(participantPort + ": Initialised Participant, listening on " + participantPort);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Registers with the coordinator by sending a message
	 */
	private void registerWithCoordinator()
	{
		out.println("JOIN " + participantPort);
	}

	/**
	 * Listens for the details of other participants sent by the coordinator
	 */
	private void listenForDetails()
	{

	}

	/**
	 * Listens for the vote options sent by the coordinator
	 */
	private void listenForVoteOptions()
	{

	}
}
