


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 *
 * @author mw369
 */
public class TFTPUDPServerThread extends Thread{
    
    private DatagramSocket connection = null;
    private DatagramPacket reqPacket = null;
    private InetAddress clientIP;
    private int clientPort;
    private DatagramPacket dataPacket;
    
    public TFTPUDPServerThread (DatagramPacket reqPacket) throws SocketException{
        this.reqPacket = reqPacket;
        clientPort = reqPacket.getPort();
        clientIP = reqPacket.getAddress();
        connection = new DatagramSocket(generatePort());
    }
    
    @Override
    // check to see if the request is a read or write request and call apropriate method
    public void run(){
        switch(reqPacket.getData()[1]){
            case 1: {
            try {
                read();
            } catch (IOException ex) {}
        }
                break;
            case 2: {
                try {
                    write();
                } catch (IOException ex) {}
            }   
                break;  
            default: break;
        }
    }
    
    private void write() throws IOException{
        // initialise block number
        short block = 0;
        // write a message telling the server console what's happening
        System.out.println("A client wants to write a new file named "+ getFileName());
        
        // send initial acknoledgement
        connection.send(generateAck(block));
        block++;
        
        // initialise a byte array ready to collect the data from the packets
        byte[] fileBytes = new byte[0];
        
        boolean end = false;
        DatagramPacket temp = new DatagramPacket(new byte[516],516);
        
        // loop untill an unfilled packet arrives
        while(!end){
            // wait on packet
            connection.receive(temp);
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
                connection.send(generateAck(block));
                block ++;
            }
        }
        
        FileOutputStream fos = new FileOutputStream(getFileName());
        fos.write(fileBytes);
        fos.close();
        
        System.out.println("File finished writing.");
        
        
    }
    
    private void read() throws IOException{
        System.out.println("A client wants to read the file named "+ getFileName());
        
        // check if the file exists
        File transferFile = null;
        transferFile = new File(getFileName());
            
        if(!transferFile.exists()){
            System.out.println("File not found!");
            connection.send(generateErr());
            throw new IOException();
        }
        
        // initialise a reader for reading from the file
        FileInputStream reader = new FileInputStream(transferFile);
        
        // initialise variables for while loop
        short block = 1;
        DatagramPacket ack = null;
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
            dataPacket = createDataPacket(dataPayload,block);
            ack = sender(dataPacket);
            
            // increment block ready for next packet
            block++;
        }
        
        System.out.println("File finished sending");
        
        
    }
    
    // returns the file name as a string from the byte array
    private String getFileName() {
        ArrayList<Byte> name = new ArrayList<Byte>();
        int count = 2;
        boolean end = false;
        
        // read filename area of the byte array
        while(!end){
            if((reqPacket.getData()[count])!= 0){
                name.add((reqPacket.getData()[count]));
            }else{
                end = true;
            }
            count++;
        }
        
        //convert Byte array to byte array
        byte[] name2 = new byte[name.size()];
        int j = 0;
        for(Byte b: name){
            name2[j++] = b.byteValue();
        }
        // convert byte array to string and return
        String s = new String(name2);
        return s;
    }
    
       // generates a valid port number at random
    private int generatePort(){
        Random rand = new Random();
        int i = rand.nextInt(64511) + 1024;
        return i;
    }
    
    // generates an acknoledgement packet
    private DatagramPacket generateAck(short block){
        byte[] blockByte = new byte[2];
        blockByte[0] = (byte)(block & 0xff);
        blockByte[1] = (byte)((block >> 8) & 0xff);
        byte[] ackByte = new byte[2];
        ackByte[1] = 4;
        byte[] doneByte = concatenate(ackByte,blockByte);
        return new DatagramPacket(doneByte,doneByte.length,clientIP,clientPort);
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
    
    // converts the relevant part of the byte array to a short representing the block number
    private short getBlock(byte[] b){
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(b[2]);
        bb.put(b[3]);
        return bb.getShort(0);
    }

    // generates an error package for file not found
    private DatagramPacket generateErr() {
        byte[] payload = new byte[5];
        payload[1] = 5;
        payload[3] = 1;
        return new DatagramPacket(payload,payload.length,clientIP,clientPort);
    }
    
    // assembles a data packet from the relevant information
    private DatagramPacket createDataPacket(byte[] payload, short block) {
        // put together the opcode block number and data
        byte[] blockByte = new byte[2];
        blockByte[0] = (byte)(block & 0xff);
        blockByte[1] = (byte)((block >> 8) & 0xff);
        byte[] opByte = new byte[2];
        byte[] endByte = concatenate(opByte,blockByte);
        endByte = concatenate(endByte,payload);
        return new DatagramPacket(endByte,endByte.length,clientIP,clientPort);
    }
    
    // recursivley sends the packet untill an acknoledgement arrives
    private DatagramPacket sender(DatagramPacket out) throws IOException{
        connection.send(out);
        DatagramPacket temp = new DatagramPacket(new byte[256],256);
        try{
            connection.receive(temp);
        }catch(SocketTimeoutException e){
            temp = sender(out);
        }
        return temp;
        
    }
}
