/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import net.sourceforge.jFuzzyLogic.plot.JFuzzyChart;
import net.sourceforge.jFuzzyLogic.rule.Variable;
import static routing.MessageRouter.Q_MODE_FIFO;
import static routing.MessageRouter.Q_MODE_RANDOM;


/**
 * Epidemic message router with drop message whit minPriority in buffer
 * connections at a time.
 */
public class EpidemicRouter_fuzzy extends ActiveRouter {
    public static final String FCL_NAMES = "fcl";
    public static final String MESSAGE_SIZE = "ms";
    public static final String FORWARD_TRANSMISSION_COUNT = "ftc";
    public static final String NILAI_PRIORITAS = "priority";
    public static final String MESSAGE_PRIORITY = "priority";
    public static final int Q_MODE_ASC = 1;
    public static final int Q_MODE_DESC = 1;

    
     private FIS fcl;
    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public EpidemicRouter_fuzzy(Settings s) {
        super(s);
        String fclString = s.getSetting(FCL_NAMES);
        fcl = FIS.load(fclString);
    }

    protected EpidemicRouter_fuzzy(EpidemicRouter_fuzzy r) {
        super(r);
        this.fcl = r.fcl;
    }

    @Override
    public boolean createNewMessage(Message m) { 
        m.setFtc(m.getHopCount());
        m.getSize();
        makeRoomInBuffer(m.getSize());
        m.addProperty(MESSAGE_PRIORITY, 1);
        return super.createNewMessage(m);
    }
 
    //menghitung priority message 
    public double callPriority(Message m) {
        Collection<Message> messages = getMessageCollection();
        double priority = 0;
            for (Message ms : messages) {
                ms.coaValue = fuzzy(ms);
                priority = 1 - ms.coaValue;
                ms.setPriority(priority);
//                System.out.println("FTC : " + ms.getFtc() + " MS :  " + ms.getSize() + " priority : " + ms.priority);
            } 
            m.setPriority(priority);
    return m.priority;
         
    }
    
    public boolean isFinalDest (Message m, DTNHost thisHost) {
        return m.getTo().equals(thisHost);
    }
    

    private void exchangeMessageProperty() {

        Collection<Message> messCollection = getMessageCollection();

        for (Connection conn : getConnections()) {
            DTNHost other = conn.getOtherNode(getHost());
            EpidemicRouter_fuzzy otherHost = (EpidemicRouter_fuzzy) other.getRouter();

            if (otherHost.isTransferring()) {
                continue; //artinya di skip
            }
           
            for (Message m : messCollection) {
                if (otherHost.hasMessage(m.getId())) {
                    Message tmp = otherHost.getMessage(m.getId());
                    Integer me = (Integer) m.getProperty(MESSAGE_PRIORITY);
                    Integer peer = (Integer) tmp.getProperty(MESSAGE_PRIORITY);

                    if (me < peer) {
                        m.updateProperty(MESSAGE_PRIORITY, peer);
                    }
                }
            }
        }
    }

    //method untuk mengurutkan pesan dari besar ke kecil (priotitasnya)
    @Override
    protected List sortByQueueMode(List list) {
        switch (sendQueueMode) {
                case Q_MODE_DESC:
                Collections.sort(list, new Comparator() {

                    @Override
                    public int compare(Object o1, Object o2) {
                        double diff;
                        Message m1, m2;

                        if (o1 instanceof Tuple) {
                            m1 = ((Tuple<Message, Connection>) o1).getKey();
                            m2 = ((Tuple<Message, Connection>) o2).getKey();
                        } else if (o1 instanceof Message) {
                            m1 = (Message) o1;
                            m2 = (Message) o2;
                        } else {
                            throw new SimError("Invalid type of objects in "
                                    + "the list");
                        }

                        diff = m2.priority - m1.priority;
                        if (diff == 0) {
                            return 0;
                        }
                        return (diff < 0 ? -1 : 1);
                    }
                });
                break;
            default:

        }
      
        return list;
    }

    protected Connection tryAllMessagesToAllConnections() {
        List<Connection> connections = getConnections();


        List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
        List<Message> temp = new ArrayList<Message>();
        exchangeMessageProperty();

        for (Connection c : connections) {
            DTNHost otherhost = c.getOtherNode(getHost());
            EpidemicRouter_fuzzy peer = (EpidemicRouter_fuzzy) otherhost.getRouter();
            
            if (getHost().getBufferOccupancy() < otherhost.getRouter().getFreeBufferSize()) {
                temp.addAll(messages);
            } else { //for each untuk CSTQ disini
                List<Message> listMergedMessages = getMergedSortMessage(); //getMergedSortMessage() adalah bandingan prioritas node A dan B
                for (Message m : listMergedMessages) {
                    if (!peer.hasMessage(m.getId()) && m.getSize() < peer.getFreeBufferSize()) {
                        temp.add(m);
//                        if (peer.canAcceptMessage(m)) {
//                            sendMessage(m, m.getTo());                        
//                        }
                    }                   
                }
            }
        }
        
        this.sendQueueMode = Q_MODE_DESC;
//        this.sendQueueMode = Q_MODE_RANDOM;
        List<Message> msgTemp = this.sortByQueueMode(temp);
        return tryMessagesToConnections(msgTemp, connections);
    }

//    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);
        int count = (Integer) m.getProperty(MESSAGE_PRIORITY) + 1;
        
        for (Connection c : getConnections()) {
            DTNHost thisHost = getHost();

            DTNHost other = c.getOtherNode(thisHost);
            EpidemicRouter_fuzzy peer = (EpidemicRouter_fuzzy) other.getRouter();
       
            if (isFinalDest(m, getHost())) {
                makeRoomInBuffer(m.getSize());
            } else {
                if (this.callPriority(m) > peer.callPriority(m)) { 
//                    System.out.println("me : " +this.callPriority(m)+" peer : "+peer.callPriority(m));
                    makeRoomInBuffer(m.getSize());
                }
            }
        }
        m.updateProperty(MESSAGE_PRIORITY, count);
        return m;
    }
    
    protected double fuzzy (Message m){
        double ftcValue = m.getFtc();
        double msValue = m.getSize();
        
        FunctionBlock functionBlock = fcl.getFunctionBlock(null);
        

        functionBlock.setVariable(FORWARD_TRANSMISSION_COUNT, ftcValue);
        functionBlock.setVariable(MESSAGE_SIZE, msValue);
        functionBlock.evaluate();

        Variable coa = functionBlock.getVariable(NILAI_PRIORITAS);
        
        return coa.getValue();
    }


    //method menggabungkan priority pesan node A dan B
    protected List<Message> getMergedSortMessage() {
        List<Message> mergeMsg = new ArrayList<Message>();
        for (Connection c : getConnections()) {
            DTNHost thisHost = getHost();

            DTNHost other = c.getOtherNode(thisHost);
            EpidemicRouter_fuzzy peer = (EpidemicRouter_fuzzy) other.getRouter();
            List<Message> messageBufferA = new ArrayList<>(getMessageCollection());
            List<Message> messageBufferB = new ArrayList<>(peer.getMessageCollection());
            
            //menggurutkan pesan yg ada di dalam buffer node A dan node B dari prioritas yg kecil
            List<Message> m1 = this.sortByQueueMode(messageBufferA);
            List<Message> m2 = peer.sortByQueueMode(messageBufferB);
            mergeMsg.addAll(m1);           
            mergeMsg.addAll(m2);
            Collections.sort(mergeMsg, new Comparator<Message>() {
                @Override
                public int compare(Message message1, Message message2) {
                    int firstMessage = (Integer) message1.getProperty(MESSAGE_PRIORITY);
                    int secondMessage = (Integer) message2.getProperty(MESSAGE_PRIORITY);

                    if (firstMessage == secondMessage) {
                        return 0;
                    } else if (firstMessage > secondMessage) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });

        }
        return mergeMsg;
    }


    protected boolean makeRoomInBuffer(int size) {
        if (size > this.getBufferSize()) {
            return false; // message too big for the buffer
        }
        int freeBuffer = this.getFreeBufferSize();
        while (freeBuffer < size) {
            Message m = getMessageWithMinPriority(true);
            if (m == null) {
                return false;
            } 
            deleteMessage(m.getId(), true);
            freeBuffer += m.getSize();
        }
        return true;
    }

    protected Message getMessageWithMinPriority(boolean excludeMsgBeingSent) {
        Collection<Message> messages = this.getMessageCollection();
        Message mMinPrio = null;
        for (Message m : messages) {
            if (excludeMsgBeingSent && isSending(m.getId())) {
                continue;
            }
            if (mMinPrio == null) {
                mMinPrio = m;
            } else if (mMinPrio.priority < m.priority) {
                mMinPrio = m;
            }
        }
        return mMinPrio;
    }
    
    @Override
    public void update() {
        super.update();
        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        // then try any/all message to any/all connection
        this.tryAllMessagesToAllConnections();

    
    }
    
   @Override
    public EpidemicRouter_fuzzy replicate() {
        return new EpidemicRouter_fuzzy(this);
    }  
}



