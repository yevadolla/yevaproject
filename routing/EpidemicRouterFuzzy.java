package routing;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import core.DTNHost;
import core.Message;
import core.Settings;
import java.util.Map;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import net.sourceforge.jFuzzyLogic.plot.JFuzzyChart;
import net.sourceforge.jFuzzyLogic.rule.Variable;
import routing.ActiveRouter;
import routing.MessageRouter;

/**
 *
 * @author hp
 */
public class EpidemicRouterFuzzy extends ActiveRouter{
    
//parameter   
    public static final String FCL_NAMES = "fcl";
    public static final String TIME_TO_LIVE = "ttl";
    public static final String FORWARD_TRANSMISSION_COUNT = "ftc";
    public static final String NILAI_PRIORITAS = "priority";
    public static final int Q_MODE_DSC = 1;
    
    
    //Fuzzy Control Language
    private FIS fcl;
    // FTC adalah forward transmission count yaitu jumlah copy pesan yang berada pada jaringan
    protected Map<DTNHost,Double> FTC;
    // TTL adalah waktu hidup dari pesan.
    protected Map<DTNHost,Double> TTL;
    
    
    

    public EpidemicRouterFuzzy(Settings s) {
        super(s);
        String fclString = s.getSetting(FCL_NAMES);
        fcl = FIS.load(fclString);   
        
    }
    
    protected EpidemicRouterFuzzy (EpidemicRouterFuzzy r){
        super(r);
    }
    
   private double getFTC (DTNHost nodes){
       if(FTC.containsKey(nodes)){
           return FTC.get(nodes); 
       }
       else {
           return 0;
       }
   } 
   
    private double getTTL (DTNHost nodes){
       if(TTL.containsKey(nodes)){
           return TTL.get(nodes); 
       }
       else {
           return 0;
       }
   } 
    
    
    private double fuzzy(DTNHost nodes) {       
        double ftcValue = getFTC(nodes);
        double ttlValue = getTTL(nodes);
        FunctionBlock functionBlock = fcl.getFunctionBlock(null);
        

        functionBlock.setVariable(FORWARD_TRANSMISSION_COUNT, ftcValue);
        functionBlock.setVariable(TIME_TO_LIVE, ttlValue);
        functionBlock.evaluate();

        Variable coa = functionBlock.getVariable(NILAI_PRIORITAS);

      //double priority = 1 - coa.getValue();
        System.out.println(functionBlock);
        System.out.println(coa.getValue());
        JFuzzyChart.get().chart(functionBlock);
        return coa.getValue();
       
        
    }
//    
//    private double callPriority (Message m){
//    return    
//    }
        
 protected void ackMessage(Message m, DTNHost h) {
     
 
 
 }


    @Override
    public MessageRouter replicate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
