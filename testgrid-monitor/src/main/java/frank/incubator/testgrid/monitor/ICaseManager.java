/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 *
 * @author larryang
 */
public class ICaseManager {
    
    private String iCaseRoot;
    
    private LogConnector log;
    
    public ICaseManager(String inICaseRoot){
        iCaseRoot = inICaseRoot;
        log = LogUtils.get("ICaseManager");
    }

    public String getiCaseRoot() {
        return iCaseRoot;
    }

    public void setiCaseRoot(String iCaseRoot) {
        this.iCaseRoot = iCaseRoot;
    }
    
    public List<String> getYears(){
        
        List<String> yearList = new ArrayList();
        
        File iCaseRootFile = new File(iCaseRoot);
        
        for (final File fileEntry : iCaseRootFile.listFiles()) {
            if (fileEntry.isDirectory()) {
                
                if (fileEntry.getName().matches("\\d{4}")){
                    yearList.add(fileEntry.getName());
                }
            }
        }

        Collections.sort(yearList);
        
        return yearList;
    }
    
    public List<String> getWeeks(String year){
        
        List<String> weekList = new ArrayList();
        
        File iCaseRootFile = new File(iCaseRoot + "/" + year);
        
        for (final File fileEntry : iCaseRootFile.listFiles()) {
            
            if (fileEntry.isDirectory() && fileEntry.getName().matches("wk\\d*")) {
                weekList.add(fileEntry.getName());
            }
        }
        
        Collections.sort(weekList);

        return weekList;
    }
    
    public List<String> getPossibleICaseOptionsForKey(String year, String week, String key){
        
        File iCaseRootFile = new File(iCaseRoot + "/" + year + "/" + week);
        
        List<String> options = getPossibleICaseOptionsForKey(key, iCaseRootFile);
        
        Collections.sort(options);
        
        return options;
    }
    
    protected List<String> getPossibleICaseOptionsForKey(String key, File iCaseRootFile) {

        List<String> outputList = new ArrayList();

        try {
            
            String[] command = {
                "/bin/bash",
                "-c",
                "find " + iCaseRootFile.getPath() + " -name '*.info' | xargs  egrep '^" + key + "=' | sed -e 's/.*" + key + "=//g' | sort -u"
            };
            
            log.info("find " + iCaseRootFile.getPath() + " -name '*.info' | xargs  egrep '^" + key + "=' | sed -e 's/.*" + key + "=//g' | sort -u");

            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                if (StringUtils.isNotEmpty(line)){
                    outputList.add(line);
                    log.info(line);
                }
            }

        } catch (Exception e) {
            log.error("Exception in getPossibleICaseOptionsForKey: {}", e);
        }

        return outputList;
    }
}
