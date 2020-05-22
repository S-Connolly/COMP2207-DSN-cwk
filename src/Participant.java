import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class Participant extends Thread
{
	private final int coordinatorPort; // coordinator is listening on
	private final int loggerPort; // logger server is listening on
	private final int participantPort; // this participant is listening on
	private final int timeout; // timeout in milliseconds <- used when waiting for a message from another process to decide whether that process has failed.

	private PrintWriter out; // send messages to coordinator
	private BufferedReader in; // recieve messages from coordinator

	private List<Integer> participants = new ArrayList<>(); // list of other participants
	private List<String> options = new ArrayList<>(); // list of vote options

	private String vote; // vote of this participant
	private final Map<Integer, String> votes = new HashMap<>(); // map of participants to votes

	public static void main(String[] args)
	{
		try
		{
			Participant participant = new Participant(args);
			participant.registerWithCoordinator();
			participant.listenForDetails();


			participant.listenForVoteOptions();

			// 4. EXECUTE A NUMBER OF ROUNDS by exchanging messages directly with the other participants (TCP)
			//    first round    <- send vote to all other participants <- message: "VOTE participantPort vote"
			//    second onwards <- add any new info received before starting the round to the records
			//                      send out this new info to each of the participants
			//                      if the records are complete then continue to next step, otherwise start new round
			// 5. DECIDE ON OUTCOME using majority <- draw = first option according to ascendant lexicographic order of tied options
			// 6. INFORM COORDINATOR of outcome on coordinatorPort <- "OUTCOME outcome [port]"
			//    where outcome is the decided winning vote and the list is all the participants who took part
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
			socket.setSoLinger(true, 0); // <-------------------------------------------------------------------- not too sure about this
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			System.out.println(participantPort + " > Initialised Participant, listening on " + participantPort);
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
		// 1. REGISTER WITH COORDINATOR by sending message "JOIN participantPort" to coordinatorPort
		out.println("JOIN " + participantPort);
	}

	/**
	 * Listens for the details of other participants sent by the coordinator
	 * @throws IOException if there is a problem with the socket
	 */
	private void listenForDetails() throws IOException
	{
		// 2. LISTEN FOR DETAILS of other participants on coordinatorPort <- message: "DETAILS [ports]"
		//    add all of the participants to the database
		while (true)
		{
			String[] input = in.readLine().split(" ");
			if(input[0].equals("DETAILS"))
			{
				for(int i = 1; i < input.length; i++)
				{
					participants.add(Integer.parseInt(input[i]));
				}
				System.out.println(participantPort + " > Participants: " + participants.toString());
				break;
			}
			else
			{
				System.err.println(participantPort + "> Expecting DETAILS message but instead received: " + input[0]);
			}
		}
	}

	/**
	 * Listens for the vote options sent by the coordinator
	 * @throws IOException if there is a problem with the socket
	 */
	private void listenForVoteOptions() throws IOException
	{
		// 3. GET VOTE OPTIONS from coordinator on coordinatorPort <- message: "VOTE_OPTIONS [option]"
		//	  then decide own vote from options (randomly)
		while (true)
		{
			String[] input = in.readLine().split(" ");
			if(input[0].equals("VOTE_OPTIONS"))
			{
				for(int i = 1; i < input.length; i++)
				{
					options.add(input[i]);
				}
				System.out.println(participantPort + " > Options: " + options.toString());
				break;
			}
			else
			{
				System.err.println(participantPort + "> Expecting VOTE_OPTIONS message but instead received: " + input[0]);
			}
		}

		// Choose option and add it to the map
		Collections.shuffle(options);
		vote = options.get(0);
		votes.put(participantPort, vote);
		System.out.println(participantPort + " > Selected vote: " + vote);
	}
}
