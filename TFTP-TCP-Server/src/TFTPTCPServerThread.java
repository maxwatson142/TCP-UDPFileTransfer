
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author mw369
 */
public class TFTPTCPServerThread extends Thread{

    Socket socket = null; 
    DataInputStream in;
    DataOutputStream out;
    String fileName;
    
    public TFTPTCPServerThread(Socket slaveSocket) {
        super("TFTPTCPServerThread");
        this.socket = slaveSocket;
    }

    @Override
    public void run() {
        try {
            int length;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            // read in request packet
            length = in.readInt();
            System.out.println(length);
            byte[] temp = new byte[length];
            in.read(temp);
            fileName = getFileName(temp);
            
            // check to see what the packet is requesting and call correct method
            switch(temp[1]){
                case 1:  
                    read();
                    break;
                
                case 2:
                    write();
                    break; 
            default: break;
            } 
        } catch (IOException ex) {}
    }

    private void write() throws IOException {
        // initialise variables
        boolean finished = false;
        int length;
        int block = 1;
        byte[] temp;
        byte[] total = new byte[0];
        
        // read in the packets sent from the client
        while(!finished){
            length = in.readInt();
            if (length == 516){
                temp = new byte[516];
                in.read(temp);
            }else{
                temp = new byte[length];
                in.read(temp);
                finished = true;
            }
            System.out.println(block);
            System.out.println(length);
            block ++;
            temp = Arrays.copyOfRange(temp,4,temp.length);
            total = concatenate(total,temp);
        }
        
        // convert the assembeled data to a file
        FileOutputStream fos = new FileOutputStream(fileName);
        fos.write(total);
        fos.close();
        
    }

    private void read() throws IOException {
        byte[] temp;
        
        File transferFile = null;
        transferFile = new File(fileName);
        
        // check to see if file exists, send error if not
        
        if(!transferFile.exists()){
            System.out.println("File not found!");
            temp = generateErr();
            out.writeInt(temp.length);
            out.write(temp);
        }
        
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
            dataPayload = createDataPayload(dataPayload,block);
            out.writeInt(dataPayload.length);
            out.write(dataPayload);
            
            // increment block ready for next packet
            block++;
        }
        System.out.println();
        
    }
    
    // converts a byte array to a string
    private String getFileName(byte[] data) {
        ArrayList<Byte> name = new ArrayList<Byte>();
        int count = 2;
        boolean end = false;
        
        // read filename area of the byte array
        while(!end){
            if((data[count])!= 0){
                name.add((data[count]));
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
    
    // converts the relevan section of a packet to a short representing block number
    private short getBlock(byte[] b){
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(b[2]);
        bb.put(b[3]);
        return bb.getShort(0);
    }
    
    // takes two byte arrays and concatenates
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
    
    // generates an error byte array for file not found errors 
    private byte[] generateErr() {
        byte[] payload = new byte[5];
        payload[1] = 5;
        payload[3] = 1;
        return payload;
    }
    
    // converts all the relevant information into a data payload 
    private byte[] createDataPayload(byte[] payload, short block) {
        // put together the opcode block number and data
        byte[] blockByte = new byte[2];
        blockByte[0] = (byte)(block & 0xff);
        blockByte[1] = (byte)((block >> 8) & 0xff);
        byte[] opByte = new byte[2];
        byte[] endByte = concatenate(opByte,blockByte);
        endByte = concatenate(endByte,payload);
        return endByte;
    }
}
