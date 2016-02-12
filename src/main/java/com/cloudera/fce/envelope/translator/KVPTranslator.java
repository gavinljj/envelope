package com.cloudera.fce.envelope.translator;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import com.cloudera.fce.envelope.RecordModel;
import com.cloudera.fce.envelope.utils.PropertiesUtils;
import com.cloudera.fce.envelope.utils.RecordUtils;

public class KVPTranslator extends Translator {
    
    private String kvpDelimiter;
    private String fieldDelimiter;
    private List<String> fieldNames;
    private List<String> fieldTypes;
    private RecordModel recordModel = new RecordModel();
    private Schema schema;
    
    public KVPTranslator(Properties props) {
        super(props);
        
        kvpDelimiter = resolveDelimiter(props.getProperty("translator.kvp.delimiter.kvp"));
        fieldDelimiter = resolveDelimiter(props.getProperty("translator.kvp.delimiter.field"));
        fieldNames = PropertiesUtils.propertyAsList(props, "translator.kvp.field.names");
        fieldTypes = PropertiesUtils.propertyAsList(props, "translator.kvp.field.types");
        recordModel.setKeyFieldNames(PropertiesUtils.propertyAsList(props, "translator.kvp.key.field.names"));
        schema = RecordUtils.schemaFor(fieldNames, fieldTypes);
    }
    
    @Override
    public GenericRecord translate(String key, String message) {
        String[] kvps = message.split(Pattern.quote(kvpDelimiter));
        
        GenericRecord record = new GenericData.Record(schema);
        
        for (int kvpPos = 0; kvpPos < kvps.length; kvpPos++) {
            String[] components = kvps[kvpPos].split(Pattern.quote(fieldDelimiter));
            
            String kvpKey = components[0];
            String kvpValue = components[1];
            
            if (!kvpKey.matches("^[A-Za-z_].*")) {
                kvpKey = "_" + kvpKey;
            }
            
            switch (fieldTypes.get(kvpPos)) {
                case "string":
                    record.put(kvpKey, kvpValue);
                    break;
                case "float":
                    record.put(kvpKey, Float.parseFloat(kvpValue));
                    break;
                case "double":
                    record.put(kvpKey, Double.parseDouble(kvpValue));
                    break;
                case "int":
                    record.put(kvpKey, Integer.parseInt(kvpValue));
                    break;
                case "long":
                    record.put(kvpKey, Long.parseLong(kvpValue));
                    break;
                default:
                    throw new RuntimeException("Unsupported KVP field type: " + fieldTypes.get(kvpPos));
            }
        }
        
        return record;
    }
    
    @Override
    public Schema getSchema() {
        return schema;
    }
    
    private String resolveDelimiter(String delimiterArg) {
        if (delimiterArg.startsWith("chars:")) {
            String[] codePoints = delimiterArg.substring("chars:".length()).split(",");
            
            StringBuilder delimiter = new StringBuilder();
            for (String codePoint : codePoints) {
                delimiter.append(Character.toChars(Integer.parseInt(codePoint)));
            }
            
            return delimiter.toString();
        }
        else {
            return delimiterArg;
        }
    }

    @Override
    public String acceptsType() {
        return "string";
    }

    @Override
    public RecordModel getRecordModel() {
        return recordModel;
    }

}
