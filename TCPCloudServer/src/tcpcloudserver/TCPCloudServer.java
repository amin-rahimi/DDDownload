/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tcpcloudserver;

/**
 *
 * @author Amin
 */
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TCPCloudServer {

    // The server socket.
    private static ServerSocket serverSocket = null;
    // The client socket.
    private static Socket clientSocket = null;
    // This chat server can accept up to maxClientsCount clients' connections.
    private static final int maxClientsCount = 10;
    private static final clientThread[] threads = new clientThread[maxClientsCount];

    public static void main(String args[]) {

        // The default port number.
        int portNumber = 2222;
        if (args.length < 1) {
            System.out.println("Usage: java TCPCloudServer <portNumber>\n" + "Server runnig on port " + portNumber);
        } else {
            portNumber = Integer.valueOf(args[0]).intValue();
        }

        /*
         * Open a server socket on the portNumber (default 2222). Note that we can
         * not choose a port less than 1023 if we are not privileged users (root).
         */
        try {
            serverSocket = new ServerSocket(portNumber);
        } catch (IOException e) {
            System.out.println(e);
        }

        /*
         * Create a client socket for each connection and pass it to a new client
         * thread.
         */
        while (true) {
            try {
                clientSocket = serverSocket.accept();

                int i = 0;
                for (i = 0; i < maxClientsCount; i++) {
                    if (threads[i] == null) {
                        (threads[i] = new clientThread(clientSocket, threads)).start();
                        break;
                    }
                }
                if (i == maxClientsCount) {
                    PrintStream os = new PrintStream(clientSocket.getOutputStream());
                    os.println("Server too busy. Try later.");
                    os.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }
}

class clientThread extends Thread {

    private DataInputStream is = null;
    private PrintStream os = null;
    private Socket clientSocket = null;
    private final clientThread[] threads;
    private int maxClientsCount;
    boolean stopMessage = false;
    RandomAccessFile fileInputStream = null;
    OutputStream out = null;
    private String fileAddress;
    File file;
    long fileSize;
    int blockSize;
    long firstBlock;
    long lastBlockSize;
    String counterMode;
    long numberOfBlocks;

    public clientThread(Socket clientSocket, clientThread[] threads) {
        this.clientSocket = clientSocket;
        this.threads = threads;
        maxClientsCount = threads.length;
    }

    public void run() {
        int maxClientsCount = this.maxClientsCount;
        clientThread[] threads = this.threads;

        try {
            /*
             * Create input and output streams for this client.
             */
            is = new DataInputStream(clientSocket.getInputStream());
            os = new PrintStream(clientSocket.getOutputStream());
//      os.println("READY");
            String line;
            String[] message = null;
            while (true) {
                line = is.readLine();
                System.out.println("Client: " + line);
                message = line.split("\\s+");
                if (message.length != 2) {
                    fileAddress = message[0];
                    fileSize = this.getFileSize(fileAddress);
                    break;
                } else {
                    fileAddress = message[1];
                    fileSize = this.getFileSize(fileAddress);
                }



                if (fileSize != -1) {
                    os.println(fileSize);
                    break;
                }



            }

            while (true) {
                if (message.length == 2) {
                    line = is.readLine();
                    message = line.split("\\s+");
                }
                initializeSending(message);
                Send();
                if (message[3].compareTo("increment") == 0) {
                    System.out.println(line);

                } else {
                    //decrement jobs
                }

                if (line.startsWith("stop")) {
                    stopMessage = true;
                    break;
                }
                break;
            }

            os.println("Jobs done");

            /*
             * Clean up. Set the current thread variable to null so that a new client
             * could be accepted by the server.
             */
            for (int i = 0; i < maxClientsCount; i++) {
                if (threads[i] == this) {
                    threads[i] = null;
                }
            }

            /*
             * Close the output stream, close the input stream, close the socket.
             */
            is.close();
            os.close();
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public long getFileSize(String fileAddress) {
        this.file = new File(fileAddress);
        if (!file.exists()) {
            System.out.println("File does not exist");
            return -1;
        }

        return file.length();
    }

    public void initializeSending(String[] message) throws FileNotFoundException {

        this.fileInputStream = new RandomAccessFile(file, "r");
        this.numberOfBlocks = (fileSize / Integer.valueOf(message[1])) + 1;
        this.lastBlockSize = (fileSize % Integer.valueOf(message[1]));
        this.blockSize = Integer.valueOf(message[1]);
        this.firstBlock = Long.parseLong(message[2]);
        this.counterMode = message[3];
        try {
            //clientSocket.setSendBufferSize(blockSize);
            //clientSocket.setReceiveBufferSize(blockSize);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void Send() {

        try {

            out = clientSocket.getOutputStream();
            int bytesRead;
            byte[] buffer = new byte[blockSize];

            if (counterMode.compareTo("increment") == 0) {

                fileInputStream.seek((firstBlock - 1) * blockSize);

                while ((bytesRead = fileInputStream.read(buffer)) != -1 && !stopMessage) {
                    if (is.available() > 0) {
                        if ((is.readLine()).equals("stop")) {
                            stopMessage = true;
                        }
                    }
                    out.write(buffer, 0, bytesRead);
                    System.out.println("Send Increment Packet");
                }
                out.flush();

            } else if (counterMode.compareTo("decrement") == 0) {
                if (firstBlock == numberOfBlocks) {
                    fileInputStream.seek(fileSize - (lastBlockSize));
                    int ss = fileInputStream.read(buffer, 0, (int) lastBlockSize);
                    out.write(buffer, 0, (int) lastBlockSize);
                    //out.write(buffer);
                    // out.flush();

                }
                buffer = new byte[blockSize];
                long start = fileSize - (lastBlockSize + blockSize);
                fileInputStream.seek(start);
                int i = 2;
                while ((bytesRead = fileInputStream.read(buffer, 0, buffer.length)) != -1 && !stopMessage) {
                    if (is.available() > 0) {
                        if ((is.readLine()).equals("stop")) {
                            stopMessage = true;
                        }
                    }
                    out.write(buffer);
                    //System.out.println(new String(buffer));
                    System.out.println("Packet = " + (i++) + " Size= " + bytesRead);
                    start -= bytesRead;
                    fileInputStream.seek(start);
                    //out.write(buffer, 0, bytesRead);

                    // out.flush();
                    System.out.println("Send Decrement Packet");

                }
                out.flush();

            }


        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
