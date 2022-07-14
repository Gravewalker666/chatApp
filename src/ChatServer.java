import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A multithreaded chat room server.  When a client connects the
 * server requests a screen name by sending the client the
 * text "SUBMITNAME", and keeps requesting a name until
 * a unique one is received.  After a client submits a unique
 * name, the server acknowledges with "NAMEACCEPTED".  Then
 * all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name.  The
 * broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple
 * chat server, there are a few features that have been left out.
 * Two are very useful and belong in production code:
 *
 *     1. The protocol should be enhanced so that the client can
 *        send clean disconnect messages to the server.
 *
 *     2. The server should do some logging.
 */
public class ChatServer {

    /**
     * The port that the server listens on.
     */
    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room.  Maintained
     * so that we can check that new clients are not registering name
     * already in use.
     */
    final private static HashSet<String> names = new HashSet<String>();

    /**
     * The set of all the print writers for all the clients.  This
     * set is kept, so we can easily broadcast messages.
     */
    final private static HashMap<String, PrintWriter> writers = new HashMap<>();

    /**
     * The appplication main method, which just listens on a port and
     * spawns handler threads.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                Socket socket  = listener.accept();
                Thread handlerThread = new Thread(new Handler(socket));
                handlerThread.start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for a dealing with a single client
     * and broadcasting its messages.
     */
    private static class Handler implements Runnable {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {

                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (names) {
                        if (!names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the
                // socket's print writer to the set of all writers so
                // this client can receive broadcast messages.
                out.println("NAMEACCEPTED");
                for (String activeUserName: names) {
                    out.println("NEW_USER" + activeUserName);
                }
                for (PrintWriter writer: writers.values()) {
                    writer.println("NEW_USER" + name);
                }
                writers.put(name, out);

                // Accept messages from this client and broadcast them.
                // Ignore other clients that cannot be broadcast to.
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }

                    String messageToBeSent = "";
                    HashSet<PrintWriter> writersToBeWrittenOn = new HashSet<>();
                    writersToBeWrittenOn.add(writers.get(name));

                    String[] destructuredInput = input.split(">>");
                    boolean isMessageStructuredProperly = destructuredInput.length == 2;
                    if (isMessageStructuredProperly) {
                        String[] receivers = destructuredInput[0].split(",");
                        messageToBeSent = destructuredInput[1];
                        if (receivers.length == 1 && receivers[0].equals("ALL")) {
                            writersToBeWrittenOn.addAll(writers.values());
                        } else {
                            for (String receiverName: receivers) {
                                if (receiverName != null) {
                                    PrintWriter writer = writers.get(receiverName);
                                    if (writer != null) writersToBeWrittenOn.add(writer);
                                }
                            }
                        }
                    }

                    if (writersToBeWrittenOn.size() == 1) {
                        messageToBeSent = "Couldn't find the receiver(s). Message: " + messageToBeSent;
                    }

                    for (PrintWriter writer : writersToBeWrittenOn) {
                        writer.println("MESSAGE " + name + ": " + messageToBeSent);
                    }
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } finally {
                // This client is going down! Remove its name and its print
                // writer from the sets, and close its socket.
                if (name != null) {
                    names.remove(name);
                    writers.remove(name);
                    for (PrintWriter writer: writers.values()) {
                        writer.println("REMOVE_USER" + name);
                    }
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}