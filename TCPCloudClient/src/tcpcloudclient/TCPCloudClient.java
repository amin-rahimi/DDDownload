/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tcpcloudclient;

/**
 *
 * @author Amin
 */
//Example 25
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class TCPCloudClient implements Runnable {

    // The client socket
    private static Socket clientSocket = null;
    private static Socket clientSocket2 = null;
    // The output stream
    private static PrintStream os = null;
    private static PrintStream os2 = null;
    // The input stream
    private static DataInputStream is = null;
    private static Message msgIncrement;
    private static Message msgDecrement;
    private Message message;
    private static int BLOCK_SIZE = 4096;
    private static long numberOfBlocks;
    private final AtomicLong counter;
    static RandomAccessFile myfile;
    private String counterMode = null;
    private static BufferedReader inputLine = null;
    private static boolean closed = false;
    private static long fileSize;
    private static Semaphore swrite = new Semaphore(1);

    public TCPCloudClient(Message message, AtomicLong counter) {
        this.message = message;
        this.counter = counter;
    }

    public static void main(String[] args) {

        // The default port.
        int portNumber = 2222;
        int portNumber2 = 3333;
        // The default host.
        String host = "localhost";
        String host2 = "172.16.168.118";

        AtomicLong sharedCounter = new AtomicLong(0);


        if (args.length < 2) {
            System.out.println("Usage: java TCPClouadClient <host> <portNumber>\n" + "Connected to " + host + ":" + portNumber);
        } else {
            host = args[0];
            portNumber = Integer.valueOf(args[1]).intValue();
        }

        /*
         * Open a socket on a given host and port. Open input and output streams.
         */
        try {
            clientSocket = new Socket(host, portNumber);
            //clientSocket2 = new Socket(host2, portNumber);
            //clientSocket.setReceiveBufferSize(BLOCK_SIZE);
            //clientSocket.setSendBufferSize(BLOCK_SIZE);
            ///clientSocket2.setReceiveBufferSize(BLOCK_SIZE);
            inputLine = new BufferedReader(new InputStreamReader(System.in));
            os = new PrintStream(clientSocket.getOutputStream());
            //os2 = new PrintStream(clientSocket2.getOutputStream());
            is = new DataInputStream(clientSocket.getInputStream());
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + host);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to the host " + host);
        }

        /*
         * If everything has been initialized then we want to write some data to the
         * socket we have opened a connection to on the port portNumber.
         */
        if (clientSocket != null && os != null && is != null) {
            try {

                String fileAddress = "c:\\1.mp4";
                os.println("get" + " " + fileAddress);

                while (true) {
                    String fileSizeString = is.readLine();
                    fileSize = Long.parseLong(fileSizeString);
                    System.out.println("requested file size: " + fileSize);
                    if (fileSize != -1) {
                        numberOfBlocks = (fileSize / BLOCK_SIZE) + 1;
                        break;
                    }

                }
                //msgIncrement = new Message(fileAddress, BLOCK_SIZE, 1, "increment");
                msgDecrement = new Message(fileAddress, BLOCK_SIZE, numberOfBlocks, "decrement");
                /* Create a thread to read from the server. */
                myfile = new RandomAccessFile("d:\\myfile.mp4", "rw");
                // new Thread(new TCPCloudClient(msgIncrement, sharedCounter)).start();
                new Thread(new TCPCloudClient(msgDecrement, sharedCounter)).start();
            } catch (IOException e) {
                System.err.println("IOException:  " + e);
            }
        }
    }

    /*
     * Create a thread to read from the server.
     *
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        try {
            if (message.getCounterMode().compareTo("increment") == 0) {
                System.out.println("INCREMENT THREAD: Starting");
                os.println(generateMessage(message));
                long start = 0;
                int con = 0;
                byte[] mybuffer = new byte[4096];
                int mybuffersize = 0;
                while (true) {
                    //read file from server
                    InputStream input = clientSocket.getInputStream();

                    byte[] b = new byte[BLOCK_SIZE];
                    int size = input.read(b, 0, b.length);

                    System.out.println("Size Read in Server 1 = " + size);
                    write_file(start, b, size);
                    start += size;
                    long tmp = counter.incrementAndGet();
                    System.out.println("Conter = " + tmp);
                    mybuffersize = 0;
                    if (tmp >= numberOfBlocks) {
                        os.println("stop");
                        break;
                    }
                }
            } else {
                System.out.println("DECREMENT THREAD: Starting");
                os.println(generateMessage(message));
//                long start = fileSize;
//                byte[] b = new byte[BLOCK_SIZE];
//                
//                while (true) {
//                    InputStream input = clientSocket.getInputStream();
//                    int size = input.read(b, 0, b.length);
//                    for (int i = size - 1; i > 0; i--) {
//                        myfile.seek(start);
//                        myfile.write(b[i]);
//                        start--;
//                    }
//                }
                byte[] data1 = new byte[BLOCK_SIZE];
                long start = fileSize;
                int lastpacketsize = (int) (fileSize % BLOCK_SIZE);
                int size = 0;
                byte[] data = new byte[lastpacketsize];
                int index = 0;
                int last = 0;
                while (index < lastpacketsize) {
                    InputStream input = clientSocket.getInputStream();
                    byte[] b = new byte[lastpacketsize];
                    size = input.read(b, 0, b.length);
                    System.out.println("Size= " + size + " & Index= " + index);
                    if (index + size > lastpacketsize) {
                        System.arraycopy(b, 0, data, index, lastpacketsize - index);
                        last = size - lastpacketsize + index;
                        index = lastpacketsize;
                        System.arraycopy(b, size - last, data1, 0, last);

                    } else {
                        System.arraycopy(b, 0, data, index, size);
                        index += size;
                    }

                }
                start -= index;
                write_file(start, data, index);
                counter.incrementAndGet();
                int con = 0;
                while (true) {
                    //read file from server 2
                    index = last;

                    while (index < BLOCK_SIZE) {
                        InputStream input = clientSocket.getInputStream();
                        byte[] b = new byte[BLOCK_SIZE];
                        size = input.read(b, 0, b.length);
                        System.out.println("Size= " + size + " & Index= " + index);
                        if (index + size > BLOCK_SIZE) {
                            System.arraycopy(b, 0, data1, index, BLOCK_SIZE - index);
                            last = size - BLOCK_SIZE + index;
                            index = BLOCK_SIZE;
                            System.arraycopy(b, size - last, data1, 0, last);

                        } else {
                            System.arraycopy(b, 0, data1, index, size);
                            index += size;
                        }
                    }
                    start -= index;                    
                    write_file(start, data1, index);
                    long tmp = counter.incrementAndGet();
                    System.out.println("Conter = " + tmp);
                    if (tmp >= numberOfBlocks) {
                        os.println("stop");
                        break;
                    }
                }
            }//end decrement
            myfile.close();
            os.close();
            //os2.close();
            clientSocket.close();
            //clientSocket2.close();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String generateMessage(Message msg) {
        return msg.getFileName() + " " + msg.getBlockSize() + " " + msg.getFirstBlock() + " " + msg.getCounterMode();
    }

    private void write_file(long seek, byte[] b, int size) {
        try {
            //swrite.acquire();
            myfile.seek(seek);
            myfile.write(b, 0, size);
            //swrite.release();
        } catch (Exception e) {
            swrite.release();
        }
    }
}
