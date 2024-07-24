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
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EpidemicRouter_fuzzyyy extends ActiveRouter {
    public static final String FCL_NAMES = "fcl";
    public static final String MESSAGE_SIZE = "ms";
    public static final String FORWARD_TRANSMISSION_COUNT = "ftc";
    public static final String NILAI_PRIORITAS = "priority";
    public static final String MESSAGE_PRIORITY = "priority";
    public static final int Q_MODE_ASC = 1;

    
     private FIS fcl;
    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public EpidemicRouter_fuzzyyy(Settings s) {
        super(s);
        String fclString = s.getSetting(FCL_NAMES);
        fcl = FIS.load(fclString);
    }

    protected EpidemicRouter_fuzzyyy(EpidemicRouter_fuzzyyy r) {
        super(r);
        this.fcl = r.fcl;
    }

    @Override
    public boolean createNewMessage(Message m) { 
        m.setFtc(m.getHopCount());
        m.getSize();
//        m.setTtl(this.msgTtl);
      //  m.addProperty(NILAI_PRIORITAS, 1);
        m.addProperty(MESSAGE_PRIORITY, 1);
        return super.createNewMessage(m);
    }
  
    //menghitung priority message 
    public double callPriority(Message m) {
         Collection<Message> messages = getMessageCollection();
            for (Message ms : messages) {
                ms.coaValue = fuzzy(ms);
                ms.priority = 1 - ms.coaValue;
            }
//            m.priority = 1 - m.coaValue;       
    return m.priority;
         
    }
    
//    public void getPriorityMp() {
//        callPriority(m)
//    }
    
    public boolean isFinalDest (Message m, DTNHost thisHost) {
        return m.getTo().equals(thisHost);
    }
    

    //method untuk update property
    private void exchangeMessageProperty() {

        Collection<Message> messCollection = getMessageCollection();

        for (Connection conn : getConnections()) {
            DTNHost other = conn.getOtherNode(getHost());
            EpidemicRouter_fuzzyyy otherHost = (EpidemicRouter_fuzzyyy) other.getRouter();

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

    //method untuk mengurutkan pesan dari kecil ke besar (priotitasnya)
    @Override
    protected List sortByQueueMode(List list) {
        switch (sendQueueMode) {
            case Q_MODE_RANDOM:
                Collections.shuffle(list, new Random(SimClock.getIntTime()));
                break;
            case Q_MODE_FIFO:
                Collections.sort(list,
                        new Comparator() {
                    /**
                     * Compares two tuples by their messages' receiving time
                     */
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

                        diff = m1.getReceiveTime() - m2.getReceiveTime();
                        if (diff == 0) {
                            return 0;
                        }
                        return (diff < 0 ? -1 : 1);
                    }
                });
                break;
//            case Q_MODE_ASC:
//                Collections.sort(list, new Comparator() {
//
//                    @Override
//                    public int compare(Object o1, Object o2) {
//                        double diff;
//                        Message m1, m2;
//
//                        if (o1 instanceof Tuple) {
//                            m1 = ((Tuple<Message, Connection>) o1).getKey();
//                            m2 = ((Tuple<Message, Connection>) o2).getKey();
//                        } else if (o1 instanceof Message) {
//                            m1 = (Message) o1;
//                            m2 = (Message) o2;
//                        } else {
//                            throw new SimError("Invalid type of objects in "
//                                    + "the list");
//                        }
//
//                        diff = (Integer) m1.getProperty(MESSAGE_PRIORITY) - (Integer) m2.getProperty(MESSAGE_PRIORITY);
//                        if (diff == 0) {
//                            return 0;
//                        }
//                        return (diff < 0 ? -1 : 1);
//                    }
//                });
//                break;
            default:

        }
      
        return list;
    }

    protected Connection tryAllMessagesToAllConnections() {
        List<Connection> connections = getConnections();

        if (connections.size() == 0 || this.getNrofMessages() == 0) {
            return null;
        }

        List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
        List<Message> temp = new ArrayList<Message>();
//        List<Message> dropMs = new ArrayList<Message>();
        exchangeMessageProperty();
//		this.sortByQueueMode(messages); //kalau asli dari activeRouter memakai ini.

        for (Connection c : connections) {
            DTNHost otherhost = c.getOtherNode(getHost());
            EpidemicRouter_fuzzyyy peer = (EpidemicRouter_fuzzyyy) otherhost.getRouter();
            
            /*Jika pesan baru dibuat ketika node buffer penuh, maka satu atau lebih 
            pesan di buffer dengan prioritas paling rendah akan dibuang (dimasukkan ke list).
            */
            if (getHost().getBufferOccupancy() < otherhost.getRouter().getFreeBufferSize()) {
                temp.addAll(messages);
//                System.out.println(getHost().getBufferOccupancy()+"||"+otherhost.getRouter().getFreeBufferSize()); // untuk nge cek
            } else if ((Integer) getHighestMessage(true) > (Integer) peer.getLowestMessage(true)) {
                temp.addAll(messages);
//                System.out.println("High : " + getHighestMessage(deleteDelivered) + " Low : " + getLowestMessage(deleteDelivered));
            } else if ((Integer) getLowestMessage(true) < (Integer) peer.getHighestMessage(true)) { //cct B
                for (Message m : messages) {
                    if (m.getSize() < peer.getFreeBufferSize()) {
                        temp.add(m);
                    }
                }
            } else { //for each untuk CSTQ disini
                List<Message> listMergedMessages = getMergedSortMessage(); //getMergedSortMessage() adalah bandingan prioritas node A dan B
                for (Message m : listMergedMessages) {
                    if (!peer.hasMessage(m.getId()) && m.getSize() < peer.getFreeBufferSize()) {
                        temp.add(m);
                        if (peer.canAcceptMessage(m)) {
//                            sendMessage(m, m.getTo());                        
                        }
                    }
                    
//                    //untuk kondis 1 dan 2
//                    if ( m.getSize() > this.getBufferSize()) {
//                        if (m.priority < listMergedMessages.size()) {
//                            dropMs.add(m); // message too big for the buffer
//                        } else if (peer.getPriorityMessage(m) < listMergedMessages.size()){
//                            dropMs.add(m);
//                        }
//                    }
//    
                }
            }
        }
//        this.sendQueueMode = Q_MODE_ASC;
        this.sendQueueMode = Q_MODE_RANDOM;
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
            EpidemicRouter_fuzzyyy peer = (EpidemicRouter_fuzzyyy) other.getRouter();
       
            if (isFinalDest(m, getHost())) {
                makeRoomInBuffer(count);
            } else {
                if (this.callPriority(m) > peer.callPriority(m)) {
                    makeRoomInBuffer(count);
                }
            }
        }
        m.updateProperty(MESSAGE_PRIORITY, count);
        return m;
    }

    //jika nilai replikasi sama, maka menggunakan ini
    //untuk menentukkan priority dari message
    private int getPriorityMessage(Message m) {
        int priority;
        int security = m.getSecurity();
        int urgency = m.getUrgency();
        priority = (security + urgency) / 2;     
//       System.out.println("Security : " + security + " | Urgency : " + urgency + " | Priority : " + priority);
//       // System.out.println("FTC : " + FTC +" dan" + " TTL : " + TTL);
       // fuzzy(m);
      //  System.out.println("coba eli : " + m.fuzzy(m));
      callPriority(m);
      System.out.println("FTC : " + m.getFtc()+" dan" + " MS : " + m.getSize()+ " memiliki priority : " +  callPriority(m));
//        return callPriority(m);
        return priority;
    }
    
    protected double fuzzy (Message m){
        double ftcValue = m.getFtc();
        double msValue = m.getSize();
        FunctionBlock functionBlock = fcl.getFunctionBlock(null);
        

        functionBlock.setVariable(FORWARD_TRANSMISSION_COUNT, ftcValue);
        functionBlock.setVariable(MESSAGE_SIZE, msValue);
        functionBlock.evaluate();

        Variable coa = functionBlock.getVariable(NILAI_PRIORITAS);

       //double priority = 1 - coa.getValue();
       // System.out.println(functionBlock);
      //  System.out.println("coa : " + coa.getValue());
      //  JFuzzyChart.get().chart(functionBlock);
        return coa.getValue();
    }


    //method menggabungkan priority pesan node A dan B
    protected List<Message> getMergedSortMessage() {
        List<Message> mergeMsg = new ArrayList<Message>();
        for (Connection c : getConnections()) {
            DTNHost thisHost = getHost();

            DTNHost other = c.getOtherNode(thisHost);
            EpidemicRouter_fuzzyyy peer = (EpidemicRouter_fuzzyyy) other.getRouter();
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

    protected Integer getHighestMessage(boolean exludeMsgBeingSent) {
        Collection<Message> messages = getMessageCollection();
        Message biggestReplicationNumber = null;

        // simpan copy
        Integer highest = 0;
        for (Message m : messages) {
            if (exludeMsgBeingSent && isSending(m.getId())) {
                continue;
            }
            if (biggestReplicationNumber == null) {
                biggestReplicationNumber = m;
                highest = (Integer) biggestReplicationNumber.getProperty(MESSAGE_PRIORITY);
//      
            } else if ((Integer) biggestReplicationNumber.getProperty(MESSAGE_PRIORITY) == (Integer) m.getProperty(MESSAGE_PRIORITY)) {
                double firstMessage = getPriorityMessage(biggestReplicationNumber);
              //  fuzzy(biggestReplicationNumber);
                double secondMessage = getPriorityMessage(m);
                if (firstMessage < secondMessage) {
                    biggestReplicationNumber = m;
                    highest = (Integer) biggestReplicationNumber.getProperty(MESSAGE_PRIORITY);
//                                     
                }
            } else if ((Integer) biggestReplicationNumber.getProperty(MESSAGE_PRIORITY) < (Integer) m.getProperty(MESSAGE_PRIORITY)) {
                biggestReplicationNumber = m;
                highest = (Integer) biggestReplicationNumber.getProperty(MESSAGE_PRIORITY);
            }

        }

        return highest;
    }

    protected Integer getLowestMessage(boolean exludeMsgBeingSent) {
        Collection<Message> messages = getMessageCollection();
        Message lowestReplicationNumber = null;

        
        Integer lowest = 0;
        for (Message m : messages) {
            if (exludeMsgBeingSent && isSending(m.getId())) {
                continue;
            }
            if (lowestReplicationNumber == null) {
                lowestReplicationNumber = m;
                lowest = (Integer) lowestReplicationNumber.getProperty(MESSAGE_PRIORITY);
            } else if ((Integer) lowestReplicationNumber.getProperty(MESSAGE_PRIORITY) == (Integer) m.getProperty(MESSAGE_PRIORITY)) {
                double firstMessage = getPriorityMessage(lowestReplicationNumber);
                double secondMessage = getPriorityMessage(m);
                if (firstMessage > secondMessage) {
                    lowestReplicationNumber = m;
                    lowest = (Integer) lowestReplicationNumber.getProperty(MESSAGE_PRIORITY);
                }
            } else if ((Integer) lowestReplicationNumber.getProperty(MESSAGE_PRIORITY) > (Integer) m.getProperty(MESSAGE_PRIORITY)) {
                lowestReplicationNumber = m;
                lowest = (Integer) lowestReplicationNumber.getProperty(MESSAGE_PRIORITY);
            }

        }

        return lowest;

    }
    
    public boolean messageIsBiggerThanBuffer(Message m) {
        return m.getSize() > this.getFreeBufferSize();
    }

    public boolean canAcceptMessage(Message m) {
        for (Connection c : getConnections()) {
            List<Message> dropMs = new ArrayList<Message>();   
            
            int freeBuffer = this.getFreeBufferSize();
            m.priority = fuzzy(m);
            if (messageIsBiggerThanBuffer(m)) {
                return false;
            }
            
            while (freeBuffer < m.getSize()) {
                if (m.getHopCount() > 1) {
                    double priorityDrop =0;
                    priorityDrop += fuzzy(m);
                    
                    if (m.priority <= priorityDrop) {
                        return false;
                    }
                    freeBuffer += m.getSize();
                    dropMs.add(m);
                }
            } 
            deleteMessage(m.getId(), false);
        }
        return true;
    }

    protected boolean makeRoomInBuffer(int size) {
        if (size > this.getBufferSize()) {
            return false;
        }
        int freeBuffer = this.getFreeBufferSize();
        while (freeBuffer < size) {
            Message m = getMessageWithMinPriority(true);
            if (m == null) {
                return false;
            } 
//            else if ( hasMessage(m.getId()) || isDeliveredMessage(m) ){
//                return false; // already seen this message
//            }
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
    public EpidemicRouter_fuzzyyy replicate() {
        return new EpidemicRouter_fuzzyyy(this);
    }  
}



