import java.util.Arrays;

public class Coordinator extends Thread
{
	int port; // this coordinator is listening on
	int lport; // logger server is listening on
	int parts; // number of participants
	int timeout; // timeout in milliseconds <- used when waiting for a message from a participant to decide whether that participant has failed.
	String[] options; // set (no duplicates) of options


	public static void main(String[] args)
	{
		try
		{
			Coordinator coordinator = new Coordinator(args);
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

	private Coordinator(String[] args) throws ArgumentQuantityException
	{
		if (args.length < 5)
		{
			throw new ArgumentQuantityException(args);
		}

		this.port = Integer.parseInt(args[0]);
		this.lport = Integer.parseInt(args[1]);
		this.parts = Integer.parseInt(args[2]);
		this.timeout = Integer.parseInt(args[3]);
		this.options = Arrays.copyOfRange(args, 4, args.length);

		System.out.println("Running with C: " + this.port + ", L: " + this.lport + ", P: " + this.parts + ", T: "
				           + this.timeout + ", O: " + Arrays.toString(this.options));
	}

	// 1. WAIT FOR PARTICIPANTS to join <- message: "JOIN port"

	// 2. SEND PARTICIPANT DETAILS to each participant <- message: "DETAILS [port]"

	// 3. SEND REQUEST FOR VOTES to each participant <- message: "VOTE_OPTIONS [option]"

	// 4. RECEIVE VOTES from participants <- message: "OUTCOME outcome [port]"
}
