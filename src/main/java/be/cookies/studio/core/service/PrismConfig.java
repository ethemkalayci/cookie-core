package be.cookies.studio.core.service;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Updates only General keys, retaining unrelated sections, comments and user settings. */
final class PrismConfig {
    static Map<String,String> read(Path path) throws IOException {
        var result=new LinkedHashMap<String,String>();String section="General";
        if(!Files.exists(path))return result;
        for(String line:Files.readAllLines(path)){
            String t=line.trim();if(t.startsWith("[")&&t.endsWith("]")){section=t.substring(1,t.length()-1);continue;}
            int eq=t.indexOf('=');if(section.equals("General")&&eq>0&&!t.startsWith("#")&&!t.startsWith(";"))result.put(t.substring(0,eq).trim(),t.substring(eq+1));
        }return result;
    }
    static void write(Path path,Map<String,String> updates) throws IOException {
        var lines=Files.exists(path)?Files.readAllLines(path):new ArrayList<String>();
        var out=new ArrayList<String>();var pending=new LinkedHashMap<>(updates);String section="General";
        boolean hasGeneral=lines.stream().anyMatch(l->l.trim().equals("[General]"));
        section=hasGeneral?"":"General";
        if(!hasGeneral)out.add("[General]");
        for(String line:lines){
            String t=line.trim();
            if(t.startsWith("[")&&t.endsWith("]")){
                if(section.equals("General")&&!t.equals("[General]")){pending.forEach((k,v)->out.add(k+"="+v));pending.clear();}
                section=t.substring(1,t.length()-1);
            }
            int eq=t.indexOf('=');String key=eq>0?t.substring(0,eq).trim():"";
            if(section.equals("General")&&updates.containsKey(key)){
                if(pending.containsKey(key))out.add(key+"="+pending.remove(key));
            }else out.add(line);
        }
        pending.forEach((k,v)->out.add(k+"="+v));
        Path tmp=Files.createTempFile(path.getParent(),".prism-settings-",".tmp");
        try{Files.write(tmp,out);CacheFiles.publish(tmp,path,true);}finally{CacheFiles.cleanup(tmp);}
    }
}
