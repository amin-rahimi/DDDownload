/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tcpcloudclient;

/**
 *
 * @author Amin
 */
public class Message {
    
    private String fileName;
    private int blockSize;
    private long firstBlock;
    private String counterMode;
    
    public Message(String fileName, int blockSize, long firstBlock, String counterMode){
        
        this.fileName = fileName;
        this.blockSize = blockSize;
        this.firstBlock = firstBlock;
        this.counterMode = counterMode;
    }
    
    
    public String getFileName(){
        return fileName;
    }
    
    public int getBlockSize(){
        return blockSize;
    }
    
    public long  getFirstBlock(){
        return firstBlock;
    }
    
    public String getCounterMode(){
        return counterMode;
    }
    
}
