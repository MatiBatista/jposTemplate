package ar.cabal.jpos;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.source.tree.Tree;
import org.jpos.core.*;
import org.jpos.iso.*;
import org.jpos.space.LocalSpace;
import org.jpos.space.SpaceFactory;
import org.jpos.space.SpaceSource;
import org.jpos.transaction.ContextConstants;
import org.jpos.util.Log;
import org.jpos.transaction.Context;
import org.jpos.space.Space;

import java.util.*;
import java.util.stream.Collectors;

public class ISOChannelListener extends Log implements ISORequestListener, Configurable {
    private long timeout;
    private Space<String, Context> sp;
    private String queue;
    private String source;
    private String request;
    private String timestamp;
    private Map<String, String> additionalContextEntries;
    private boolean remote;

    // Configuración del listener, se llama al inicio del ciclo de vida del listener
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        timeout = cfg.getLong("timeout", 15000L);
        sp = SpaceFactory.getSpace(cfg.get("space"));
        queue = cfg.get("queue");
        if (queue == null) {
            throw new ConfigurationException("queue property not specified");
        }
        source = cfg.get("source", ContextConstants.SOURCE.toString());
        request = cfg.get("request", ContextConstants.REQUEST.toString());
        timestamp = cfg.get("timestamp", ContextConstants.TIMESTAMP.toString());
        remote = cfg.getBoolean("remote") || cfg.get("space").startsWith("rspace:");
        additionalContextEntries = new HashMap<>();
        cfg.keySet().stream()
                .filter(s -> s.startsWith("ctx."))
                .forEach(s -> additionalContextEntries.put(s.substring(4), cfg.get(s)));
    }

    // Método principal que procesa las solicitudes ISO
    public boolean process(ISOSource src, ISOMsg m) {
        // Crear una instancia de SpaceSource si se está ejecutando de forma remota
        SpaceSource spaceSource = remote ? new SpaceSource((LocalSpace) sp, src, timeout) : null;

        // Actualizar la referencia src si spaceSource no es nulo
        if (spaceSource != null) {
            src = spaceSource;
        }

        try {
            // Recuperar información de la tarjeta
            CardHolder ch = retrieveCardHolder(m);
            // Mostrar información sobre la tarjeta
            if (ch != null) {
                String pan = ch.getPAN();
                info(ch.toString());
                info(String.format("El PAN es: %s", pan));
               // Map<String,HashMap<String,String>> js=new HashMap<>();
                Map<String,HashMap<String,String>> js= new LinkedHashMap<>();
                /*int numberOfFields=m.getMaxField();
                info(String.format("Field mas grande: %s",String.valueOf(numberOfFields)));
                for(int i=0;i<=numberOfFields;i++){
                   if(m.hasField(i)){
                       ISOComponent isoComponent=m.getComponent(i);
                       String fieldNumber= "field-id-"+String.valueOf(isoComponent.getFieldNumber());
                       if(!isoComponent.getChildren().isEmpty()){
                           Collection<ISOField> isoComponents = isoComponent.getChildren().values();
                           HashMap<String,String> isoFields = new HashMap<>();
                           for (ISOField isoField : isoComponents) {
                               isoFields.put("field-id-"+String.valueOf(isoField.getFieldNumber()),isoField.getValue().toString());
                           }
                           js.put(fieldNumber,isoFields);
                       }else{
                   //ISOComponent isoComponent=m.getComponent(i);
                   //String fieldNumber= "field-id-"+String.valueOf(isoComponent.getFieldNumber());
                   String value=isoComponent.getValue().toString();
                   HashMap<String,String> val=new HashMap<>();
                   val.put("value",value);
                   js.put(fieldNumber,val);}
                   }
               } */
                /*Collection<ISOField> isoComponents = m.getComponent(43).getChildren().values();
                Map<String,String> isoFields = new LinkedHashMap<>();
                for (ISOField isoField : isoComponents) {
                    // Haz algo con cada ISOField
                    isoFields.put(String.valueOf(isoField.getFieldNumber()),isoField.getValue().toString());
                }*/
                ObjectMapper objectMapper=new ObjectMapper();

               info(String.format("JSON message:\n%s", convertISOMsgToJson(m)));

                Map<String, Object> jsonMap = createJson(pan);
                String jsonString = objectMapper.writeValueAsString(jsonMap);
                info(String.format("Lo muestro como JSON: %s", jsonString));
            }

            // Crear un context y enviarlo al Space
            Context ctx = createContext(src, m);
            sp.out(queue, ctx, timeout);
            return true;
        } catch (Exception e) {
            error("Error en el procesamiento", e);
            return false;
        }
    }


    // Recuperar información de la tarjeta a partir del mensaje ISO
    private CardHolder retrieveCardHolder(ISOMsg m) {
        try {
            return new CardHolder(m);
        } catch (InvalidCardException e) {
            info("No se pudo recuperar el número de tarjeta");
            return null;
        }
    }


    public String convertISOMsgToJson(ISOMsg m) throws JsonProcessingException {
        Collection<?> a = m.getChildren().values();
        info(m.getChildren().toString());
        Map map=m.getChildren();
        List<Integer> listaDeclaves=new ArrayList<>(map.keySet());
        ArrayList<?> arrayList = new ArrayList<>(a);
        Map<String,HashMap<String,String>> ss= new LinkedHashMap<>();
        int pos=0;
        for(Object object : arrayList){
            int p=listaDeclaves.get(pos);
            pos+=1;
            String fieldNumber="field-id-"+ p;
            String value = null;
            HashMap<String,String> rs=new HashMap<>();
            if(object instanceof ISOMsg){
                ISOMsg isoMsg= (ISOMsg) object;
                Map s=isoMsg.getChildren();
                List<Integer> listaDeclave=new ArrayList<>(s.keySet());
                if(!listaDeclave.isEmpty()) {
                    Collection<ISOField> isoComponents = isoMsg.getChildren().values();
                    LinkedHashMap<String, String> isoFields = new LinkedHashMap<>();
                    int k=0;
                    for (ISOField isoField : isoComponents) {
                        int ls=listaDeclave.get(k);
                        k+=1;
                        isoFields.put("field-id-" + ls, isoField.getValue().toString());
                    }
                    ss.put(fieldNumber, isoFields);
                }
            }else if(object instanceof  ISOField){
                ISOField isoField=(ISOField) object;
                value = isoField.getValue().toString();
                rs.put("value:",value);
                ss.put(fieldNumber,rs);
            }else if(object instanceof ISOAmount){
                ISOAmount isoAmount=(ISOAmount) object;
                value = isoAmount.getAmount().toString();
                String c=String.valueOf(isoAmount.getCurrencyCode());
                rs.put("type:","amount");
                rs.put("amount:",value);
                rs.put("Currency code:",c);
                ss.put(fieldNumber,rs);
            }
        }
        ObjectMapper objectMapper=new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        String jsn = objectMapper.writeValueAsString(ss);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return jsn;
    }



    // Crear un objeto JSON a partir del número de tarjeta
    private Map<String, Object> createJson(String pan) {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("pan", pan);
        return jsonMap;
    }

    // Crear un context para enviar al Space
    private Context createContext(ISOSource src, ISOMsg m) {
        Context ctx = new Context();
        ctx.put(timestamp, new Date(), remote);
        ctx.put(source, src, remote);
        ctx.put(request, m, remote);
        additionalContextEntries.forEach((key, value) -> ctx.put(key, value, remote));
        return ctx;
    }
}

