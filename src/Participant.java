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

	private ParticipantLogger logger;

	private Socket coordinatorSocket;
	private PrintWriter coordinatorOut; // send messages to coordinator
	private BufferedReader coordinatorIn; // receive messages from coordinator

	private List<Integer> participants = new ArrayList<>(); // list of other participants
	private List<String> options = new ArrayList<>(); // list of vote options

	private String vote; // vote of this participant
	private final Map<Integer, String> votes = new HashMap<>(); // map of participants to votes
	private Map<Integer, String> newVotes = new HashMap<>(); // map of the votes that we're received last round
	private String winningVote;

	private HashMap<ParticipantListener, Socket> participantReadSockets = new HashMap<>(); // map of the ParticipantListeners to the sockets they are using
	private HashMap<ParticipantWriter, Socket> participantWriteSockets = new HashMap<>(); // map of the ParticipantWriters to the sockets they are using

	private ServerSocket serverSocket; // the socket that this participant is listening on

	private int maxRounds; // the maximum number of rounds to run
	private int round = 1; // the round this participant is currently on

	private Participant(String[] args) throws Coordinator.ArgumentQuantityException, IOException
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

		ParticipantLogger.initLogger(loggerPort, participantPort, timeout);
		logger = ParticipantLogger.getLogger();

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
				coordinatorSocket = new Socket("localhost", coordinatorPort);
				coordinatorSocket.setSoLinger(true, 0);
				coordinatorOut = new PrintWriter(coordinatorSocket.getOutputStream(), true);
				coordinatorIn = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream()));
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
		logger.joinSent(coordinatorPort);
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
				logger.detailsReceived(participants);
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
				logger.voteOptionsReceived(options);
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
		round = 0;
		listenForParticipants(); // Allow all other participants to connect to this one
		connectToParticipants(); // Attempt to establish a connection to all other participants and complete the first round
		Thread.sleep(timeout);
		round += 1;

		while(round <= maxRounds)
		{
			logger.beginRound(round);
			System.out.println(participantPort + " > Round start : " + round);

			for(ParticipantListener thread: participantReadSockets.keySet())
			{
				thread.readyUp();
			}

			boolean finished = false;
			while(!finished) // keep checking if any of the listeners or writers are still in the first round and wait for them
			{
				finished = true;
				for(ParticipantWriter thread : participantWriteSockets.keySet())
				{
					if(thread.thisRound == round)
					{
						finished = false;
					}
				}
				for(ParticipantListener thread : participantReadSockets.keySet())
				{
					if(!thread.isDone())
					{
						thread.readyUp();//finished = false;
					}
				}
				Thread.sleep(timeout);
			}

			Thread.sleep(timeout);

			synchronized(newVotes)
			{
				votes.putAll(newVotes);
				newVotes.clear();
			}
			logger.endRound(round);
			System.out.println(participantPort + " > Round complete: " + round);
			round += 1;
		}

		System.out.println(participantPort + " > Votes collected:");
		votes.forEach((key, value) -> System.out.println(key + " -> " + value));
	}

	/**
	 * Counts up all of the votes and decides on the winning option
	 */
	private void decideOutcome()
	{
		// 5. DECIDE ON OUTCOME using majority <- draw = first option according to ascendant lexicographic order of tied options
		Map<String, Integer> voteCount = new HashMap<>();
		for(String option : votes.values()) // count votes
		{
			if(voteCount.containsKey(option)) // if the option has already been seen then increment
			{
				voteCount.put(option, voteCount.get(option) + 1);
			}
			else // if the option hasn't already been seen it then add it with a value of 1
			{
				voteCount.put(option, 1);
			}
		}

		winningVote = voteCount.keySet().iterator().next();
		for(String option : voteCount.keySet())
		{
			if(voteCount.get(option) > voteCount.get(winningVote)) // if the next option is higher then use that
			{
				winningVote = option;
			}
			else if(voteCount.get(option) == voteCount.get(winningVote)) // if the next option is tied then pick the lexicographic first
			{
				if(!(winningVote.compareTo(option) < 0)) // if the new option is first lexicographically
				{
					winningVote = option;
				}
			}
		}

		logger.outcomeDecided(winningVote, new ArrayList<>(votes.keySet()));
	}

	/**
	 * Inform the coordinator of the winning option and which participants were taken into account
	 */
	private void informCoordinator()
	{
		// 6. INFORM COORDINATOR of outcome on coordinatorPort <- "OUTCOME outcome [port]"
		//    where outcome is the decided winning vote and the list is all the participants who took part

		StringBuilder message = new StringBuilder("OUTCOME " + winningVote + " ");
		for(int participant: votes.keySet())
		{
			message.append(participant + " ");
		}
		coordinatorOut.println(message);
		logger.outcomeNotified(winningVote, new ArrayList<>(votes.keySet()));
		System.out.println(participantPort + " > Outcome: " + message.toString() + "sent to coordinator");

		try // close everything
		{
			coordinatorIn.close();
			coordinatorOut.close();
			coordinatorSocket.close();

			this.stop();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
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
	public void listenForParticipants()
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
				logger.connectionEstablished(socket.getPort());
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
			serverSocket.close();
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
						List<Vote> messageVotes = new ArrayList<>();
						messageVotes.add(new Vote(participantPort, vote));
						sendMessage("VOTE " + participantPort + " " + vote);
						logger.votesSent(socket.getPort(), messageVotes);
						System.out.println(participantPort + " > Vote sent to: " + socket.getPort());
						thisRound += 1;
					}
					else if(thisRound == round && round <= maxRounds) // successive rounds
					{
						synchronized(newVotes)
						{
							if(newVotes.size() > 0) // if there is new information
							{
								List<Vote> messageVotes = new ArrayList<>();
								StringBuilder message = new StringBuilder("VOTE ");
								for(int participant : newVotes.keySet())
								{
									messageVotes.add(new Vote(participant, newVotes.get(participant)));
									message.append(participant + " " + newVotes.get(participant));
								}
								sendMessage(message.toString());
								logger.votesSent(socket.getPort(), messageVotes);
								System.out.println(participantPort + " > Message: " + message.toString() + " sent to: " + socket.getPort());

							}
						}
						thisRound += 1;
					}
					else if(round > maxRounds) // all rounds are complete
					{
						System.out.println(participantPort + " > Finished sending to: " + socket.getPort());
						socket.close();
						out.close();
						break;
					}
					else
					{
						Thread.sleep(timeout);
					}
				}
				catch(IOException | InterruptedException e)
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

		private boolean done;


		/**
		 * Handles incoming messages from other participants
		 * @param socket The socket of the connection
		 * @throws IOException
		 */
		public ParticipantListener(Socket socket) throws IOException
		{
			this.done = false;
			this.socket = socket;
			socket.setSoLinger(true, 0);
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}

		@Override
		public void run()
		{
			String line;
			String[] input;
			while (true)
			{
				System.out.println(participantPort + " > " + done + " : " + round);

				try
				{
					if(!done && round == 1) // the first round
					{
						line = in.readLine();

						if(line != null)
						{
							input = line.split(" ");
							if(input[0].equals("VOTE"))
							{
								thisPort = Integer.parseInt(input[1]);
								thisVote = input[2];
								synchronized(newVotes)
								{
									newVotes.put(thisPort, thisVote);
								}
								List<Vote> messageVotes = new ArrayList<>();
								messageVotes.add(new Vote(thisPort, thisVote));
								logger.votesReceived(thisPort, messageVotes);
								System.out.println(participantPort + " > Received vote: " + thisVote + " from: " + thisPort);
								done = true;
							}
							else
							{
								throw new WrongMessageException("VOTE", input[0]);
							}
						}
					}
					else if(!done && round <= maxRounds) // successive rounds
					{
						line = in.readLine();

						if(line != null)
						{
							input = line.split(" ");

							if(input[0].equals("VOTE"))
							{
								List<Vote> messageVotes = new ArrayList<>();

								for(int i = 1; i < input.length; i++)
								{
									synchronized(newVotes)
									{
										if(!newVotes.containsKey(Integer.parseInt(input[i])))
										{
											newVotes.put(Integer.parseInt(input[i]), input[i + 1]);
										}
									}
									messageVotes.add(new Vote(Integer.parseInt(input[i]), input[i + 1]));
									System.out.println(participantPort + " > Received vote: " + input[i] + " -> " + input[i + 1] + thisPort);
									i += 2;
								}

								logger.votesReceived(thisPort, messageVotes);

							}
							else
							{
								throw new WrongMessageException("VOTE", input[0]);
							}
						}
						done = true;

					}
					else if(round > maxRounds) // all rounds are complete
					{
						System.out.println(participantPort + " > Finished listening from: " + socket.getPort());
						socket.close();
						in.close();
						break;
					}
					else
					{
						Thread.sleep(timeout);
					}
				}
				catch(IOException | WrongMessageException | InterruptedException e)
				{
					e.printStackTrace();
					break;
				}
			}
		}

		public boolean isDone()
		{
			return done;
		}

		public void readyUp()
		{
			done = false;
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
			participant.informCoordinator();
			System.out.println(participant.participantPort + " > Done");
		}
		catch(Coordinator.ArgumentQuantityException | IOException | WrongMessageException | InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
