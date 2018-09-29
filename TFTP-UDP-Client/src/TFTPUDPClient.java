

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

/**
 *
 * @author mw369
 */
public class TFTPUDPClient {

    public static final int serverPort = 10000;
    public int connectionPort;
    public InetAddress address;
    public DatagramSocket socket;
    public DatagramPacket packet;
    public byte[] packetPayload;
    
    public static void main(String[] args) throws IOException {
        TFTPUDPClient client = new TFTPUDPClient(args[0]);
        
        
    }
    
    public TFTPUDPClient(String ip) throws IOException{
        System.out.println("Client Started...");
        address = InetAddress.getByName(ip);
        socket = new DatagramSocket(generatePort(),address);
        // set the socket to timeout at 1 second
        socket.setSoTimeout(1000);
        while(true){
            userSelection();
        }
    }
    
    
    public void userSelection() throws IOException{
        // set up a buffered reader to recieve keyboard input
        BufferedReader buffer=new BufferedReader(new InputStreamReader(System.in));
        
        // give user a choice of read or write
        System.out.println("To write a file enter w, to read a file enter r.");
        String line = buffer.readLine();
        switch(line){
            case "w":
                write();
                break;
            case "r":
                read();
                break;
            default:
                System.out.println("Not a valid input.");
                // print error message and call user selection again if invalid
                userSelection();
                break;
        }
    }
    
    private void write() throws IOException{
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
        
        // construct packet
        packet = new DatagramPacket(payload,payload.length,address,serverPort);
        // initialise variable for ack
        DatagramPacket ack = new DatagramPacket(new byte[256],256);
        // send packet recursivley untill ack recieved
        ack = sender(packet);

        System.out.println("ack recieved");
        connectionPort=ack.getPort();
        
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
            packet = createDataPacket(dataPayload,block);
            ack = sender(packet);
            
            // increment block ready for next packet
            block++;
        }
        // send an acknoledgement of the file being finished sending
        System.out.println("File finished sending.");
    }

    private void read() throws IOException {
        // initialise block number
        
        String line = null;
        short block = 1;
        BufferedReader buffer=new BufferedReader(new InputStreamReader(System.in));
        DatagramPacket ack = new DatagramPacket(new byte[516],516);
        boolean valid = false;
        
        // read file name
        while(!valid){
            System.out.println("Type the name of the file you would like to read:");
            line = buffer.readLine();   
            // convert file name into finished payload
            byte[] payload = createRequest(line,1);
            //construct packet and send
            packet = new DatagramPacket(payload,payload.length,address,serverPort);
            ack = sender(packet);
            if(ack.getData()[1] != 5){
                valid = true;
            }else{
                System.out.println("File not found");
            }
        }
        connectionPort = ack.getPort();
        
        // put data from first packet into byte array and then send acknoledgement for first packet
        byte[] fileBytes = new byte[0];
        fileBytes = concatenate(fileBytes,Arrays.copyOfRange(ack.getData(), 4, ack.getLength()));
        System.out.println(Arrays.toString(fileBytes));
        System.out.println(fileBytes.length);
         socket.send(generateAck(block));
        block ++;
        
        boolean end = false;
        DatagramPacket temp = new DatagramPacket(new byte[516],516);
        
        // loop untill an unfilled packet arrives
        while(!end){
            // wait on packet
            socket.receive(temp);
            // check if packet has expected block number
            if(getBlock(temp.getData()) == block){
                System.out.println(temp.getLength());
                // pull out the data from the byte array
                byte[] data = Arrays.copyOfRange(temp.getData(), 4, temp.getLength());
                // check if full or not
                if(temp.getLength() >= 516){
                    fileBytes = concatenate(fileBytes,data);
                }else{
                    end = true;
                    fileBytes = concatenate(fileBytes,data);
                }
                System.out.println("recieved block " + block);
                socket.send(generateAck(block));
                block ++;
            }
        }
        
        FileOutputStream fos = new FileOutputStream(line);
        fos.write(fileBytes);
        fos.close();
        
        System.out.println("File finished writing.");
        
        
        
    }
    
    private byte[] createRequest(String fileName, int opCode){
        // concatenates all the relevant byte arrays into a single byte array
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
    
    
    // takes two byte arrays and 
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
    
    // generates a valid port number at random
    private int generatePort(){
        Random rand = new Random();
        int i = rand.nextInt(64511) + 1024;
        return i;
    }
    
    // assembles a payload for the packet then constructs the packet and returns it
    private DatagramPacket createDataPacket(byte[] payload, short block) {
        // put together the opcode block number and data
        byte[] blockByte = new byte[2];
        blockByte[0] = (byte)(block & 0xff);
        blockByte[1] = (byte)((block >> 8) & 0xff);
        byte[] opByte = new byte[2];
        byte[] endByte = concatenate(opByte,blockByte);
        endByte = concatenate(endByte,payload);
        return new DatagramPacket(endByte,endByte.length,address,connectionPort);
    }
    
    // recursivley sends and then waits on an acknoledgement untill an acknoledgement arrives
    private DatagramPacket sender(DatagramPacket out) throws IOException{
        socket.send(out);
        DatagramPacket temp = new DatagramPacket(new byte[516],516);
        try{
            socket.receive(temp);
        }catch(SocketTimeoutException e){
            temp = sender(out);
        }
        return temp;
        
    }
    
    // gets the block from the byte array
    private short getBlock(byte[] b){
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(b[2]);
        bb.put(b[3]);
        return bb.getShort(0);
    }
    
    // generates an acknoledgement packet
     private DatagramPacket generateAck(short block){
        byte[] blockByte = new byte[2];
        blockByte[0] = (byte)(block & 0xff);
        blockByte[1] = (byte)((block >> 8) & 0xff);
        byte[] ackByte = new byte[2];
        ackByte[1] = 4;
        byte[] doneByte = concatenate(ackByte,blockByte);
        return new DatagramPacket(doneByte,doneByte.length,address,connectionPort);
    }
}
