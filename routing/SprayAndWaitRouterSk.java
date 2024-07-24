package routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

public class SprayAndWaitRouterSk extends ActiveRouter {
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String QON_PROPERTY = SPRAYANDWAIT_NS + "." + "QoN";
    public static final String LAST_UPDATE_TIME_PROPERTY = SPRAYANDWAIT_NS + "." + "lastUpdateTime";
    public static final String K_OLD_PROPERTY = SPRAYANDWAIT_NS + "." + "kOld";

    protected int initialNrofCopies;
    protected boolean isBinary;
    protected double alpha = 0.15;
    private double Q;

    public SprayAndWaitRouterSk(Settings s) {
        super(s);
        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
        initialNrofCopies = snwSettings.getInt(NROF_COPIES);
        isBinary = snwSettings.getBoolean(BINARY_MODE);
    }

    protected SprayAndWaitRouterSk(SprayAndWaitRouterSk r) {
        super(r);
        this.initialNrofCopies = r.initialNrofCopies;
        this.isBinary = r.isBinary;
    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        return super.receiveMessage(m, from);
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

        assert nrofCopies != null : "Not a SnW message: " + msg;

        double QoN = this.Q;
        msg.updateProperty(MSG_COUNT_PROPERTY, (int) Math.ceil(QoN));

        return msg;
    }

    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());
        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
        msg.addProperty(QON_PROPERTY, calculateQoN(getHost()));
        addToMessages(msg, true);
        return true;
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return;
        }
        if (exchangeDeliverableMessages() != null) {
            return;
        }
        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
        if (copiesLeft.size() > 0) {
            this.tryMessagesToConnections(copiesLeft, getConnections());
        }
    }

    protected List<Message> getMessagesWithCopiesLeft() {
        List<Message> list = new ArrayList<Message>();
        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "SnW message " + m + " didn't have nrof copies property!";
            if (nrofCopies > 1) {
                list.add(m);
            }
        }
        return list;
    }

    @Override
    protected void transferDone(Connection con) {
        Integer nrofCopies;
        String msgId = con.getMessage().getId();
        Message msg = getMessage(msgId);
        if (msg == null) {
            return;
        }
        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        if (isBinary) {
            nrofCopies /= 2;
        } else {
            nrofCopies--;
        }
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
    }

    @Override
    public SprayAndWaitRouterSk replicate() {
        return new SprayAndWaitRouterSk(this);
    }

    private double calculateQoN(DTNHost host) {
        Map<String, Object> properties = host.getProperties();

        Double Q = (Double) properties.get(QON_PROPERTY);
        if (Q == null) {
            Q = 0.0;
        }

        Double lastUpdateTime = (Double) properties.get(LAST_UPDATE_TIME_PROPERTY);
        if (lastUpdateTime == null) {
            lastUpdateTime = 0.0;
        }

        double currentTime = host.getSimTime();
        int k = host.getEncounterCount();
        double delta_t = currentTime - lastUpdateTime;

        // Jika waktu update melebihi waktu fragmen, nilai k_old dan Q_old diambil dari current k dan Q
        if (delta_t > 3600) {
            properties.put(K_OLD_PROPERTY, k);
            properties.put(QON_PROPERTY, Q);
            lastUpdateTime = currentTime;
        }

        double avgK = k / delta_t;
        Q = alpha * Q + (1 - alpha) * avgK;

        properties.put(QON_PROPERTY, Q);
        properties.put(LAST_UPDATE_TIME_PROPERTY, currentTime);

        return Q;
    }

    protected int getNrofCopiesForNewConnection(Connection con) {
        Message msg = con.getMessage();
        DTNHost other = con.getOtherNode(getHost());

        Map<String, Object> hostProperties = getHost().getProperties();
        Map<String, Object> otherProperties = other.getProperties();

        Double QoN1 = (Double) hostProperties.get(QON_PROPERTY);
        if (QoN1 == null) {
            QoN1 = 0.0;
        }

        Double QoN2 = (Double) otherProperties.get(QON_PROPERTY);
        if (QoN2 == null) {
            QoN2 = 0.0;
        }

        int nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        int newCopies = (int) Math.ceil((QoN2 / (QoN1 + QoN2)) * nrofCopies);
        return newCopies;
    }

    
    
}