

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 *
 * @author mw369
 */
public class TFTPUDPServer {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException{
        // set port for master socket
        int portNumber = 10000;
        
        // initialise master and slave sockets
        DatagramSocket masterSocket;
        
        // initialises the packet variable with a dummy packet
        
        
        masterSocket = new DatagramSocket(portNumber,InetAddress.getByName("127.0.0.1"));
        
        System.out.println("Server Started...");
        
        //loop that runs forever until the server is closed
        while (true){
            // wait on a connection from a client
            byte[] test = new byte[256];
            DatagramPacket temp = new DatagramPacket(test,256);
            masterSocket.receive(temp);
            Thread transfer = new Thread(new TFTPUDPServerThread(temp));
            transfer.start();
        }
        
    }
    

    
}
