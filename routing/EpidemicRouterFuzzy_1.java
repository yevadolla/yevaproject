/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimError;
import core.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import net.sourceforge.jFuzzyLogic.rule.Variable;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EpidemicRouterFuzzy_1 extends ActiveRouter {

    public static final String FCL_NAMES = "fcl";
    public static final String TIME_TO_LIVE = "ttl";
    public static final String FORWARD_TRANSMISSION_COUNT = "ftc";
    public static final String MESSAGE_PRIORITY = "priority";
    public static final int Q_MODE_DESC = 1;
    private List<Message> ackList;
    private Set<String> allAckedMessages;

    private FIS fcl;

    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public EpidemicRouterFuzzy_1(Settings s) {
        super(s);
        String fclString = s.getSetting(FCL_NAMES);
        fcl = FIS.load(fclString);
        this.ackList = new ArrayList<Message>();
        this.allAckedMessages = new HashSet<>();
        //TODO: read&use epidemic router specific settings (if any)
    }

    /**
     * Copy constructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected EpidemicRouterFuzzy_1(EpidemicRouterFuzzy_1 r) {
        super(r);
        this.fcl = r.fcl;
        this.ackList = r.ackList;
        this.allAckedMessages = r.allAckedMessages;
        //TODO: copy epidemic settings here (if any)
    }

    @Override
    public boolean createNewMessage(Message m) {
        m.setFtc(m.getHopCount());
        m.setTtl(this.msgTtl);
        return super.createNewMessage(m);
    }

    public List<Message> getAckList() {
        return ackList;
    }

// method ini berisi daftar pesan yang sudah sampai ke tujuan akhir yang diketahui oleh setiap node
    protected Message createAckedList(Message m) {
        if (isDeliveredMessage(m)) {
            ackList.add(m);
            allAckedMessages.add(m.getId());
            //  System.out.println(ackList);
        }
        return m;
    }

//    public void exchangeAckedList(MessageRouter otherRouter) {
//        //daftar ID pesan yang sudah dikirim oleh router ini (this).
//        List<String> myAckedMessages = new LinkedList<>(this.deliveredMessages.keySet());
//       
//        // daftar ID pesan yang sudah dikirim oleh router lain (otherRouter).
//        List<String> otherAckedMessages = new LinkedList<>(otherRouter.deliveredMessages.keySet());
//
//        //untuk setiap ID pesan yang sudah dikirim oleh otherRouter.
//        for (String ackedMessage : otherAckedMessages) {
//            // jika this router blm punya id pesan yg ada di other router
//            if (!this.deliveredMessages.containsKey(ackedMessage)) {
//                this.deliveredMessages.put(ackedMessage, null); // Add the message ID to the list
//            }
//        }
//        //untuk setiap setiap ID pesan yang sudah dikirim oleh thisRouter.
//        for (String ackedMessage : myAckedMessages) {
//            // jika other router blm punya id pesan yg ada di this router
//            if (!otherRouter.deliveredMessages.containsKey(ackedMessage)) {    
//                otherRouter.deliveredMessages.put(ackedMessage, null); // Add the message ID to the list
//            }
//        }
//    }
    // Pertukaran daftar pesan yang diakui antar node
    public void exchangeAckedList() {
        List<Connection> connections = getConnections();
        List<String> myAckedMessages = new LinkedList<>(this.deliveredMessages.keySet());

        for (Connection con : connections) {
            MessageRouter otherRouter = con.getOtherNode(getHost()).getRouter();
            List<String> otherAckedMessages = new LinkedList<>(otherRouter.deliveredMessages.keySet());

            for (String ackedMessage : otherAckedMessages) {
                if (!this.deliveredMessages.containsKey(ackedMessage)) {
                    this.deliveredMessages.put(ackedMessage, null);
                }
            }

            for (String ackedMessage : myAckedMessages) {
                if (!otherRouter.deliveredMessages.containsKey(ackedMessage)) {
                    otherRouter.deliveredMessages.put(ackedMessage, null);
                }
            }
        }
    }

    public void deleteAckedList() {

        Collection<Message> messCollection = getMessageCollection();
        List<String> messagesToDelete = new ArrayList<>();

        for (Message message : messCollection) {
            if (isDeliveredMessage(message) || allAckedMessages.contains(message.getId())) {
                messagesToDelete.add(message.getId());
            }
        }
        for (String messageId : messagesToDelete) {
            deleteMessage(messageId, true);
        }
    }

// deliverable message adalah pesan yang tujuannya node yang terhubung
    @Override
    protected Connection exchangeDeliverableMessages() {
        List<Connection> connections = getConnections();
        List<Message> deliverableMessages = new ArrayList<>();

        if (connections.size() == 0) {
            return null;
        }

        List<Tuple<Message, Connection>> sortedMessagesForConnected = sortByQueueMode(getMessagesForConnected());

        Tuple<Message, Connection> t = tryMessagesForConnected(sortedMessagesForConnected);

        if (t != null) {
            Message m = t.getKey();
            deliverableMessages.add(m);

            if (deliverableMessages.size() > 1) {
                callPriority(deliverableMessages);
                List<Message> sortedDeliverableMessages = this.sortByQueueMode(deliverableMessages);
                return tryMessagesToConnections(sortedDeliverableMessages, connections);
            } else {
                return t.getValue();
            }
        }

        for (Connection con : connections) {
            if (con.getOtherNode(getHost()).requestDeliverableMessages(con)) {
                return con;
            }
        }

        return null;
    }

    protected double fuzzy(Message m) {
        double ftcValue = m.getFtc();
        double ttlValue = m.getTtl();
        FunctionBlock functionBlock = fcl.getFunctionBlock(null);

        functionBlock.setVariable(FORWARD_TRANSMISSION_COUNT, ftcValue);
        functionBlock.setVariable(TIME_TO_LIVE, ttlValue);
        functionBlock.evaluate();

        Variable coa = functionBlock.getVariable(MESSAGE_PRIORITY);

        return coa.getValue();
    }

//    public double callPriority(Message m) {
//        m.coaValue = fuzzy(m);
//        m.priority = 1 - m.coaValue;
//        return m.priority;
//
//    }
    public List<Message> callPriority(List<Message> msgs) {
        for (Message msg : msgs) {
            msg.coaValue = fuzzy(msg);
            double priority = 1 - msg.coaValue;
            msg.setPriority(priority);
            //System.out.println("FTC : " + msg.getFtc() + " TTL :  " + msg.getTtl() + " priority : " + msg.priority);
        }
        return msgs;
    }

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
    
    
    @Override
    protected Connection tryAllMessagesToAllConnections() {
        List<Connection> connections = getConnections();

        if (connections.size() == 0 || this.getNrofMessages() == 0) {
            return null;
        }

        // Tambahkan pesan dari router saat ini
        List<Message> messages = new ArrayList<>(this.getMessageCollection());
        List<Message> allMessages = new ArrayList<>();
        this.sortByQueueMode(messages); //kalau asli dari activeRouter memakai ini.

          allMessages.addAll(messages);
        // Gabungkan pesan dari router yang terhubung jika buffer memiliki ruang cukup
        for (Connection conn : connections) {
            DTNHost thisHost = getHost();

            DTNHost other = conn.getOtherNode(thisHost);
            EpidemicRouterFuzzy_1 peer = (EpidemicRouterFuzzy_1) other.getRouter();

            List<Message> messageBufferA = new ArrayList<>(getMessageCollection());
            System.out.println("buffA : " + messageBufferA.size());
            List<Message> messageBufferB = new ArrayList<>(peer.getMessageCollection());
            System.out.println("buffB : " + messageBufferB.size());


            // Tambahkan pesan dari buffer saat ini jika belum ada di buffer tujuan
        for (Message m : messageBufferA) {
            if (!peer.hasMessage(m.getId())) {
                allMessages.add(m);
            }
        }

        // Tambahkan pesan dari buffer router tetangga jika belum ada di buffer saat ini dan ukurannya sesuai dengan buffer yang tersedia
        for (Message m : messageBufferB) {
            if (!hasMessage(m.getId()) && m.getSize() < getFreeBufferSize()) {
                allMessages.add(m);
            }
        }
        }
        // Urutkan pesan berdasarkan prioritas
       System.out.println("jumlah all : " + allMessages.size());
       callPriority(allMessages);
       this.sortByQueueMode(allMessages);
       System.out.println("urutan : " +sortByQueueMode(allMessages) );

        // Coba kirim pesan ke semua koneksi
        return tryMessagesToConnections(allMessages, connections);
    }
    

    @Override
    public Message messageTransferred(String id, DTNHost from) {

        Message m = super.messageTransferred(id, from);
        if (m.getTo() == getHost()) {
            
            createAckedList(m);
         
            // generate a response message
            Message res = new Message(this.getHost(), m.getFrom(),
                    RESPONSE_PREFIX + m.getId(), m.getResponseSize());
            this.createNewMessage(res);
            this.getMessage(RESPONSE_PREFIX + m.getId()).setRequest(m);

        }

        return m;
    }

    @Override
    public void update() {
        super.update();

        exchangeAckedList();

        if (isTransferring() || !canStartTransfer()) {
            deleteAckedList();
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            deleteAckedList();
            return; // started a transfer, don't try others (yet)
        }

        // then try any/all message to any/all connection
        this.tryAllMessagesToAllConnections();
    }

    @Override
    public EpidemicRouterFuzzy_1 replicate() {
        return new EpidemicRouterFuzzy_1(this);
    }

}