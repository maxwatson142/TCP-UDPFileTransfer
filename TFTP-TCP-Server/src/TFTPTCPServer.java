
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author mw369
 */
public class TFTPTCPServer {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // starts a master socket 
        int portNumber = 10000;
        ServerSocket masterSocket;
        Socket slaveSocket;
        
        masterSocket = new ServerSocket(portNumber);

        System.out.println("Server Started...");
        // create a new thread to handle each connection 
        while (true) {
            slaveSocket = masterSocket.accept();
            Thread temp = new Thread(new TFTPTCPServerThread(slaveSocket));
            temp.start();
        }
    }
}
