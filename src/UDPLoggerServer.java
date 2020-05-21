public class UDPLoggerServer
{
	int port; // this logger server is listening on

	public static void main(String[] args)
	{
		UDPLoggerServer loggerServer = new UDPLoggerServer(Integer.parseInt(args[0]));
	}

	private UDPLoggerServer(int port)
	{
		this.port = port;

		System.out.println("Running with P: " + this.port );
	}
}
