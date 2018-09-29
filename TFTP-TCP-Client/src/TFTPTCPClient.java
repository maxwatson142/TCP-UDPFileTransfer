
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 *
 * @author mw369
 */
public class TFTPTCPClient {

    public InetAddress address;
    public Socket socket;
    DataOutputStream out;
    DataInputStream in;
    
    public static void main(String[] args) throws UnknownHostException, IOException {
          TFTPTCPClient client = new TFTPTCPClient(args[0]);
    }
    
    public TFTPTCPClient(String ip) throws UnknownHostException, IOException{
        System.out.println("Client Started...");
        // continuously call the running method so that a client can read/write multiple files
        while(true){
            userSelection(ip);
        }
        
    }

    private void userSelection(String ip) throws UnknownHostException, IOException {
         
         // set up a buffered reader to recieve keyboard input
        BufferedReader buffer=new BufferedReader(new InputStreamReader(System.in));
        
        // give user a choice of read or write
        System.out.println("To write a file enter w, to read a file enter r.");
        String line = buffer.readLine();
        switch(line){
            case "w":
                write(ip);
                break;
            case "r":
                read(ip);
                break;
            default:
                System.out.println("Not a valid input.");
                // print error message and call user selection again if invalid
                userSelection(ip);
                break;
        }
    }

    private void write(String ip) throws UnknownHostException, IOException {
        // initialise socket and writer and input
        socket = new Socket(InetAddress.getByName(ip),10000);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
        
        BufferedReader buffer=new BufferedReader(new InputStreamReader(System.in));
        // read file name
        boolean valid = false;
        String line = "";
        
        // loop untill valid file name
        File transferFile = null;
        while(!valid){
            System.out.println("Type the name of the file you would like to write:");
            line = buffer.readLine();
            
            // check if the file exists
            transferFile = new File(line);
            
            if(transferFile.exists()){
                // break out of loop if file name is valid
                valid = true;
            }else{
                // print file not found message and repeat
                System.out.println("File does not exist!");
            }
        }
        
        // convert file name into finished payload
        byte[] payload = createRequest(line,2);
        out.writeInt(payload.length);
        out.write(payload, 0, payload.length);
        
        // initialise the reader
        FileInputStream reader = new FileInputStream(transferFile);
        
        short block = 1;
        byte[] dataPayload = null;
        
        // split the file into packet size sections and send
        while(reader.available() > 0){
            System.out.println(reader.available());
            if(reader.available() >= 512){
                dataPayload = new byte[512];
                reader.read(dataPayload);
            }else{
                dataPayload = new byte[reader.available()];
                reader.read(dataPayload);
            }
            
            
            // make the next packet and send
            System.out.println(block);
            out.writeInt(createDataPayload(dataPayload,block).length);
            out.write(createDataPayload(dataPayload,block),0,createDataPayload(dataPayload,block).length);
            
            // increment block ready for next packet
            block++;
        }
        
        System.out.println("File finished writing!");
        
        
    }

    private void read(String ip) throws UnknownHostException, IOException {
        
        
        byte[] temp;
        byte[] ack = null;
        int len;
        String line = null;
        BufferedReader buffer=new BufferedReader(new InputStreamReader(System.in));
        boolean valid = false;
        
        // read file names from the user and check to see if they are present on the server side, request new file name if not present
        while(!valid){
            // restart socket on each attempt to restart thread on the server side
            socket = new Socket(InetAddress.getByName(ip),10000);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            System.out.println("Type the name of the file you would like to read:");
            line = buffer.readLine();   
            // convert file name into finished payload
            temp = createRequest(line,1);
            //construct packet and send
            out.writeInt(temp.length);
            out.write(temp);
            len = in.readInt();
            ack = new byte[len];
            in.read(ack);
            // check to see if the packet recieved 
            if(ack[1] != 5){
                valid = true;
            }else{
                System.out.println("File not found");
            }
        }
        // remove data section of packet for re-assembly
        byte[] last = Arrays.copyOfRange(ack, 4, ack.length);
        
        boolean finished = false;
        // read in all other packets
        while(!finished){
            len = in.readInt();
            if (len == 516){
                temp = new byte[516];
                in.read(temp);
            }else{
                temp = new byte[len];
                in.read(temp);
                finished = true;
            }
            temp = Arrays.copyOfRange(temp,4,temp.length);
            last = concatenate(last,temp);
        }
        // take the re-assebled byte array and convert to file
        FileOutputStream fos = new FileOutputStream(line);
        fos.write(last);
        fos.close();
        
        
        
    }
    
     // concatenates all the relevant byte arrays into a single byte array
    private byte[] createRequest(String fileName, int opCode){
        byte[] file = fileName.getBytes();
        byte[] opbyte = new byte[2];
        opbyte[1] = (byte) opCode;
        byte[] payload = concatenate(opbyte,file);
        payload = concatenate(payload,new byte[1]);
        byte[] mode = ("octet").getBytes();
        payload = concatenate(payload, mode);
        payload = concatenate(payload, new byte[1]);
        return payload;
    }
    
    // takes two byte arrays and concatenates them
    private byte[] concatenate(byte[] first, byte[] second){
        byte[] sum = new byte[(first.length+second.length)];
        int count = 0;
        for (byte b: first){
            sum[count]=b;
            count++;
        }
        
        for (byte b: second){
            sum[count]=b;
            count++;
        }
        
        return sum;
    }
    
            // put together the opcode block number and data
    private byte[] createDataPayload(byte[] payload, short block) {
        byte[] blockByte = new byte[2];
        blockByte[0] = (byte)(block & 0xff);
        blockByte[1] = (byte)((block >> 8) & 0xff);
        byte[] opByte = new byte[2];
        byte[] endByte = concatenate(opByte,blockByte);
        endByte = concatenate(endByte,payload);
        return endByte;
    }
}
