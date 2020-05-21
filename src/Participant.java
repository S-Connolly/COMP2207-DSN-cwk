import java.util.Arrays;

public class Participant extends Thread
{
	private final int cport; // coordinator is listening on
	private final int lport; // logger server is listening on
	private final int pport; // this participant is listening on
	private final int timeout; // timeout in milliseconds <- used when waiting for a message from another process to decide whether that process has failed.


	public static void main(String[] args)
	{
		try
		{
			Participant participant = new Participant(args);
		}
		catch(ArgumentQuantityException e)
		{
			e.printStackTrace();
		}
	}

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

		this.cport = Integer.parseInt(args[0]);
		this.lport = Integer.parseInt(args[1]);
		this.pport = Integer.parseInt(args[2]);
		this.timeout = Integer.parseInt(args[3]);

		System.out.println("Running with C: " + this.cport + ", L: " + this.lport + ", P: " + this.pport + ", T: " + this.timeout);
	}

	// 1. REGISTER WITH COORDINATOR by sending message "JOIN pport" to cport

	// 2. LISTEN FOR DETAILS of other participants on cport <- message: "DETAILS [ports]"
	//    add all of the participants to the database

	// 3. GET VOTE OPTIONS from coordinator on cport <- message: "VOTE_OPTIONS [option]"
	//	  then decide own vote from options (randomly)

	// 4. EXECUTE A NUMBER OF ROUNDS by exchanging messages directly with the other participants (TCP)
	//    first round    <- send vote to all other participants <- message: "VOTE pport vote"
	//    second onwards <- add any new info received before starting the round to the records
	//                      send out this new info to each of the participants
	//                      if the records are complete then continue to next step, otherwise start new round

	// 5. DECIDE ON OUTCOME using majority <- draw = first option according to ascendant lexicographic order of tied options

	// 6. INFORM COORDINATOR of outcome on cport <- "OUTCOME outcome [port]"
	//    where outcome is the decided winning vote and the list is all the participants who took part
}
