import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Participant extends Thread
{
	private final int coordinatorPort; // coordinator is listening on
	private final int loggerPort; // logger server is listening on
	private final int participantPort; // this participant is listening on
	private final int timeout; // timeout in milliseconds <- used when waiting for a message from another process to decide whether that process has failed.

	private PrintWriter coordinatorOut; // send messages to coordinator
	private BufferedReader coordinatorIn; // receive messages from coordinator

	private List<Integer> participants = new ArrayList<>(); // list of other participants
	private List<String> options = new ArrayList<>(); // list of vote options

	private String vote; // vote of this participant
	private final Map<Integer, String> votes = new HashMap<>(); // map of participants to votes
	private Map<Integer, String> newVotes = new HashMap<>(); // map of the votes that we're received last round
	private HashMap<ParticipantListener, Socket> participantReadSockets = new HashMap<>(); // map of the ParticipantListeners to the sockets they are using
	private HashMap<ParticipantWriter, Socket> participantWriteSockets = new HashMap<>(); // map of the ParticipantWriters to the sockets they are using

	private ServerSocket serverSocket; // the socket that this participant is listening on
	private int maxRounds = 2; // the maximum number of rounds to run
	private int round = 1; // the round this participant is currently on

	private Participant(String[] args) throws Coordinator.ArgumentQuantityException
	{
		if (args.length < 4)
		{
			throw new Coordinator.ArgumentQuantityException(args);
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
		while(true) // Keep trying if the server is not up
		{
			try
			{
				Socket socket = new Socket("localhost", coordinatorPort);
				socket.setSoLinger(true, 0);
				coordinatorOut = new PrintWriter(socket.getOutputStream(), true);
				coordinatorIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				System.out.println(participantPort + " > Initialised Participant, listening on " + participantPort);
				break;
			}
			catch(ConnectException e)
			{
				try
				{
					Thread.sleep(100);
				}
				catch(InterruptedException e2)
				{
					e2.printStackTrace();
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Registers with the coordinator by sending a message
	 */
	private void registerWithCoordinator()
	{
		// 1. REGISTER WITH COORDINATOR by sending message "JOIN participantPort" to coordinatorPort
		coordinatorOut.println("JOIN " + participantPort);
	}

	/**
	 * Listens for the details of other participants sent by the coordinator
	 * @throws IOException if there is a problem with the socket
	 */
	private void listenForDetails() throws IOException, WrongMessageException
	{
		// 2. LISTEN FOR DETAILS of other participants on coordinatorPort <- message: "DETAILS [ports]"
		//    add all of the participants to the database
		while (true)
		{
			String[] input = coordinatorIn.readLine().split(" ");
			System.out.println(participantPort + " > Adding details of length: " + input.length);
			if(input[0].equals("DETAILS"))
			{
				for(int i = 1; i < input.length; i++)
				{
					System.out.println(participantPort + " > Adding detail: " + input[i]);
					participants.add(Integer.parseInt(input[i]));
				}
				maxRounds = participants.size();
				System.out.println(participantPort + " > Participants: " + participants.toString());
				break;
			}
			else
			{
				throw new WrongMessageException("DETAILS", input[0]);
			}
		}
	}

	/**
	 * Listens for the vote options sent by the coordinator
	 * @throws IOException if there is a problem with the socket
	 */
	private void listenForVoteOptions() throws IOException, WrongMessageException
	{
		// 3. GET VOTE OPTIONS from coordinator on coordinatorPort <- message: "VOTE_OPTIONS [option]"
		//	  then decide own vote from options (randomly)
		while (true)
		{
			String[] input = coordinatorIn.readLine().split(" ");
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
				throw new WrongMessageException("VOTE_OPTIONS", input[0]);
			}
		}

		// Choose option and add it to the map
		Collections.shuffle(options);
		vote = options.get(0);
		votes.put(participantPort, vote);
		System.out.println(participantPort + " > Selected vote: " + vote);
	}

	/**
	 * Communicate with all other participants in a number of rounds to collect each participants vote
	 * @throws InterruptedException if the thread is interrupted
	 */
	private void executeRounds() throws InterruptedException
	{
		// 4. EXECUTE A NUMBER OF ROUNDS by exchanging messages directly with the other participants (TCP)
		//    first round    <- send vote to all other participants <- message: "VOTE participantPort vote"
		//    second onwards <- add any new info received before starting the round to the records
		//                      send out this new info to each of the participants
		//                      if the records are complete then continue to next step, otherwise start new round
		listenForParticipants(); // Allow all other participants to connect to this one
		connectToParticipants(); // Attempt to establish a connection to all other participants and complete the first round
		runSuccessiveRounds(); // Complete the remaining rounds
	}

	private void decideOutcome()
	{
		// 5. DECIDE ON OUTCOME using majority <- draw = first option according to ascendant lexicographic order of tied options

	}

	/**
	 * Completes all of the remaining rounds after the first
	 * @throws InterruptedException if the thread is interrupted
	 */
	private void runSuccessiveRounds() throws InterruptedException
	{
		Thread.sleep(timeout);

		while(round <= maxRounds)
		{
			System.out.println(participantPort + " > Running round: " + round);
			newVotes = new HashMap<>();

			Thread.sleep(timeout);

			round += 1;
		}

		System.out.println(participantPort + " > Votes collected:");
		votes.forEach((key, value) -> System.out.println(key + " -> " + value));
	}

	/**
	 * Attempt to establish a connection to each of the other participants
	 */
	private void connectToParticipants()
	{
		for(int participant : participants)
		{
			try
			{
				Socket socket = new Socket("localhost", participant);
				ParticipantWriter thread = new ParticipantWriter(socket);
				synchronized(participantWriteSockets)
				{
					participantWriteSockets.put(thread, socket);
				}
				thread.start();
				System.out.println(participantPort + " > Connecting to " + participant);
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Waits for all other participants to open a connection and assigns a ParticipantListener to them
	 */
	public void listenForParticipants() //----------------------------------------------------------------------------------------- IMPLEMENT THE TIMEOUT
	{
		this.start();
	}

	@Override
	public void run()
	{
		try
		{
			serverSocket = new ServerSocket(participantPort);
			Socket socket;
			while(participantReadSockets.size() < participants.size())
			{
				socket = serverSocket.accept();
				socket.setSoLinger(true, 0);
				System.out.println(participantPort + " > A participant has connected to " + participantPort);

				// Create a thread to handle the participant and add it to the map
				ParticipantListener thread = new ParticipantListener(socket);
				synchronized(participantReadSockets) // only one thread can be interacting with 'participants' at a time
				{
					participantReadSockets.put(thread, socket);
				}
				thread.start();
			}
			System.out.println(participantPort + " > All participants have connected to " + participantPort);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	private class ParticipantWriter extends Thread
	{
		private final Socket socket; // the socket of the participant this thread is handling
		private final PrintWriter out; // send messages to the participant

		private int thisRound;

		/**
		 * Handles sending out messages to other participants
		 * @param socket
		 * @throws IOException
		 */
		public ParticipantWriter(Socket socket) throws IOException
		{
			this.thisRound = 1;
			this.socket = socket;
			socket.setSoLinger(true, 0);
			this.out = new PrintWriter(socket.getOutputStream(), true);
		}

		@Override
		public void run()
		{
			while (true)
			{
				try
				{
					if(thisRound == round && round == 1) // the first round
					{
						sendMessage("VOTE " + participantPort + " " + vote);
						System.out.println(participantPort + " > Vote sent to: " + socket.getPort());
						thisRound += 1;
					}
					else if(thisRound == round && round <= maxRounds) // successive rounds
					{
						// procedure for successive rounds
						StringBuilder message = new StringBuilder("VOTE ");
						for(int participant: newVotes.keySet())
						{
							message.append(participant + " " + newVotes.get(participant + " "));
						}
						sendMessage(message.toString());
						System.out.println(participantPort + " > Message: " + message.toString() + " sent to: " + socket.getPort());
						round += 1;
					}
					else if(round == maxRounds) // all rounds are complete
					{
						System.out.println(participantPort + " > Finished sending to: " + socket.getPort());
						socket.close();
						coordinatorIn.close();
						break;
					}
				}
				catch(IOException e)
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
	}

	private class ParticipantListener extends Thread
	{
		private final Socket socket; // the socket of the participant this thread is handling
		private final BufferedReader in; // receive messages from the participant

		private int thisPort; // the port of the participant this thread is handling
		private String thisVote; // the vote chosen by the participant this thread is handling

		private int thisRound;

		/**
		 * Handles incoming messages from other participants
		 * @param socket The socket of the connection
		 * @throws IOException
		 */
		public ParticipantListener(Socket socket) throws IOException
		{
			this.thisRound = 1;
			this.socket = socket;
			socket.setSoLinger(true, 0);
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}

		@Override
		public void run()
		{
			String[] input;
			while (true)
			{
				try
				{
					if(thisRound == round && round == 1) // the first round
					{
						input = in.readLine().split(" ");
						if(input[0].equals("VOTE"))
						{
							thisPort = Integer.parseInt(input[1]);
							thisVote = input[2];
							synchronized(votes)
							{
								votes.put(thisPort, thisVote);
							}
							synchronized(newVotes)
							{
								newVotes.put(thisPort, thisVote);
							}
							System.out.println(participantPort + " > Received vote: " + thisVote + " from: " + thisPort);
							thisRound += 1;
						}
						else
						{
							throw new WrongMessageException("VOTE", input[0]);
						}
					}
					else if(thisRound == round && round <= maxRounds) // successive rounds
					{
						input = in.readLine().split(" ");
						if(input[0].equals("VOTE"))
						{
							for(int i = 1; i < input.length; i++)
							{
								synchronized(votes)
								{
									if(!votes.containsKey(input[i]))
									{
										votes.put(Integer.parseInt(input[i]), input[i + 1]);
									}
								}
								synchronized(newVotes)
								{
									if(!newVotes.containsKey(input[i]))
									{
										newVotes.put(Integer.parseInt(input[i]), input[i + 1]);
									}
								}

								System.out.println(participantPort + " > Received vote: " + input[i] + " -> " + input[i + 1] + thisPort);
								i += 2;
							}

							thisRound += 1;
						}
						else
						{
							throw new WrongMessageException("VOTE", input[0]);
						}
					}
					else if(round == maxRounds) // all rounds are complete
					{
						System.out.println(participantPort + " > Finished listening from: " + socket.getPort());
						socket.close();
						in.close();
						break;
					}
				}
				catch(IOException | WrongMessageException e)
				{
					e.printStackTrace();
					break;
				}
			}
		}
	}

	static class WrongMessageException extends Exception
	{
		String expected;
		String actual;

		/**
		 * A certain message was expected but a different one was received
		 * @param expected The message that was expected
		 * @param actual The message that was received
		 */
		WrongMessageException(String expected, String actual)
		{
			this.expected = expected;
			this.actual = actual;
		}

		@Override
		public String toString()
		{
			return "Expecting: \"" + expected + "\", but received: \"" + actual + "\"";
		}
	}

	public static void main(String[] args)
	{
		try
		{
			Participant participant = new Participant(args);
			participant.registerWithCoordinator();
			participant.listenForDetails();
			participant.listenForVoteOptions();
			participant.executeRounds();






			participant.decideOutcome();

			// 6. INFORM COORDINATOR of outcome on coordinatorPort <- "OUTCOME outcome [port]"
			//    where outcome is the decided winning vote and the list is all the participants who took part
		}
		catch(Coordinator.ArgumentQuantityException | IOException | WrongMessageException | InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
